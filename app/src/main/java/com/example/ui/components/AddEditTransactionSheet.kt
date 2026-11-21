package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.KasCategory
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.data.formatRupiah
import com.example.ui.theme.kasExpenseColor
import com.example.ui.theme.kasExpenseContainer
import com.example.ui.theme.kasIncomeColor
import com.example.ui.theme.kasIncomeContainer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionSheet(
    sheetState: SheetState,
    initialTransaction: TransactionEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        type: TransactionType,
        title: String,
        amount: Long,
        category: String,
        dateMillis: Long,
        personName: String,
        notes: String
    ) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.imePadding()
    ) {
        AddTransactionFormContent(
            initialTransaction = initialTransaction,
            showHeader = true,
            onDismiss = onDismiss,
            onSave = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        )
    }
}

/**
 * Full-screen Composable wrapper for adding or editing a cash transaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    initialTransaction: TransactionEntity? = null,
    onNavigateBack: () -> Unit,
    onSave: (
        type: TransactionType,
        title: String,
        amount: Long,
        category: String,
        dateMillis: Long,
        personName: String,
        notes: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("add_transaction_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (initialTransaction != null) "Edit Catatan Kas" else "Catat Kas Baru",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        AddTransactionFormContent(
            initialTransaction = initialTransaction,
            showHeader = false,
            onDismiss = onNavigateBack,
            onSave = onSave,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionFormContent(
    initialTransaction: TransactionEntity? = null,
    showHeader: Boolean = true,
    onDismiss: () -> Unit = {},
    onSave: (
        type: TransactionType,
        title: String,
        amount: Long,
        category: String,
        dateMillis: Long,
        personName: String,
        notes: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEditMode = initialTransaction != null

    var selectedType by remember(initialTransaction) {
        mutableStateOf(
            if (initialTransaction != null) {
                if (initialTransaction.isIncome) TransactionType.PEMASUKAN else TransactionType.PENGELUARAN
            } else {
                TransactionType.PEMASUKAN
            }
        )
    }

    var amountText by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.amount?.toString() ?: "")
    }

    var titleText by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.title ?: "")
    }

    var personNameText by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.personName ?: "")
    }

    var notesText by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.notes ?: "")
    }

    var selectedDateMillis by remember(initialTransaction) {
        mutableLongStateOf(initialTransaction?.dateMillis ?: System.currentTimeMillis())
    }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    var selectedCategory by remember(selectedType, initialTransaction) {
        mutableStateOf(
            if (initialTransaction != null && initialTransaction.type == selectedType.name) {
                KasCategory.fromString(initialTransaction.category).name
            } else {
                if (selectedType == TransactionType.PEMASUKAN) {
                    KasCategory.IURAN_MINGGUAN.name
                } else {
                    KasCategory.ATK_KELAS.name
                }
            }
        )
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = remember(selectedType) {
        KasCategory.entries.filter { it.defaultType == selectedType }
    }

    val quickAmounts = listOf(2000L, 5000L, 10000L, 20000L, 50000L, 100000L)

    val quickTitleSuggestions = remember(selectedType) {
        if (selectedType == TransactionType.PEMASUKAN) {
            listOf(
                "Uang Kas Mingguan",
                "Uang Kas Bulanan",
                "Donasi Sukarela Kelas",
                "Denda Keterlambatan Piket",
                "Sisa Dana Acara"
            )
        } else {
            listOf(
                "Pembelian Alat Tulis (Spidol/Kertas)",
                "Dana Acara Kelas",
                "Alat Kebersihan Kelas",
                "Fotokopi Materi / Soal",
                "Konsumsi & Snack",
                "Dana Sosial / Jenguk"
            )
        }
    }

    val idLocale = remember { Locale.forLanguageTag("id-ID") }
    val dateFormatter = remember(idLocale) {
        SimpleDateFormat("EEEE, dd MMMM yyyy", idLocale)
    }

    val incomeClr = kasIncomeColor()
    val incomeBg = kasIncomeContainer()
    val expenseClr = kasExpenseColor()
    val expenseBg = kasExpenseContainer()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        cursorColor = MaterialTheme.colorScheme.primary
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .testTag("add_transaction_form")
    ) {
        if (showHeader) {
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
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Description else Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEditMode) "Edit Catatan Kas" else "Catat Kas Baru",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // 1. RadioButton Selector for Income / Expense
        Text(
            text = "Jenis Transaksi *",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .testTag("transaction_type_radio_group"),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isIncomeSelected = selectedType == TransactionType.PEMASUKAN
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .selectable(
                        selected = isIncomeSelected,
                        role = Role.RadioButton,
                        onClick = {
                            selectedType = TransactionType.PEMASUKAN
                            selectedCategory = KasCategory.IURAN_MINGGUAN.name
                        }
                    )
                    .testTag("type_income_button"),
                color = if (isIncomeSelected) incomeBg else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    width = if (isIncomeSelected) 1.6.dp else 1.dp,
                    color = if (isIncomeSelected) incomeClr else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isIncomeSelected,
                        onClick = null,
                        modifier = Modifier.testTag("radio_income"),
                        colors = RadioButtonDefaults.colors(
                            selectedColor = incomeClr,
                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pemasukan",
                        fontWeight = FontWeight.Bold,
                        color = if (isIncomeSelected) incomeClr else MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val isExpenseSelected = selectedType == TransactionType.PENGELUARAN
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .selectable(
                        selected = isExpenseSelected,
                        role = Role.RadioButton,
                        onClick = {
                            selectedType = TransactionType.PENGELUARAN
                            selectedCategory = KasCategory.ATK_KELAS.name
                        }
                    )
                    .testTag("type_expense_button"),
                color = if (isExpenseSelected) expenseBg else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    width = if (isExpenseSelected) 1.6.dp else 1.dp,
                    color = if (isExpenseSelected) expenseClr else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isExpenseSelected,
                        onClick = null,
                        modifier = Modifier.testTag("radio_expense"),
                        colors = RadioButtonDefaults.colors(
                            selectedColor = expenseClr,
                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pengeluaran",
                        fontWeight = FontWeight.Bold,
                        color = if (isExpenseSelected) expenseClr else MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Amount Input Field
        OutlinedTextField(
            value = amountText,
            onValueChange = { input ->
                val clean = input.filter { it.isDigit() }
                amountText = clean
                errorMessage = null
            },
            label = { Text("Nominal / Jumlah Uang (Rp) *") },
            prefix = {
                Text(
                    "Rp ",
                    fontWeight = FontWeight.Bold,
                    color = if (selectedType == TransactionType.PEMASUKAN) incomeClr else expenseClr
                )
            },
            placeholder = { Text("0") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("amount_input_field"),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Nominal Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickAmounts.forEach { amt ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val current = amountText.toLongOrNull() ?: 0L
                            amountText = (current + amt).toString()
                            errorMessage = null
                        },
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "+${formatRupiah(amt)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Description Input Field
        OutlinedTextField(
            value = titleText,
            onValueChange = {
                titleText = it
                errorMessage = null
            },
            label = { Text("Deskripsi / Keterangan Transaksi *") },
            placeholder = { Text("Contoh: Uang Kas / Pembelian Alat Tulis") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("title_input_field"),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickTitleSuggestions.forEach { suggestion ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            titleText = suggestion
                            errorMessage = null
                        },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = suggestion,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Date Picker Field + Quick Date Shortcuts
        Text(
            text = "Tanggal Transaksi *",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedCard(
            onClick = { showDatePickerDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .testTag("date_picker_field"),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = "Pilih tanggal transaksi",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = dateFormatter.format(Date(selectedDateMillis)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Pilih Tanggal",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val todayMillis = System.currentTimeMillis()
            val yesterdayMillis = todayMillis - 24L * 60L * 60L * 1000L
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { selectedDateMillis = System.currentTimeMillis() }
                    .testTag("quick_date_today"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Hari Ini",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { selectedDateMillis = yesterdayMillis }
                    .testTag("quick_date_yesterday"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Kemarin",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Person / Student Name Field
        OutlinedTextField(
            value = personNameText,
            onValueChange = { personNameText = it },
            label = {
                Text(
                    if (selectedType == TransactionType.PEMASUKAN) "Nama Pembayar / Siswa"
                    else "Penanggung Jawab / Toko (Opsional)"
                )
            },
            placeholder = {
                Text(
                    if (selectedType == TransactionType.PEMASUKAN) "Contoh: Budi (muncul di laporan share)"
                    else "Contoh: Siti (Bendahara) / Toko Makmur"
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("person_input_field"),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 6. Category Assignment Section
        Text(
            text = if (selectedType == TransactionType.PEMASUKAN) {
                "Label Kategori Pemasukan:"
            } else {
                "Label Kategori Pengeluaran:"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("category_selection_flow"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = selectedCategory == cat.name
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = cat.name },
                    modifier = Modifier.testTag("category_chip_${cat.name}"),
                    label = {
                        Text(
                            text = cat.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = getCategoryIcon(cat.name),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 7. Optional Notes
        OutlinedTextField(
            value = notesText,
            onValueChange = { notesText = it },
            label = { Text("Catatan Tambahan (Opsional)") },
            placeholder = { Text("Detail barang, bukti nota, atau keterangan lain...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Notes,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("notes_input_field"),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 8. Submit Button
        Button(
            onClick = {
                val amount = amountText.toLongOrNull()
                if (amount == null || amount <= 0) {
                    errorMessage = "Mohon masukkan nominal uang yang valid (lebih dari 0)"
                    return@Button
                }
                if (titleText.isBlank()) {
                    errorMessage = "Mohon isi deskripsi transaksi"
                    return@Button
                }

                onSave(
                    selectedType,
                    titleText.trim(),
                    amount,
                    selectedCategory,
                    selectedDateMillis,
                    personNameText.trim(),
                    notesText.trim()
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_transaction_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isEditMode) "Simpan Perubahan" else "Simpan Transaksi Kas",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localMillisToUtcMidnight(selectedDateMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            modifier = Modifier.testTag("date_picker_dialog"),
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            selectedDateMillis = mergeUtcDateWithLocalTime(utcMillis, selectedDateMillis)
                        }
                        showDatePickerDialog = false
                    },
                    modifier = Modifier.testTag("confirm_date_picker_button")
                ) {
                    Text("Pilih", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Batal")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun localMillisToUtcMidnight(localMillis: Long): Long {
    val localCal = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            localCal.get(Calendar.YEAR),
            localCal.get(Calendar.MONTH),
            localCal.get(Calendar.DAY_OF_MONTH),
            0,
            0,
            0
        )
    }.timeInMillis
}

private fun mergeUtcDateWithLocalTime(utcDateMillis: Long, referenceLocalMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcDateMillis
    }
    val refCal = Calendar.getInstance().apply {
        timeInMillis = referenceLocalMillis
    }
    return Calendar.getInstance().apply {
        set(
            utcCal.get(Calendar.YEAR),
            utcCal.get(Calendar.MONTH),
            utcCal.get(Calendar.DAY_OF_MONTH),
            refCal.get(Calendar.HOUR_OF_DAY),
            refCal.get(Calendar.MINUTE),
            refCal.get(Calendar.SECOND)
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
