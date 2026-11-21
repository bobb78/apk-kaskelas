package com.example.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.backup.BackupUiState
import com.example.backup.DriveAuthOutcome
import com.example.backup.DriveBackupManager
import com.example.backup.DriveBackupSettings
import com.example.backup.OkHttpGoogleDriveApi
import com.example.backup.RestoreMode
import com.example.data.formatRupiah
import com.example.ui.theme.GeoBrandBlue
import com.example.ui.theme.GeoNavy
import com.example.ui.theme.GeoSoftBlueContainer
import com.example.ui.theme.LocalKasIsDark
import com.example.ui.theme.kasExpenseColor
import com.example.ui.theme.kasGoldColor
import com.example.ui.theme.kasGoldContainer
import com.example.ui.theme.kasIncomeColor
import com.example.ui.theme.kasIncomeContainer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingDriveAction {
    CONNECT,
    BACKUP_NOW,
    CHECK_REMOTE,
    RESTORE_MERGE,
    RESTORE_REPLACE
}

@Composable
fun KasBackupScreen(
    viewModel: KasViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val backupSettings by viewModel.driveBackupSettings.collectAsStateWithLifecycle()
    val uiState by viewModel.backupUiState.collectAsStateWithLifecycle()
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val roster by viewModel.studentRoster.collectAsStateWithLifecycle()

    var pendingAction by remember { mutableStateOf<PendingDriveAction?>(null) }
    var pendingLocalJsonToRestore by remember { mutableStateOf<String?>(null) }

    val saveBackupJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportLocalBackupJson { res ->
                res.onSuccess { jsonStr ->
                    val writeResult = runCatching {
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                            it.write(jsonStr)
                        } ?: error("Gagal membuka berkas tujuan")
                    }
                    if (writeResult.isSuccess) {
                        onShowMessage("Backup file JSON berhasil disimpan (${transactions.size} transaksi)!")
                    } else {
                        onShowMessage("Gagal menulis file backup JSON.")
                    }
                }.onFailure { err ->
                    onShowMessage(err.localizedMessage ?: "Gagal menyiapkan backup JSON.")
                }
            }
        }
    }

    val openBackupJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val readRes = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: error("Gagal membaca file backup")
            }
            readRes.onSuccess { content ->
                if (content.isBlank()) {
                    onShowMessage("File yang dipilih kosong.")
                } else {
                    pendingLocalJsonToRestore = content
                }
            }.onFailure {
                onShowMessage("Gagal membaca file backup yang dipilih.")
            }
        }
    }

    fun executeAuthorizedAction(action: PendingDriveAction, accessToken: String) {
        when (action) {
            PendingDriveAction.CONNECT -> {
                viewModel.handleGoogleDriveConnected(accessToken) { _, msg ->
                    onShowMessage(msg)
                }
            }

            PendingDriveAction.BACKUP_NOW -> {
                viewModel.backupToDriveWithToken(accessToken) { _, msg ->
                    onShowMessage(msg)
                }
            }

            PendingDriveAction.CHECK_REMOTE -> {
                viewModel.checkRemoteDriveBackupWithToken(accessToken) { _, msg ->
                    onShowMessage(msg)
                }
            }

            PendingDriveAction.RESTORE_MERGE -> {
                viewModel.restoreFromDriveWithToken(accessToken, RestoreMode.MERGE_MISSING) { _, msg ->
                    onShowMessage(msg)
                }
            }

            PendingDriveAction.RESTORE_REPLACE -> {
                viewModel.restoreFromDriveWithToken(accessToken, RestoreMode.REPLACE_ALL) { _, msg ->
                    onShowMessage(msg)
                }
            }
        }
    }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val action = pendingAction ?: PendingDriveAction.CONNECT
        pendingAction = null
        if (result.resultCode == Activity.RESULT_OK) {
            val tokenRes = DriveBackupManager.extractTokenFromIntent(context, result.data)
            tokenRes.onSuccess { token ->
                executeAuthorizedAction(action, token)
            }.onFailure { err ->
                onShowMessage(err.localizedMessage ?: "Otorisasi Google Drive dibatalkan.")
            }
        } else {
            onShowMessage("Persetujuan Google Drive dibatalkan.")
        }
    }

    fun launchWithDriveAuth(action: PendingDriveAction) {
        scope.launch {
            when (val authOutcome = DriveBackupManager.authorizeDrive(context)) {
                is DriveAuthOutcome.Authorized -> {
                    executeAuthorizedAction(action, authOutcome.accessToken)
                }

                is DriveAuthOutcome.NeedsConsent -> {
                    pendingAction = action
                    authLauncher.launch(
                        IntentSenderRequest.Builder(authOutcome.pendingIntent.intentSender).build()
                    )
                }

                is DriveAuthOutcome.Error -> {
                    onShowMessage(authOutcome.message)
                }
            }
        }
    }

    KasBackupContent(
        settings = backupSettings,
        uiState = uiState,
        localTransactionCount = transactions.size,
        localRosterCount = roster.size,
        onConnectOrBackupClick = {
            if (!backupSettings.isConnected || uiState.needsReauth) {
                launchWithDriveAuth(PendingDriveAction.CONNECT)
            } else {
                launchWithDriveAuth(PendingDriveAction.BACKUP_NOW)
            }
        },
        onUpdateSettings = { updated ->
            viewModel.updateDriveBackupSettings(updated)
            if (updated.autoBackupEnabled && !updated.isConnected) {
                launchWithDriveAuth(PendingDriveAction.CONNECT)
            } else if (updated.autoBackupEnabled) {
                onShowMessage("Backup otomatis Google Drive diperbarui (setiap ${updated.intervalHours} jam).")
            } else {
                onShowMessage("Backup otomatis terjadwal dinonaktifkan.")
            }
        },
        onCheckRemoteBackup = { launchWithDriveAuth(PendingDriveAction.CHECK_REMOTE) },
        onRestoreBackup = { mode ->
            launchWithDriveAuth(
                if (mode == RestoreMode.REPLACE_ALL) PendingDriveAction.RESTORE_REPLACE
                else PendingDriveAction.RESTORE_MERGE
            )
        },
        onDisconnect = {
            viewModel.disconnectDriveAccount {
                onShowMessage("Akun Google Drive berhasil diputuskan.")
            }
        },
        onExportLocalJsonClick = {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            saveBackupJsonLauncher.launch("KasKelas_Backup_$stamp.json")
        },
        onImportLocalJsonClick = {
            openBackupJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        },
        modifier = modifier
    )

    val localJsonPending = pendingLocalJsonToRestore
    if (localJsonPending != null) {
        val expenseClr = kasExpenseColor()
        AlertDialog(
            onDismissRequest = { pendingLocalJsonToRestore = null },
            modifier = Modifier.testTag("local_restore_confirm_dialog"),
            title = {
                Text(
                    text = "Pulihkan dari File Backup",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Pilih cara memulihkan data dari file JSON lokal:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedCard(
                        onClick = {
                            val raw = localJsonPending
                            pendingLocalJsonToRestore = null
                            viewModel.restoreFromLocalJson(raw, RestoreMode.MERGE_MISSING) { _, msg ->
                                onShowMessage(msg)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_restore_mode_merge"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = RestoreMode.MERGE_MISSING.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tambahkan transaksi & anggota dari file tanpa menghapus data lokal saat ini.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedCard(
                        onClick = {
                            val raw = localJsonPending
                            pendingLocalJsonToRestore = null
                            viewModel.restoreFromLocalJson(raw, RestoreMode.REPLACE_ALL) { _, msg ->
                                onShowMessage(msg)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("local_restore_mode_replace"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, expenseClr.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = RestoreMode.REPLACE_ALL.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = expenseClr
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ganti seluruh data lokal dengan isi file cadangan JSON.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingLocalJsonToRestore = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KasBackupContent(
    settings: DriveBackupSettings,
    uiState: BackupUiState,
    localTransactionCount: Int,
    localRosterCount: Int,
    onConnectOrBackupClick: () -> Unit,
    onUpdateSettings: (DriveBackupSettings) -> Unit,
    onCheckRemoteBackup: () -> Unit,
    onRestoreBackup: (RestoreMode) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    onExportLocalJsonClick: () -> Unit = {},
    onImportLocalJsonClick: () -> Unit = {}
) {
    val isDark = LocalKasIsDark.current
    val incomeColor = kasIncomeColor()
    val incomeContainer = kasIncomeContainer()
    val goldColor = kasGoldColor()
    val goldContainer = kasGoldContainer()
    val expenseColor = kasExpenseColor()
    val heroCardBg = if (isDark) Color(0xFF0E233B) else GeoNavy
    val heroTextColor = Color.White
    val heroSubTextColor = Color(0xFFDBEAFE)

    var showRestoreDialog by remember { mutableStateOf(false) }

    val idLocale = remember { Locale.forLanguageTag("id-ID") }
    val dateFormatter = remember(idLocale) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", idLocale)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("kas_backup_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Card: Google Drive Connection & Backup Status
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backup_hero_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = heroCardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (settings.isConnected) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Backup Google Drive",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = heroTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = settings.connectedEmail ?: "Belum terhubung ke akun Google",
                                    fontSize = 12.sp,
                                    color = heroSubTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            color = when {
                                settings.isConnected && settings.autoBackupEnabled -> incomeContainer
                                settings.isConnected -> GeoSoftBlueContainer
                                else -> goldContainer
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = when {
                                    settings.isConnected && settings.autoBackupEnabled -> "Otomatis Aktif"
                                    settings.isConnected -> "Manual"
                                    else -> "Belum Aktif"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    settings.isConnected && settings.autoBackupEnabled -> incomeColor
                                    settings.isConnected -> GeoBrandBlue
                                    else -> goldColor
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Local data summary & last backup timestamp
                    Surface(
                        color = Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Data Lokal Saat Ini: $localTransactionCount transaksi • $localRosterCount anggota kelas",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = heroTextColor
                            )
                            val lastTimeStr = if (settings.lastBackupMillis > 0L) {
                                dateFormatter.format(Date(settings.lastBackupMillis))
                            } else {
                                "Belum pernah"
                            }
                            Text(
                                text = "Backup Terakhir: $lastTimeStr",
                                fontSize = 11.sp,
                                color = heroSubTextColor
                            )
                            Text(
                                text = settings.lastBackupStatus,
                                fontSize = 11.sp,
                                color = heroSubTextColor.copy(alpha = 0.9f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (uiState.isBusy) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF93C5FD),
                                trackColor = Color.White.copy(alpha = 0.18f)
                            )
                            Text(
                                text = uiState.busyMessage ?: "Menyinkronkan dengan Google Drive...",
                                fontSize = 11.sp,
                                color = heroSubTextColor
                            )
                        }
                    }

                    Button(
                        onClick = onConnectOrBackupClick,
                        enabled = !uiState.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("drive_connect_or_backup_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = GeoNavy
                        )
                    ) {
                        Icon(
                            imageVector = if (!settings.isConnected || uiState.needsReauth) {
                                Icons.Rounded.CloudUpload
                            } else {
                                Icons.Rounded.Backup
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                !settings.isConnected -> "Hubungkan Google Drive & Aktifkan Backup"
                                uiState.needsReauth -> "Otorisasi Ulang Google Drive"
                                else -> "Cadangkan Sekarang ke Drive"
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 2. Automatic Backup Settings Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auto_backup_settings_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Pengaturan Backup Otomatis",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Room Database dicadangkan otomatis di latar belakang",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Switch 1: Scheduled Periodic Backup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Backup Otomatis Terjadwal",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Sinkronisasi rutin otomatis di latar belakang",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.autoBackupEnabled,
                            onCheckedChange = { checked ->
                                onUpdateSettings(settings.copy(autoBackupEnabled = checked))
                            },
                            modifier = Modifier.testTag("auto_backup_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // Frequency Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Frekuensi Backup Terjadwal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(12 to "Setiap 12 Jam", 24 to "Setiap 24 Jam").forEach { (hours, label) ->
                                val selected = settings.intervalHours == hours
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        onUpdateSettings(settings.copy(intervalHours = hours))
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .testTag("backup_interval_$hours"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }

                    // Switch 2: Sync immediately when Room DB changes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Sinkron Otomatis Saat Ada Perubahan",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Unggah otomatis beberapa detik setelah catat/edit transaksi",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.syncOnDataChange,
                            onCheckedChange = { checked ->
                                onUpdateSettings(settings.copy(syncOnDataChange = checked))
                            },
                            enabled = settings.autoBackupEnabled,
                            modifier = Modifier.testTag("sync_on_change_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // Switch 3: Wi-Fi only constraint
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Hanya Backup via Wi-Fi",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Hemat kuota seluler saat sinkronisasi latar belakang",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.wifiOnly,
                            onCheckedChange = { checked ->
                                onUpdateSettings(settings.copy(wifiOnly = checked))
                            },
                            modifier = Modifier.testTag("wifi_only_backup_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }

        // 3. Restore from Google Drive Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drive_restore_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Restore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Pulihkan Data (Ganti HP / Instal Ulang)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Kembalikan riwayat transaksi & daftar anggota dari Google Drive",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val remote = uiState.remoteBackup
                    if (remote != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = incomeContainer.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, incomeColor.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("remote_backup_info_box")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = incomeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Backup Tersedia di Google Drive",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = incomeColor
                                    )
                                }
                                Text(
                                    text = "Diperbarui: ${dateFormatter.format(Date(remote.modifiedTimeMillis))}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val detailParts = buildList {
                                    remote.transactionCount?.let { add("$it transaksi") }
                                    remote.rosterCount?.let { add("$it anggota") }
                                    remote.balance?.let { add("Saldo ${formatRupiah(it)}") }
                                }
                                if (detailParts.isNotEmpty()) {
                                    Text(
                                        text = detailParts.joinToString(" • "),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Ketuk 'Periksa Backup' untuk mengecek file cadangan terbaru di Google Drive Anda.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCheckRemoteBackup,
                            enabled = !uiState.isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("check_remote_backup_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Periksa Backup",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = { showRestoreDialog = true },
                            enabled = !uiState.isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("restore_drive_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pulihkan Data",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 4. Offline Local JSON File Backup & Restore Card (works without Google OAuth)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("local_json_backup_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.SaveAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Backup File Lokal (Offline JSON)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Simpan atau pindahkan data ke HP lain tanpa perlu login Google",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onExportLocalJsonClick,
                            enabled = !uiState.isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_local_json_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SaveAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Simpan (.json)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = onImportLocalJsonClick,
                            enabled = !uiState.isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("import_local_json_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Impor (.json)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 5. Security & Storage Location Info + Disconnect Option
        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Lokasi & Cakupan Backup",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Disimpan aman di folder '${OkHttpGoogleDriveApi.BACKUP_FOLDER_NAME}/${OkHttpGoogleDriveApi.BACKUP_FILE_NAME}' pada Google Drive pribadi Anda atau sebagai file JSON lokal. Mencakup seluruh transaksi Room Database, daftar anggota kelas, dan pengaturan target iuran.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (settings.isConnected) {
                        OutlinedButton(
                            onClick = onDisconnect,
                            enabled = !uiState.isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("disconnect_drive_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, expenseColor.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = expenseColor)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LinkOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Putuskan Akun Google Drive", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            modifier = Modifier.testTag("restore_confirm_dialog"),
            title = {
                Text(
                    text = "Pilih Metode Pemulihan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Pilih cara memulihkan data dari file cadangan Google Drive:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedCard(
                        onClick = {
                            showRestoreDialog = false
                            onRestoreBackup(RestoreMode.MERGE_MISSING)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_mode_merge"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = RestoreMode.MERGE_MISSING.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = RestoreMode.MERGE_MISSING.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedCard(
                        onClick = {
                            showRestoreDialog = false
                            onRestoreBackup(RestoreMode.REPLACE_ALL)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_mode_replace"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, expenseColor.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = RestoreMode.REPLACE_ALL.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = expenseColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = RestoreMode.REPLACE_ALL.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
