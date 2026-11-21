package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.reminder.KasReminderManager
import com.example.ui.theme.kasIncomeColor
import com.example.ui.theme.kasIncomeContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Dialog for bulk-importing class member names without typing one by one:
 * - Zero-permission SAF import from .csv (RFC-4180 aware with column detection) or .txt file
 * - Multi-line paste (supports numbered/bulleted WhatsApp lists & CSV/tab-separated lines)
 * - 1-tap paste from clipboard
 * - 1-tap import from historical transaction payers
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BulkStudentImportDialog(
    existingRoster: List<String>,
    pastPayerNames: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onImportNames: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rawInputText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val parsedPreview = remember(rawInputText) {
        KasReminderManager.parseStudentNames(rawInputText)
    }

    val existingLowerSet = remember(existingRoster) {
        existingRoster.map { it.trim().lowercase() }.toSet()
    }
    val newUniqueNames = remember(parsedPreview, existingLowerSet) {
        parsedPreview.filter { it.lowercase() !in existingLowerSet }
    }
    val duplicateCount = parsedPreview.size - newUniqueNames.size

    val historicalAvailable = remember(pastPayerNames, existingLowerSet) {
        pastPayerNames
            .map { it.trim() }
            .filter { it.isNotBlank() && it.lowercase() !in existingLowerSet }
            .distinctBy { it.lowercase() }
    }

    fun appendNamesToInput(names: List<String>) {
        if (names.isEmpty()) return
        val current = KasReminderManager.parseStudentNames(rawInputText)
        val combined = (current + names).distinctBy { it.lowercase() }
        rawInputText = combined.joinToString("\n")
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val (fileName, fileText) = withContext(Dispatchers.IO) {
                    val resolvedName = runCatching {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { cursor ->
                                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                            }
                    }.getOrNull() ?: "berkas.csv"

                    val text = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                        }
                    }.getOrNull()

                    resolvedName to text
                }

                if (!fileText.isNullOrBlank()) {
                    val csvResult = KasReminderManager.parseCsvStudentNames(fileText)
                    if (csvResult.names.isNotEmpty()) {
                        appendNamesToInput(csvResult.names)
                        val colSuffix = csvResult.detectedColumnName?.let { " (kolom '$it')" } ?: ""
                        statusMessage = "${csvResult.names.size} nama dimuat dari $fileName$colSuffix."
                    } else {
                        statusMessage = "Tidak ada nama valid ditemukan di $fileName."
                    }
                } else {
                    statusMessage = "Gagal membaca $fileName atau berkas kosong."
                }
            }
        }
    }

    val incomeColor = kasIncomeColor()
    val incomeBg = kasIncomeContainer()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 540.dp)
                .testTag("bulk_student_import_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GroupAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Impor Anggota & File CSV",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Unggah CSV absensi kelas atau tempel daftar sekaligus",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup dialog",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dedicated Primary CSV Upload Banner Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/csv",
                                        "application/vnd.ms-excel",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("bulk_import_file_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unggah File CSV (.csv / .txt)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Mendukung file CSV dari Excel/Google Sheets (otomatis mendeteksi kolom 'Nama' & pemisah koma/titik koma).",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Import Source Buttons (Clipboard & Past Transactions)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                                .orEmpty()
                            if (clipText.isBlank()) {
                                statusMessage = "Clipboard masih kosong. Salin daftar nama dari WhatsApp/Excel terlebih dahulu."
                            } else {
                                val parsed = KasReminderManager.parseStudentNames(clipText)
                                if (parsed.isNotEmpty()) {
                                    appendNamesToInput(parsed)
                                    statusMessage = "${parsed.size} nama ditempel dari clipboard."
                                } else {
                                    rawInputText = clipText
                                    statusMessage = "Teks ditempel, periksa format daftar nama."
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("bulk_paste_clipboard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tempel Clipboard", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (historicalAvailable.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                appendNamesToInput(historicalAvailable)
                                statusMessage = "${historicalAvailable.size} nama diambil dari riwayat pembayar."
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("bulk_import_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Riwayat (${historicalAvailable.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Multi-line Paste TextField
                OutlinedTextField(
                    value = rawInputText,
                    onValueChange = {
                        rawInputText = it
                        statusMessage = null
                    },
                    label = { Text("Atau Tempel Daftar / Isi CSV di Sini") },
                    placeholder = {
                        Text(
                            "Contoh:\nNo,Nama Siswa,Kelas\n1,Andi Pratama,XI-A\n2,Budi Santoso,XI-A",
                            fontSize = 12.sp
                        )
                    },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bulk_import_text_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = statusMessage ?: "",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Preview Section
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (newUniqueNames.isNotEmpty()) {
                        incomeBg.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        1.dp,
                        if (newUniqueNames.isNotEmpty()) incomeColor.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pratinjau Nama (${newUniqueNames.size} baru)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (newUniqueNames.isNotEmpty()) incomeColor else MaterialTheme.colorScheme.onSurface
                            )
                            if (duplicateCount > 0) {
                                Text(
                                    text = "$duplicateCount sudah terdaftar",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (parsedPreview.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Kolom nomor, kelas, tanda strip (-), atau duplikat akan otomatis dibersihkan.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (newUniqueNames.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Semua nama yang terdeteksi sudah ada di daftar anggota kelas.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                newUniqueNames.forEach { name ->
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            val remaining = parsedPreview.filterNot {
                                                it.equals(name, ignoreCase = true)
                                            }
                                            rawInputText = remaining.joinToString("\n")
                                        },
                                        label = { Text(name, fontSize = 11.sp, maxLines = 1) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Hapus $name",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        colors = InputChipDefaults.inputChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.surface,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                            selectedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        border = InputChipDefaults.inputChipBorder(
                                            enabled = true,
                                            selected = true,
                                            selectedBorderColor = incomeColor.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Batal", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            if (newUniqueNames.isNotEmpty()) {
                                onImportNames(newUniqueNames)
                            }
                        },
                        enabled = newUniqueNames.isNotEmpty(),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("confirm_bulk_import_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tambahkan (${newUniqueNames.size})",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
