package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.KasPdfExporter
import com.example.data.TransactionEntity
import com.example.ui.components.AddEditTransactionSheet
import com.example.ui.components.PdfExportScope
import com.example.ui.components.QuickIuranSheet
import com.example.ui.components.ShareReportSheet
import com.example.ui.components.TransactionDetailDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KasHomeScreen(
    viewModel: KasViewModel,
    transactionViewModel: TransactionViewModel? = null,
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val summary by (transactionViewModel?.summary ?: viewModel.summary).collectAsStateWithLifecycle()
    val transactions by (transactionViewModel?.filteredTransactions ?: viewModel.filteredTransactions).collectAsStateWithLifecycle()
    val allTransactions by (transactionViewModel?.transactions ?: viewModel.allTransactions).collectAsStateWithLifecycle()
    val currentFilterType by (transactionViewModel?.selectedFilterType ?: viewModel.selectedFilterType).collectAsStateWithLifecycle()
    val searchQuery by (transactionViewModel?.searchQuery ?: viewModel.searchQuery).collectAsStateWithLifecycle()
    val selectedCategoryFilter by (transactionViewModel?.selectedCategoryFilter ?: viewModel.selectedCategoryFilter).collectAsStateWithLifecycle()
    val studentRoster by viewModel.studentRoster.collectAsStateWithLifecycle()
    val unpaidSummary by viewModel.unpaidSummary.collectAsStateWithLifecycle()
    val analyticsData by viewModel.analyticsData.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val historicalPayerNames = remember(allTransactions) {
        viewModel.getHistoricalPayerNames(allTransactions)
    }

    var showSearchField by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showQuickIuranSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var selectedPdfScope by remember { mutableStateOf(PdfExportScope.ALL) }
    var selectedTransactionForDetail by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var currentNavTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 3)) } // 0: Beranda, 1: Laporan, 2: Pengingat, 3: Backup

    LaunchedEffect(initialTab) {
        if (initialTab in 0..3) {
            currentNavTab = initialTab
        }
    }

    BackHandler(enabled = currentNavTab != 0) {
        currentNavTab = 0
    }

    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val quickIuranSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val hasActiveFilter = searchQuery.isNotBlank() ||
        currentFilterType != FilterType.ALL ||
        selectedCategoryFilter != null

    val effectivePdfScope = if (selectedPdfScope == PdfExportScope.FILTERED && !hasActiveFilter) {
        PdfExportScope.ALL
    } else {
        selectedPdfScope
    }

    val scopedPdfTransactions = remember(effectivePdfScope, allTransactions, transactions) {
        when (effectivePdfScope) {
            PdfExportScope.ALL -> allTransactions
            PdfExportScope.THIS_MONTH -> viewModel.getCurrentMonthTransactions(allTransactions)
            PdfExportScope.FILTERED -> transactions
        }
    }

    val scopedPdfSummary = remember(scopedPdfTransactions, summary, effectivePdfScope) {
        if (effectivePdfScope == PdfExportScope.ALL) summary
        else viewModel.computeSummaryFor(scopedPdfTransactions)
    }

    val scopedPdfPaidPayers = remember(scopedPdfTransactions) {
        viewModel.getPaidPayers(scopedPdfTransactions)
    }

    val scopedPeriodLabel = remember(effectivePdfScope, unpaidSummary.monthLabel) {
        when (effectivePdfScope) {
            PdfExportScope.ALL -> "Semua Transaksi"
            PdfExportScope.THIS_MONTH -> "Bulan ${unpaidSummary.monthLabel}"
            PdfExportScope.FILTERED -> "Hasil Filter Aktif"
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            isExportingPdf = true
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { outStream ->
                            KasPdfExporter.writePdfToOutputStream(
                                outputStream = outStream,
                                summary = scopedPdfSummary,
                                transactions = scopedPdfTransactions,
                                paidPayers = scopedPdfPaidPayers,
                                unpaidMembers = unpaidSummary.unpaidMembers,
                                periodLabel = scopedPeriodLabel
                            )
                        } ?: error("Gagal membuka berkas tujuan")
                    }.isSuccess
                }
                isExportingPdf = false
                showShareSheet = false
                snackbarHostState.showSnackbar(
                    if (success) "Laporan PDF berhasil disimpan!"
                    else "Gagal menyimpan laporan PDF."
                )
            }
        }
    }

    val printPdfReport: () -> Unit = {
        val started = KasPdfExporter.printPdfReport(
            context = context,
            summary = scopedPdfSummary,
            transactions = scopedPdfTransactions,
            paidPayers = scopedPdfPaidPayers,
            unpaidMembers = unpaidSummary.unpaidMembers,
            periodLabel = scopedPeriodLabel
        )
        showShareSheet = false
        if (!started) {
            scope.launch {
                snackbarHostState.showSnackbar("Layanan cetak tidak tersedia pada perangkat ini.")
            }
        }
    }

    val sharePdfReport: () -> Unit = {
        isExportingPdf = true
        scope.launch {
            val shareIntentResult = withContext(Dispatchers.IO) {
                runCatching {
                    val pdfFile = KasPdfExporter.exportToCacheFile(
                        context = context,
                        summary = scopedPdfSummary,
                        transactions = scopedPdfTransactions,
                        paidPayers = scopedPdfPaidPayers,
                        unpaidMembers = unpaidSummary.unpaidMembers,
                        periodLabel = scopedPeriodLabel
                    )
                    val pdfUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        pdfFile
                    )
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, pdfUri)
                        putExtra(Intent.EXTRA_SUBJECT, "Laporan Keuangan Kas Kelas ($scopedPeriodLabel)")
                        putExtra(Intent.EXTRA_TEXT, "Berikut terlampir laporan keuangan Kas Kelas dalam format PDF.")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            }
            isExportingPdf = false
            showShareSheet = false
            shareIntentResult.onSuccess { intent ->
                context.startActivity(Intent.createChooser(intent, "Bagikan Laporan PDF Kas Kelas"))
            }.onFailure {
                snackbarHostState.showSnackbar("Gagal mengekspor laporan PDF.")
            }
        }
    }

    val todayFormatted = remember {
        val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.forLanguageTag("id-ID"))
        sdf.format(Date())
    }

    val shareReport: () -> Unit = {
        showShareSheet = true
    }

    val launchExternalShare: (String, String) -> Unit = { title, text ->
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, title))
    }

    val copyToClipboard: (String, String) -> Unit = { text, toastMessage ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Kas Kelas", text))
        scope.launch {
            snackbarHostState.showSnackbar(toastMessage)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
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
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (currentNavTab) {
                                        1 -> "Laporan Kas Kelas"
                                        2 -> "Anggota & Tagihan Kas"
                                        3 -> "Backup Google Drive"
                                        else -> "Buku Kas Kelas"
                                    },
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when (currentNavTab) {
                                        1 -> "Visualisasi Tren & Kategori"
                                        2 -> "Pantau Iuran & Kirim Tagihan WA"
                                        3 -> "Sinkronisasi Otomatis Room DB"
                                        else -> todayFormatted
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Material 3 48dp+ Tappable Top Action Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentNavTab == 0) {
                                FilledTonalIconButton(
                                    onClick = {
                                        showSearchField = !showSearchField
                                        if (!showSearchField) {
                                            transactionViewModel?.setSearchQuery("") ?: viewModel.setSearchQuery("")
                                        }
                                    },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (showSearchField) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                        contentColor = if (showSearchField) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    ),
                                    modifier = Modifier.testTag("search_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (showSearchField) Icons.Rounded.Clear else Icons.Rounded.Search,
                                        contentDescription = "Cari transaksi"
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = { viewModel.toggleTheme() },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isDarkTheme) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    contentColor = if (isDarkTheme) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                ),
                                modifier = Modifier.testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = if (isDarkTheme) "Aktifkan mode terang" else "Aktifkan mode gelap"
                                )
                            }

                            FilledTonalIconButton(
                                onClick = shareReport,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("share_report_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureAsPdf,
                                    contentDescription = "Ekspor PDF & Bagikan Laporan"
                                )
                            }
                        }
                    }

                    // Expandable Search Field
                    AnimatedVisibility(
                        visible = showSearchField && currentNavTab == 0,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { q ->
                                transactionViewModel?.setSearchQuery(q) ?: viewModel.setSearchQuery(q)
                            },
                            placeholder = {
                                Text(
                                    "Cari catatan, nama siswa, atau kategori...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            transactionViewModel?.setSearchQuery("") ?: viewModel.setSearchQuery("")
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = "Hapus pencarian"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .testTag("search_bar_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                windowInsets = NavigationBarDefaults.windowInsets
            ) {
                val navItemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                NavigationBarItem(
                    selected = currentNavTab == 0,
                    onClick = { currentNavTab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (currentNavTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = "Beranda"
                        )
                    },
                    label = {
                        Text(
                            "Beranda",
                            fontWeight = if (currentNavTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = navItemColors,
                    modifier = Modifier.testTag("nav_tab_home")
                )

                NavigationBarItem(
                    selected = currentNavTab == 1,
                    onClick = { currentNavTab = 1 },
                    icon = {
                        Icon(
                            imageVector = if (currentNavTab == 1) Icons.Filled.Leaderboard else Icons.Outlined.Leaderboard,
                            contentDescription = "Laporan"
                        )
                    },
                    label = {
                        Text(
                            "Laporan",
                            fontWeight = if (currentNavTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = navItemColors,
                    modifier = Modifier.testTag("nav_tab_report")
                )

                val unpaidCount = unpaidSummary.unpaidMembers.size
                NavigationBarItem(
                    selected = currentNavTab == 2,
                    onClick = { currentNavTab = 2 },
                    icon = {
                            if (unpaidCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text("$unpaidCount")
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (currentNavTab == 2) Icons.Filled.Group else Icons.Outlined.Group,
                                        contentDescription = "Anggota"
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (currentNavTab == 2) Icons.Filled.Group else Icons.Outlined.Group,
                                    contentDescription = "Anggota"
                                )
                            }
                        },
                        label = {
                            Text(
                                "Anggota",
                                fontWeight = if (currentNavTab == 2) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                    colors = navItemColors,
                    modifier = Modifier.testTag("nav_tab_reminder")
                )

                NavigationBarItem(
                    selected = currentNavTab == 3,
                    onClick = { currentNavTab = 3 },
                    icon = {
                        Icon(
                            imageVector = if (currentNavTab == 3) Icons.Filled.CloudDone else Icons.Outlined.CloudUpload,
                            contentDescription = "Backup Drive"
                        )
                    },
                    label = {
                        Text(
                            "Backup",
                            fontWeight = if (currentNavTab == 3) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = navItemColors,
                    modifier = Modifier.testTag("nav_tab_backup")
                )
            }
        },
        floatingActionButton = {
            if (currentNavTab < 2) {
                ExtendedFloatingActionButton(
                    onClick = {
                        transactionToEdit = null
                        showAddSheet = true
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Catat Kas"
                        )
                    },
                    text = {
                        Text(
                            text = "Catat Kas",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("add_transaction_fab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (currentNavTab) {
                1 -> {
                    KasReportScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentNavTab = 0 },
                        onSelectTransaction = { selectedTransactionForDetail = it },
                        onShareReport = shareReport,
                        modifier = Modifier.widthIn(max = 680.dp)
                    )
                }

                2 -> {
                    KasReminderScreen(
                        viewModel = viewModel,
                        onShowMessage = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                        onCopyReminderText = { text ->
                            copyToClipboard(text, "Pesan pengingat iuran berhasil disalin!")
                        },
                        onShareReminderText = { text ->
                            launchExternalShare("Kirim Pengingat Iuran Kas", text)
                        },
                        modifier = Modifier.widthIn(max = 680.dp)
                    )
                }

                3 -> {
                    KasBackupScreen(
                        viewModel = viewModel,
                        onShowMessage = { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
                        modifier = Modifier.widthIn(max = 680.dp)
                    )
                }

                else -> {
                    if (transactionViewModel != null) {
                        MainDashboardScreen(
                            transactionViewModel = transactionViewModel,
                            monthlyTrendPoints = analyticsData.monthlyTrendPoints,
                            onQuickIuranClick = { showQuickIuranSheet = true },
                            onOpenReportsClick = { currentNavTab = 1 },
                            onExportPdfClick = shareReport,
                            onAddTransactionClick = {
                                transactionToEdit = null
                                showAddSheet = true
                            },
                            onTransactionClick = { selectedTransactionForDetail = it }
                        )
                    } else {
                        MainDashboardContent(
                            summary = summary,
                            transactions = transactions,
                            currentFilterType = currentFilterType,
                            selectedCategoryFilter = selectedCategoryFilter,
                            hasActiveFilter = hasActiveFilter,
                            monthlyTrendPoints = analyticsData.monthlyTrendPoints,
                            onFilterTypeChange = { viewModel.setFilterType(it) },
                            onCategoryFilterChange = { viewModel.setCategoryFilter(it) },
                            onResetFilters = { viewModel.resetFilters() },
                            onQuickIuranClick = { showQuickIuranSheet = true },
                            onOpenReportsClick = { currentNavTab = 1 },
                            onExportPdfClick = shareReport,
                            onAddTransactionClick = {
                                transactionToEdit = null
                                showAddSheet = true
                            },
                            onTransactionClick = { selectedTransactionForDetail = it }
                        )
                    }
                }
            }
        }
    }

    // Modal BottomSheet for Add / Edit
    if (showAddSheet || transactionToEdit != null) {
        AddEditTransactionSheet(
            sheetState = addSheetState,
            initialTransaction = transactionToEdit,
            onDismiss = {
                showAddSheet = false
                transactionToEdit = null
            },
            onSave = { type, title, amount, category, dateMillis, personName, notes ->
                if (transactionToEdit != null) {
                    val updated = transactionToEdit!!.copy(
                        type = type.name,
                        title = title,
                        amount = amount,
                        category = category,
                        dateMillis = dateMillis,
                        personName = personName,
                        notes = notes
                    )
                    transactionViewModel?.updateTransaction(updated) ?: viewModel.updateTransaction(updated)
                    scope.launch {
                        snackbarHostState.showSnackbar("Transaksi berhasil diperbarui")
                    }
                } else {
                    if (transactionViewModel != null) {
                        transactionViewModel.addTransaction(
                            type = type,
                            title = title,
                            amount = amount,
                            category = category,
                            dateMillis = dateMillis,
                            personName = personName,
                            notes = notes
                        )
                    } else {
                        viewModel.addTransaction(
                            type = type,
                            title = title,
                            amount = amount,
                            category = category,
                            dateMillis = dateMillis,
                            personName = personName,
                            notes = notes
                        )
                    }
                    scope.launch {
                        snackbarHostState.showSnackbar("Transaksi berhasil dicatat!")
                    }
                }
                showAddSheet = false
                transactionToEdit = null
            }
        )
    }

    // Quick Iuran Sheet (synced with shared studentRoster)
    if (showQuickIuranSheet) {
        QuickIuranSheet(
            sheetState = quickIuranSheetState,
            externalRoster = studentRoster,
            pastPayerNames = historicalPayerNames,
            onRosterAdded = { raw -> viewModel.addStudentsToRoster(raw) },
            onRosterRemoved = { name -> viewModel.removeStudentFromRoster(name) },
            onDismiss = { showQuickIuranSheet = false },
            onSubmitBatch = { amount, students, weekTitle, notes ->
                viewModel.recordQuickBatchIuran(
                    amountPerStudent = amount,
                    studentNames = students,
                    weekTitle = weekTitle,
                    notes = notes
                )
                showQuickIuranSheet = false
                scope.launch {
                    snackbarHostState.showSnackbar("${students.size} iuran siswa berhasil dicatat!")
                }
            }
        )
    }

    // Share & PDF Export/Print Sheet
    if (showShareSheet) {
        val reportText = remember(allTransactions, summary) {
            viewModel.generateReportText()
        }
        ShareReportSheet(
            sheetState = shareSheetState,
            reportText = reportText,
            paidPayers = scopedPdfPaidPayers,
            isExportingPdf = isExportingPdf,
            selectedScope = effectivePdfScope,
            hasActiveFilter = hasActiveFilter,
            pdfTransactionCount = scopedPdfTransactions.size,
            onScopeChange = { selectedPdfScope = it },
            onDismiss = { showShareSheet = false },
            onCopyReport = {
                copyToClipboard(reportText, "Laporan & daftar pembayar berhasil disalin!")
                showShareSheet = false
            },
            onShareExternal = {
                launchExternalShare("Bagikan Laporan Kas Kelas", reportText)
                showShareSheet = false
            },
            onPrintPdf = printPdfReport,
            onSharePdf = sharePdfReport,
            onSavePdf = {
                savePdfLauncher.launch(KasPdfExporter.defaultPdfFileName())
            }
        )
    }

    // Transaction Detail Dialog
    if (selectedTransactionForDetail != null) {
        TransactionDetailDialog(
            transaction = selectedTransactionForDetail!!,
            onDismiss = { selectedTransactionForDetail = null },
            onEdit = {
                transactionToEdit = selectedTransactionForDetail
                selectedTransactionForDetail = null
            },
            onDelete = {
                val toDelete = selectedTransactionForDetail
                if (toDelete != null) {
                    transactionViewModel?.deleteTransaction(toDelete) ?: viewModel.deleteTransaction(toDelete)
                    scope.launch {
                        snackbarHostState.showSnackbar("Transaksi berhasil dihapus")
                    }
                }
                selectedTransactionForDetail = null
            }
        )
    }
}
