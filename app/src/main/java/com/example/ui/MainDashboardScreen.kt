package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.KasCategory
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.ui.components.MonthlyCashflowBarChart
import com.example.ui.components.RealtimeBalanceCard
import com.example.ui.components.TransactionItemCard
import com.example.ui.components.getCategoryIcon

/**
 * Main Dashboard UI driven directly by [TransactionViewModel].
 * Displays the current total balance hero card at the top and a scrollable
 * list of recent transactions below, styled with a modern blue theme.
 */
@Composable
fun MainDashboardScreen(
    transactionViewModel: TransactionViewModel,
    monthlyTrendPoints: List<MonthlyTrendPoint> = emptyList(),
    showQuickActionsAndChart: Boolean = true,
    onQuickIuranClick: () -> Unit = {},
    onOpenReportsClick: () -> Unit = {},
    onExportPdfClick: () -> Unit = {},
    onAddTransactionClick: () -> Unit = {},
    onTransactionClick: (TransactionEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val summary by transactionViewModel.summary.collectAsStateWithLifecycle()
    val totalBalance by transactionViewModel.totalBalance.collectAsStateWithLifecycle()
    val transactions by transactionViewModel.filteredTransactions.collectAsStateWithLifecycle()
    val currentFilterType by transactionViewModel.selectedFilterType.collectAsStateWithLifecycle()
    val searchQuery by transactionViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategoryFilter by transactionViewModel.selectedCategoryFilter.collectAsStateWithLifecycle()

    val hasActiveFilter = searchQuery.isNotBlank() ||
        currentFilterType != FilterType.ALL ||
        selectedCategoryFilter != null

    MainDashboardContent(
        summary = summary.copy(balance = totalBalance),
        transactions = transactions,
        currentFilterType = currentFilterType,
        selectedCategoryFilter = selectedCategoryFilter,
        hasActiveFilter = hasActiveFilter,
        monthlyTrendPoints = monthlyTrendPoints,
        showQuickActionsAndChart = showQuickActionsAndChart,
        onFilterTypeChange = { transactionViewModel.setFilterType(it) },
        onCategoryFilterChange = { transactionViewModel.setCategoryFilter(it) },
        onResetFilters = { transactionViewModel.resetFilters() },
        onQuickIuranClick = onQuickIuranClick,
        onOpenReportsClick = onOpenReportsClick,
        onExportPdfClick = onExportPdfClick,
        onAddTransactionClick = onAddTransactionClick,
        onTransactionClick = onTransactionClick,
        modifier = modifier
    )
}

@Composable
fun MainDashboardContent(
    summary: KasSummary,
    transactions: List<TransactionEntity>,
    currentFilterType: FilterType,
    selectedCategoryFilter: String?,
    hasActiveFilter: Boolean,
    monthlyTrendPoints: List<MonthlyTrendPoint>,
    showQuickActionsAndChart: Boolean = true,
    onFilterTypeChange: (FilterType) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onResetFilters: () -> Unit,
    onQuickIuranClick: () -> Unit,
    onOpenReportsClick: () -> Unit,
    onExportPdfClick: () -> Unit,
    onAddTransactionClick: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 680.dp)
            .testTag("home_transactions_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Prominent Modern Blue Total Balance Card at the top
        item {
            RealtimeBalanceCard(summary = summary)
        }

        if (showQuickActionsAndChart) {
            // 2. Quick Action Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedCard(
                        onClick = onQuickIuranClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_iuran_button"),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Group,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Iuran Siswa",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Catat cepat masal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    OutlinedCard(
                        onClick = onOpenReportsClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_stats_button"),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Leaderboard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Laporan & Grafik",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Tren saldo bulanan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Recent Transactions Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Transaksi Terkini",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${transactions.size} catatan ditampilkan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = onExportPdfClick,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "Ekspor PDF",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 4. Material 3 Segmented Button Filter
        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transaction_type_segmented_filter")
            ) {
                FilterType.entries.forEachIndexed { index, filter ->
                    val selected = currentFilterType == filter
                    SegmentedButton(
                        selected = selected,
                        onClick = { onFilterTypeChange(filter) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = FilterType.entries.size
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Text(
                            text = filter.label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 5. Category Filter Chips
        item {
            val availableCategories = when (currentFilterType) {
                FilterType.ALL -> KasCategory.entries
                FilterType.INCOME -> KasCategory.entries.filter { it.defaultType == TransactionType.PEMASUKAN }
                FilterType.EXPENSE -> KasCategory.entries.filter { it.defaultType == TransactionType.PENGELUARAN }
            }

            LazyRow(
                modifier = Modifier.testTag("category_filter_chips_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryFilter == null,
                        onClick = { onCategoryFilterChange(null) },
                        label = { Text("Semua Kategori", fontSize = 12.sp, maxLines = 1) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                items(availableCategories) { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat.name,
                        onClick = {
                            if (selectedCategoryFilter == cat.name) {
                                onCategoryFilterChange(null)
                            } else {
                                onCategoryFilterChange(cat.name)
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = getCategoryIcon(cat.name),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(cat.displayName, fontSize = 12.sp, maxLines = 1) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        // 6. Scrollable Recent Transactions List or Empty State
        if (transactions.isEmpty()) {
            item {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .testTag("empty_transactions_card"),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (hasActiveFilter) "Tidak ada transaksi yang cocok"
                            else "Belum ada transaksi tercatat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (hasActiveFilter) "Coba ubah filter kategori, jenis transaksi, atau kata kunci pencarian."
                            else "Tekan tombol 'Catat Kas' di bawah untuk mulai mencatat pemasukan atau pengeluaran kelas.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (hasActiveFilter) {
                            Button(
                                onClick = onResetFilters,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Clear, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset Semua Filter")
                            }
                        } else {
                            Button(
                                onClick = onAddTransactionClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tambah Transaksi Pertama")
                            }
                        }
                    }
                }
            }
        } else {
            items(
                items = transactions,
                key = { it.id }
            ) { tx ->
                TransactionItemCard(
                    transaction = tx,
                    onClick = { onTransactionClick(tx) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        if (showQuickActionsAndChart && monthlyTrendPoints.isNotEmpty()) {
            // 7. Monthly Cashflow Summary Chart
            item {
                MonthlyCashflowBarChart(
                    monthlyPoints = monthlyTrendPoints,
                    onOpenDetailReport = onOpenReportsClick
                )
            }
        }
    }
}
