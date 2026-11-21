package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.formatRupiah
import com.example.reminder.KasReminderManager
import com.example.ui.theme.kasExpenseColor
import com.example.ui.theme.kasIncomeColor
import com.example.ui.theme.kasIncomeContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickIuranSheet(
    sheetState: SheetState,
    externalRoster: List<String>? = null,
    pastPayerNames: List<String> = emptyList(),
    onRosterAdded: ((String) -> Unit)? = null,
    onRosterRemoved: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSubmitBatch: (amountPerStudent: Long, selectedStudents: List<String>, weekTitle: String, notes: String) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("kas_student_roster", Context.MODE_PRIVATE) }

    val initialRoster = remember {
        externalRoster ?: run {
            val raw = sharedPrefs.getString("saved_students", "") ?: ""
            if (raw.isNotBlank()) raw.split("|||").filter { it.isNotBlank() } else emptyList()
        }
    }

    var amountPerStudentText by remember { mutableStateOf("5000") }
    var weekTitleText by remember { mutableStateOf("Iuran Kas Mingguan") }
    var newStudentName by remember { mutableStateOf("") }

    val studentList = remember { mutableStateListOf<String>().apply { addAll(initialRoster) } }
    val selectedStudents = remember { mutableStateListOf<String>() }

    LaunchedEffect(externalRoster) {
        if (externalRoster != null) {
            studentList.clear()
            studentList.addAll(externalRoster)
            selectedStudents.removeAll { sel ->
                externalRoster.none { it.equals(sel, ignoreCase = true) }
            }
        }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showBulkImportDialog by remember { mutableStateOf(false) }

    fun persistStudents(updatedList: List<String>) {
        sharedPrefs.edit().putString("saved_students", updatedList.joinToString("|||")).apply()
    }

    fun importEntries(entries: List<String>) {
        if (entries.isEmpty()) return
        var addedCount = 0
        for (name in entries) {
            val existing = studentList.find { it.equals(name, ignoreCase = true) }
            if (existing == null) {
                studentList.add(name)
                if (!selectedStudents.any { it.equals(name, ignoreCase = true) }) {
                    selectedStudents.add(name)
                }
                addedCount++
            } else if (!selectedStudents.any { it.equals(existing, ignoreCase = true) }) {
                selectedStudents.add(existing)
            }
        }

        if (addedCount > 0) {
            if (onRosterAdded != null) {
                onRosterAdded(entries.joinToString("\n"))
            } else {
                persistStudents(studentList.toList())
            }
            errorMessage = null
        }
    }

    fun addStudent() {
        val entries = KasReminderManager.parseStudentNames(newStudentName)
        if (entries.isEmpty()) return
        importEntries(entries)
        newStudentName = ""
        errorMessage = null
    }

    fun selectAllStudents() {
        if (newStudentName.isNotBlank()) {
            addStudent()
        }
        selectedStudents.clear()
        selectedStudents.addAll(studentList)
        errorMessage = null
    }

    fun removeStudent(student: String) {
        studentList.remove(student)
        selectedStudents.remove(student)
        if (onRosterRemoved != null) {
            onRosterRemoved(student)
        } else {
            persistStudents(studentList.toList())
        }
    }

    val totalCalculatedAmount = (amountPerStudentText.toLongOrNull() ?: 0L) * selectedStudents.size
    val incomeColor = kasIncomeColor()
    val incomeContainer = kasIncomeContainer()
    val expenseColor = kasExpenseColor()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(incomeContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = incomeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Catat Iuran Cepat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Tandai siswa yang sudah membayar iuran",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Total Estimation Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = incomeContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = "Total Iuran Terkumpul",
                            fontSize = 12.sp,
                            color = incomeColor,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatRupiah(totalCalculatedAmount),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = incomeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = incomeColor,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${selectedStudents.size} Siswa Bayar",
                            color = MaterialTheme.colorScheme.surface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Nominal Per Siswa
            OutlinedTextField(
                value = amountPerStudentText,
                onValueChange = { input ->
                    amountPerStudentText = input.filter { it.isDigit() }
                    errorMessage = null
                },
                label = { Text("Nominal Iuran Per Siswa (Rp)") },
                prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = incomeColor) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick preset nominals
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("2000", "5000", "10000", "15000", "20000").forEach { preset ->
                    val isPresetSelected = amountPerStudentText == preset
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                amountPerStudentText = preset
                            },
                        color = if (isPresetSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = formatRupiah(preset.toLong()),
                            fontSize = 12.sp,
                            color = if (isPresetSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Week Title
            OutlinedTextField(
                value = weekTitleText,
                onValueChange = { weekTitleText = it },
                label = { Text("Nama Iuran / Minggu ke-") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Students Checklist Section Header
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daftar Nama Siswa (${selectedStudents.size}/${studentList.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { showBulkImportDialog = true },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("open_bulk_import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Impor CSV",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (studentList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { selectAllStudents() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("select_all_students_button"),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pilih Semua (${studentList.size})",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            onClick = { selectedStudents.clear() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("clear_all_students_button"),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Batal Semua",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Student Chips Grid or Empty State
            if (studentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Belum ada daftar nama siswa",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Unggah file CSV absensi atau tempel daftar nama tanpa mengetik satu per satu.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showBulkImportDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Impor dari File CSV / Daftar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    studentList.forEach { student ->
                        val isChecked = selectedStudents.any { it.equals(student, ignoreCase = true) }
                        FilterChip(
                            selected = isChecked,
                            onClick = {
                                if (isChecked) {
                                    selectedStudents.removeAll { it.equals(student, ignoreCase = true) }
                                } else {
                                    selectedStudents.add(student)
                                }
                            },
                            label = { Text(student, fontSize = 12.sp) },
                            leadingIcon = {
                                if (isChecked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Sudah bayar",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            },
                            trailingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isChecked) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                                            else MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                        .clickable {
                                            removeStudent(student)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus $student dari daftar",
                                        modifier = Modifier.size(12.dp),
                                        tint = if (isChecked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isChecked,
                                borderColor = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Add Custom Student Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newStudentName,
                    onValueChange = {
                        newStudentName = it
                        errorMessage = null
                    },
                    label = { Text("Tambah Nama Siswa") },
                    placeholder = { Text("Contoh: Dimas, Siti, Budi") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("student_name_input"),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addStudent() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { addStudent() },
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("add_student_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah", fontSize = 13.sp)
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "",
                    color = expenseColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    if (newStudentName.isNotBlank()) {
                        addStudent()
                    }
                    val amount = amountPerStudentText.toLongOrNull()
                    if (amount == null || amount <= 0) {
                        errorMessage = "Nominal iuran tidak boleh kosong"
                        return@Button
                    }
                    if (selectedStudents.isEmpty()) {
                        errorMessage = "Pilih minimal 1 siswa yang sudah membayar iuran"
                        return@Button
                    }

                    onSubmitBatch(
                        amount,
                        selectedStudents.toList(),
                        weekTitleText.ifBlank { "Iuran Kas Mingguan" },
                        "Iuran kas kelas"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_quick_iuran_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = com.example.ui.theme.IncomeGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Catat ${selectedStudents.size} Iuran (${formatRupiah(totalCalculatedAmount)})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showBulkImportDialog) {
        BulkStudentImportDialog(
            existingRoster = studentList.toList(),
            pastPayerNames = pastPayerNames,
            onDismiss = { showBulkImportDialog = false },
            onImportNames = { imported ->
                importEntries(imported)
                showBulkImportDialog = false
            }
        )
    }
}
