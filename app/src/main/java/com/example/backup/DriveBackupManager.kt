package com.example.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.KasDatabase
import com.example.data.TransactionDao
import com.example.data.TransactionEntity
import com.example.reminder.KasReminderManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class DriveAuthOutcome {
    data class Authorized(val accessToken: String) : DriveAuthOutcome()
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthOutcome()
    data class Error(val message: String) : DriveAuthOutcome()
}

object DriveBackupManager {

    const val PREFS_NAME = "kas_drive_backup_prefs"
    private const val KEY_AUTO_ENABLED = "auto_backup_enabled"
    private const val KEY_INTERVAL_HOURS = "interval_hours"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_SYNC_ON_CHANGE = "sync_on_data_change"
    private const val KEY_CONNECTED_EMAIL = "connected_email"
    private const val KEY_LAST_BACKUP_MILLIS = "last_backup_millis"
    private const val KEY_LAST_BACKUP_STATUS = "last_backup_status"
    private const val KEY_LAST_FILE_ID = "last_backup_file_id"
    private const val KEY_LAST_TX_COUNT = "last_tx_count"
    private const val KEY_LAST_RESTORE_MILLIS = "last_restore_millis"

    const val UNIQUE_PERIODIC_WORK = "kas_drive_auto_backup_periodic"
    const val UNIQUE_IMMEDIATE_WORK = "kas_drive_auto_backup_immediate"

    private const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    private const val SCOPE_USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"

    private val driveScopes = listOf(
        Scope(SCOPE_DRIVE_FILE),
        Scope(SCOPE_USERINFO_EMAIL)
    )

    fun getSettings(context: Context): DriveBackupSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return DriveBackupSettings(
            autoBackupEnabled = prefs.getBoolean(KEY_AUTO_ENABLED, false),
            intervalHours = prefs.getInt(KEY_INTERVAL_HOURS, 24).let { if (it == 12) 12 else 24 },
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false),
            syncOnDataChange = prefs.getBoolean(KEY_SYNC_ON_CHANGE, true),
            connectedEmail = prefs.getString(KEY_CONNECTED_EMAIL, null)?.takeIf { it.isNotBlank() },
            lastBackupMillis = prefs.getLong(KEY_LAST_BACKUP_MILLIS, 0L),
            lastBackupStatus = prefs.getString(KEY_LAST_BACKUP_STATUS, null)
                ?.takeIf { it.isNotBlank() }
                ?: "Belum pernah dicadangkan",
            lastBackupFileId = prefs.getString(KEY_LAST_FILE_ID, null)?.takeIf { it.isNotBlank() },
            lastBackedUpTransactionCount = prefs.getInt(KEY_LAST_TX_COUNT, 0),
            lastRestoreMillis = prefs.getLong(KEY_LAST_RESTORE_MILLIS, 0L)
        )
    }

    fun saveSettings(context: Context, settings: DriveBackupSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_ENABLED, settings.autoBackupEnabled)
            .putInt(KEY_INTERVAL_HOURS, if (settings.intervalHours == 12) 12 else 24)
            .putBoolean(KEY_WIFI_ONLY, settings.wifiOnly)
            .putBoolean(KEY_SYNC_ON_CHANGE, settings.syncOnDataChange)
            .putString(KEY_CONNECTED_EMAIL, settings.connectedEmail)
            .putLong(KEY_LAST_BACKUP_MILLIS, settings.lastBackupMillis)
            .putString(KEY_LAST_BACKUP_STATUS, settings.lastBackupStatus)
            .putString(KEY_LAST_FILE_ID, settings.lastBackupFileId)
            .putInt(KEY_LAST_TX_COUNT, settings.lastBackedUpTransactionCount)
            .putLong(KEY_LAST_RESTORE_MILLIS, settings.lastRestoreMillis)
            .apply()
    }

    fun updateStatus(context: Context, status: String) {
        val current = getSettings(context)
        saveSettings(context, current.copy(lastBackupStatus = status))
    }

    fun buildAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(driveScopes)
            .build()
    }

    suspend fun authorizeDrive(context: Context): DriveAuthOutcome {
        return try {
            val result = Identity.getAuthorizationClient(context)
                .authorize(buildAuthorizationRequest())
                .awaitTask()

            val pending = result.pendingIntent
            val token = result.accessToken
            when {
                result.hasResolution() && pending != null -> DriveAuthOutcome.NeedsConsent(pending)
                !token.isNullOrBlank() -> DriveAuthOutcome.Authorized(token)
                else -> DriveAuthOutcome.Error("Token akses Google Drive tidak tersedia di perangkat ini.")
            }
        } catch (e: Exception) {
            DriveAuthOutcome.Error(
                e.localizedMessage ?: "Gagal membuka otorisasi Google Drive. Pastikan Google Play Services aktif."
            )
        }
    }

    fun extractTokenFromIntent(context: Context, data: Intent?): Result<String> {
        return runCatching {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            authResult.accessToken?.takeIf { it.isNotBlank() }
                ?: error("Izin akses Google Drive tidak diberikan.")
        }
    }

    suspend fun disconnectAccount(context: Context) {
        val current = getSettings(context)
        saveSettings(
            context,
            current.copy(
                autoBackupEnabled = false,
                connectedEmail = null,
                lastBackupStatus = "Akun Google Drive diputuskan"
            )
        )
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK)
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_IMMEDIATE_WORK)
        }
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    suspend fun buildBackupPayload(
        context: Context,
        accountEmail: String = "",
        dao: TransactionDao = KasDatabase.getDatabase(context).transactionDao()
    ): KasBackupPayload = withContext(Dispatchers.IO) {
        val transactions = dao.getAllTransactionsSync()
        val roster = KasReminderManager.getStudentRoster(context)
        val reminderSettings = KasReminderManager.getSettings(context)
        val isDarkTheme = context.getSharedPreferences("kas_theme_prefs", Context.MODE_PRIVATE)
            .getBoolean("is_dark_theme", false)

        KasBackupPayload(
            exportedAtMillis = System.currentTimeMillis(),
            accountEmail = accountEmail,
            transactions = transactions,
            studentRoster = roster,
            reminderSettings = reminderSettings,
            isDarkTheme = isDarkTheme
        )
    }

    suspend fun exportLocalBackupJson(
        context: Context,
        dao: TransactionDao = KasDatabase.getDatabase(context).transactionDao()
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildBackupPayload(context = context, accountEmail = "Backup Lokal", dao = dao)
            BackupJsonCodec.encodePayload(payload)
        }
    }

    suspend fun restoreFromLocalJson(
        context: Context,
        rawJson: String,
        mode: RestoreMode,
        dao: TransactionDao = KasDatabase.getDatabase(context).transactionDao()
    ): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = BackupJsonCodec.decodePayload(rawJson)
            applyBackupPayload(
                context = context,
                payload = payload,
                mode = mode,
                dao = dao
            )
        }
    }

    private suspend fun applyBackupPayload(
        context: Context,
        payload: KasBackupPayload,
        mode: RestoreMode,
        dao: TransactionDao
    ): RestoreSummary {
        var restoredTxCount = 0
        var skippedCount = 0

        when (mode) {
            RestoreMode.REPLACE_ALL -> {
                dao.deleteAllTransactions()
                val toInsert = payload.transactions.map { it.copy(id = 0L) }
                if (toInsert.isNotEmpty()) {
                    dao.insertTransactions(toInsert)
                }
                restoredTxCount = toInsert.size
            }

            RestoreMode.MERGE_MISSING -> {
                val existing = dao.getAllTransactionsSync()
                val existingKeys = existing.map { txDeduplicationKey(it) }.toMutableSet()
                val missingToInsert = mutableListOf<TransactionEntity>()

                for (tx in payload.transactions) {
                    val key = txDeduplicationKey(tx)
                    if (key !in existingKeys) {
                        existingKeys.add(key)
                        missingToInsert.add(tx.copy(id = 0L))
                    } else {
                        skippedCount++
                    }
                }

                if (missingToInsert.isNotEmpty()) {
                    dao.insertTransactions(missingToInsert)
                }
                restoredTxCount = missingToInsert.size
            }
        }

        val wasLocalRosterEmpty = KasReminderManager.getStudentRoster(context).isEmpty()
        val restoredRoster = when (mode) {
            RestoreMode.REPLACE_ALL -> payload.studentRoster
            RestoreMode.MERGE_MISSING -> {
                val existingRoster = KasReminderManager.getStudentRoster(context)
                (existingRoster + payload.studentRoster).distinctBy { it.lowercase() }
            }
        }
        KasReminderManager.saveStudentRoster(context, restoredRoster)

        if (mode == RestoreMode.REPLACE_ALL || wasLocalRosterEmpty) {
            KasReminderManager.saveSettings(context, payload.reminderSettings)
        }

        val totalAfter = dao.getAllTransactionsSync().size
        return RestoreSummary(
            restoredTransactionsCount = restoredTxCount,
            skippedDuplicateCount = skippedCount,
            totalLocalTransactionsAfter = totalAfter,
            restoredRosterCount = restoredRoster.size,
            backupTimestampMillis = payload.exportedAtMillis,
            mode = mode
        )
    }

    suspend fun performBackupNow(
        context: Context,
        accessToken: String,
        driveApi: GoogleDriveApi = OkHttpGoogleDriveApi(),
        dao: TransactionDao = KasDatabase.getDatabase(context).transactionDao()
    ): Result<DriveBackupMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = getSettings(context)
            val resolvedEmail = settings.connectedEmail
                ?: runCatching { driveApi.fetchUserEmail(accessToken) }.getOrNull()
                ?: "Akun Google Terhubung"

            val payload = buildBackupPayload(
                context = context,
                accountEmail = resolvedEmail,
                dao = dao
            )
            val jsonContent = BackupJsonCodec.encodePayload(payload)

            val uploaded = driveApi.uploadBackupJson(
                accessToken = accessToken,
                jsonContent = jsonContent,
                existingFileId = settings.lastBackupFileId,
                transactionCount = payload.transactions.size,
                rosterCount = payload.studentRoster.size,
                balance = payload.balance
            )

            val updatedSettings = settings.copy(
                connectedEmail = resolvedEmail,
                lastBackupMillis = System.currentTimeMillis(),
                lastBackupStatus = "Berhasil dicadangkan (${payload.transactions.size} transaksi)",
                lastBackupFileId = uploaded.fileId.ifBlank { settings.lastBackupFileId },
                lastBackedUpTransactionCount = payload.transactions.size
            )
            saveSettings(context, updatedSettings)

            uploaded.copy(
                transactionCount = uploaded.transactionCount ?: payload.transactions.size,
                rosterCount = uploaded.rosterCount ?: payload.studentRoster.size,
                balance = uploaded.balance ?: payload.balance
            )
        }.onFailure { err ->
            updateStatus(context, "Gagal backup: ${err.localizedMessage ?: "Kesalahan jaringan"}")
        }
    }

    suspend fun checkRemoteBackup(
        context: Context,
        accessToken: String,
        driveApi: GoogleDriveApi = OkHttpGoogleDriveApi()
    ): Result<DriveBackupMetadata?> = withContext(Dispatchers.IO) {
        runCatching {
            val current = getSettings(context)
            val email = current.connectedEmail ?: runCatching {
                driveApi.fetchUserEmail(accessToken)
            }.getOrNull()
            if (!email.isNullOrBlank() && email != current.connectedEmail) {
                saveSettings(context, current.copy(connectedEmail = email))
            }

            val meta = driveApi.findLatestBackupFile(accessToken)
            if (meta != null && meta.fileId.isNotBlank()) {
                val latest = getSettings(context)
                saveSettings(context, latest.copy(lastBackupFileId = meta.fileId))
            }
            meta
        }
    }

    suspend fun restoreFromDrive(
        context: Context,
        accessToken: String,
        mode: RestoreMode,
        driveApi: GoogleDriveApi = OkHttpGoogleDriveApi(),
        dao: TransactionDao = KasDatabase.getDatabase(context).transactionDao()
    ): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val remoteMeta = driveApi.findLatestBackupFile(accessToken)
                ?: error("Belum ada file backup Kas Kelas di Google Drive akun ini.")

            val rawJson = driveApi.downloadBackupJson(accessToken, remoteMeta.fileId)
            val payload = BackupJsonCodec.decodePayload(rawJson)
            val summary = applyBackupPayload(
                context = context,
                payload = payload,
                mode = mode,
                dao = dao
            )

            val settings = getSettings(context)
            saveSettings(
                context,
                settings.copy(
                    connectedEmail = settings.connectedEmail
                        ?: payload.accountEmail.takeIf { it.isNotBlank() },
                    lastBackupFileId = remoteMeta.fileId,
                    lastRestoreMillis = System.currentTimeMillis(),
                    lastBackupStatus = "Berhasil dipulihkan (${summary.restoredTransactionsCount} transaksi)"
                )
            )
            summary
        }
    }

    fun syncWorkSchedule(context: Context, settings: DriveBackupSettings) {
        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (!settings.autoBackupEnabled || !settings.isConnected) {
            workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<DriveBackupWorker>(
            settings.intervalHours.toLong().coerceIn(12L, 24L),
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    fun scheduleDebouncedBackupOnDataChange(context: Context) {
        val settings = getSettings(context)
        if (!settings.autoBackupEnabled || !settings.syncOnDataChange || !settings.isConnected) {
            return
        }
        val workManager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val immediateRequest = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setConstraints(constraints)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            immediateRequest
        )
    }

    private fun txDeduplicationKey(tx: TransactionEntity): String {
        return "${tx.type.uppercase()}|${tx.title.trim().lowercase()}|${tx.amount}|${tx.dateMillis / 1000L}|${tx.personName.trim().lowercase()}"
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exc ->
            if (cont.isActive) cont.resumeWithException(exc)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
}
