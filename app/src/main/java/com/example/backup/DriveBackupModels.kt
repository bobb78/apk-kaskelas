package com.example.backup

import android.os.Build
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.reminder.ReminderSettings
import org.json.JSONArray
import org.json.JSONObject

data class KasBackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val accountEmail: String = "",
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
    val transactions: List<TransactionEntity>,
    val studentRoster: List<String>,
    val reminderSettings: ReminderSettings,
    val isDarkTheme: Boolean = false
) {
    val totalIncome: Long
        get() = transactions.filter { it.isIncome }.sumOf { it.amount }

    val totalExpense: Long
        get() = transactions.filter { !it.isIncome }.sumOf { it.amount }

    val balance: Long
        get() = totalIncome - totalExpense

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class DriveBackupMetadata(
    val fileId: String,
    val fileName: String,
    val modifiedTimeMillis: Long,
    val sizeBytes: Long,
    val transactionCount: Int? = null,
    val rosterCount: Int? = null,
    val balance: Long? = null
)

data class DriveBackupSettings(
    val autoBackupEnabled: Boolean = false,
    val intervalHours: Int = 24,
    val wifiOnly: Boolean = false,
    val syncOnDataChange: Boolean = true,
    val connectedEmail: String? = null,
    val lastBackupMillis: Long = 0L,
    val lastBackupStatus: String = "Belum pernah dicadangkan",
    val lastBackupFileId: String? = null,
    val lastBackedUpTransactionCount: Int = 0,
    val lastRestoreMillis: Long = 0L
) {
    val isConnected: Boolean
        get() = !connectedEmail.isNullOrBlank()
}

enum class RestoreMode(val title: String, val description: String) {
    MERGE_MISSING(
        title = "Gabungkan Data (Aman)",
        description = "Tambahkan transaksi & anggota dari Google Drive tanpa menghapus data lokal saat ini."
    ),
    REPLACE_ALL(
        title = "Ganti Semua Data Lokal",
        description = "Cocok untuk perangkat baru/instal ulang: ganti data lokal dengan isi backup Google Drive."
    )
}

data class RestoreSummary(
    val restoredTransactionsCount: Int,
    val skippedDuplicateCount: Int,
    val totalLocalTransactionsAfter: Int,
    val restoredRosterCount: Int,
    val backupTimestampMillis: Long,
    val mode: RestoreMode
)

data class BackupUiState(
    val isBusy: Boolean = false,
    val busyMessage: String? = null,
    val remoteBackup: DriveBackupMetadata? = null,
    val needsReauth: Boolean = false
)

object BackupJsonCodec {

    fun encodePayload(payload: KasBackupPayload): String {
        val root = JSONObject().apply {
            put("schemaVersion", payload.schemaVersion)
            put("appName", "Kas Kelas")
            put("exportedAtMillis", payload.exportedAtMillis)
            put("accountEmail", payload.accountEmail)
            put("deviceModel", payload.deviceModel)
            put("summary", JSONObject().apply {
                put("transactionCount", payload.transactions.size)
                put("rosterCount", payload.studentRoster.size)
                put("totalIncome", payload.totalIncome)
                put("totalExpense", payload.totalExpense)
                put("balance", payload.balance)
            })
            put("isDarkTheme", payload.isDarkTheme)

            val txArray = JSONArray()
            payload.transactions.forEach { tx ->
                txArray.put(JSONObject().apply {
                    put("id", tx.id)
                    put("type", tx.type)
                    put("title", tx.title)
                    put("amount", tx.amount)
                    put("category", tx.category)
                    put("dateMillis", tx.dateMillis)
                    put("personName", tx.personName)
                    put("notes", tx.notes)
                })
            }
            put("transactions", txArray)

            val rosterArray = JSONArray()
            payload.studentRoster.forEach { name ->
                rosterArray.put(name)
            }
            put("studentRoster", rosterArray)

            put("reminderSettings", JSONObject().apply {
                put("enabled", payload.reminderSettings.enabled)
                put("dayOfMonth", payload.reminderSettings.dayOfMonth)
                put("hour", payload.reminderSettings.hour)
                put("minute", payload.reminderSettings.minute)
                put("monthlyTargetAmount", payload.reminderSettings.monthlyTargetAmount)
                put("customNote", payload.reminderSettings.customNote)
            })
        }
        return root.toString(2)
    }

    fun decodePayload(jsonString: String): KasBackupPayload {
        require(jsonString.isNotBlank()) { "File backup kosong." }
        val root = JSONObject(jsonString)
        val schemaVersion = root.optInt("schemaVersion", 1)
        require(schemaVersion >= 1) { "Format versi backup tidak dikenali." }
        require(root.has("transactions")) { "File bukan backup valid Room Database Kas Kelas." }

        val exportedAtMillis = root.optLong("exportedAtMillis", System.currentTimeMillis())
        val accountEmail = root.optString("accountEmail", "")
        val deviceModel = root.optString("deviceModel", "")
        val isDarkTheme = root.optBoolean("isDarkTheme", false)

        val txList = mutableListOf<TransactionEntity>()
        val txArray = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txArray.length()) {
            val obj = txArray.optJSONObject(i) ?: continue
            val rawType = obj.optString("type", TransactionType.PEMASUKAN.name)
            val normalizedType = if (rawType.equals(TransactionType.PENGELUARAN.name, ignoreCase = true)) {
                TransactionType.PENGELUARAN.name
            } else {
                TransactionType.PEMASUKAN.name
            }
            val title = obj.optString("title", "").trim()
            val amount = obj.optLong("amount", 0L)
            if (title.isBlank() || amount <= 0L) continue

            txList.add(
                TransactionEntity(
                    id = obj.optLong("id", 0L).coerceAtLeast(0L),
                    type = normalizedType,
                    title = title,
                    amount = amount,
                    category = obj.optString("category", ""),
                    dateMillis = obj.optLong("dateMillis", exportedAtMillis),
                    personName = obj.optString("personName", ""),
                    notes = obj.optString("notes", "")
                )
            )
        }

        val roster = mutableListOf<String>()
        val rosterArray = root.optJSONArray("studentRoster") ?: JSONArray()
        for (i in 0 until rosterArray.length()) {
            val name = rosterArray.optString(i, "").trim()
            if (name.isNotBlank()) {
                roster.add(name)
            }
        }

        val reminderObj = root.optJSONObject("reminderSettings")
        val reminderSettings = if (reminderObj != null) {
            ReminderSettings(
                enabled = reminderObj.optBoolean("enabled", false),
                dayOfMonth = reminderObj.optInt("dayOfMonth", 1).coerceIn(1, 28),
                hour = reminderObj.optInt("hour", 8).coerceIn(0, 23),
                minute = reminderObj.optInt("minute", 0).coerceIn(0, 59),
                monthlyTargetAmount = reminderObj.optLong("monthlyTargetAmount", 10_000L).coerceAtLeast(1000L),
                customNote = reminderObj.optString("customNote", "")
                    .takeIf { it.isNotBlank() }
                    ?: "Mohon segera melunasi iuran kas kelas bulan ini."
            )
        } else {
            ReminderSettings()
        }

        return KasBackupPayload(
            schemaVersion = schemaVersion,
            exportedAtMillis = exportedAtMillis,
            accountEmail = accountEmail,
            deviceModel = deviceModel,
            transactions = txList,
            studentRoster = roster.distinctBy { it.lowercase() },
            reminderSettings = reminderSettings,
            isDarkTheme = isDarkTheme
        )
    }
}
