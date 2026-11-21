package com.example.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.KasCategory
import com.example.data.TransactionEntity
import com.example.data.formatRupiah
import com.example.ui.components.CategoryComparisonBars
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.MonthlyBalanceTrendChart
import com.example.ui.components.MonthlyCashflowBarChart
import com.example.ui.components.TransactionItemCard
import com.example.ui.components.TrendComparisonChart
import com.example.ui.theme.GeoBrandBlue
import com.example.ui.theme.GeoNavy
import com.example.ui.theme.GeoSoftBlueContainer
import com.example.ui.theme.LocalKasIsDark
import com.example.ui.theme.kasExpenseColor
import com.example.ui.theme.kasExpenseContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KasReportScreen(
    viewModel: KasViewModel,
    onNavigateBack: () -> Unit,
    onSelectTransaction: (TransactionEntity) -> Unit,
    onShareReport: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val analytics by viewModel.analyticsData.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()

    var selectedChartTab by remember { mutableIntStateOf(0) } // 0: Tren Saldo, 1: Pengeluaran, 2: Pemasukan
    var selectedCategoryDrilldown by remember { mutableStateOf<String?>(null) }

    val todayDateStr = remember {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("id-ID"))
        sdf.format(Date())
    }

    val matchingDrilldownTransactions = remember(selectedCategoryDrilldown, allTransactions) {
        selectedCategoryDrilldown?.let { cat ->
            val targetCat = KasCategory.fromString(cat)
            allTransactions.filter {
                it.category.equals(cat, ignoreCase = true) ||
                    KasCategory.fromString(it.category) == targetCat
            }
        } ?: emptyList()
    }

    val expenseColor = kasExpenseColor()
    val expenseBg = kasExpenseContainer()
    val heroBg = if (LocalKasIsDark.current) Color(0xFF0E233B) else GeoNavy

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("kas_report_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Report Screen Hero Card with Dominant Blue Palette
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_hero_card"),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = heroBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(GeoBrandBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Leaderboard,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Laporan & Visualisasi Kas",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Analisis tren keuangan per $todayDateStr",
                                fontSize = 11.sp,
                                color = GeoSoftBlueContainer.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Share Action Button
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (onShareReport != null) {
                                        onShareReport()
                                    } else {
                                        val report = viewModel.generateReportText()
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, report)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Bagikan Laporan Kas"))
                                    }
                                },
                            color = GeoBrandBlue.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Bagikan",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Bagikan",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3-Way Summary Metrics Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            label = "TOTAL SALDO",
                            value = formatRupiah(summary.balance),
                            accentColor = Color(0xFFA0CAFD),
                            modifier = Modifier.weight(1f)
                        )

                        MetricTile(
                            label = "PEMASUKAN",
                            value = "+${formatRupiah(summary.totalIncome)}",
                            accentColor = Color(0xFF86EFAC),
                            modifier = Modifier.weight(1f)
                        )

                        MetricTile(
                            label = "PENGELUARAN",
                            value = "-${formatRupiah(summary.totalExpense)}",
                            accentColor = Color(0xFFFCA5A5),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Financial Health & Category Highlights Banner
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Dominant Expense Category Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(expenseBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = expenseColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Beban Terbesar",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = analytics.highestExpenseCategory?.displayName ?: "Belum ada",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = analytics.highestExpenseCategory?.let { formatRupiah(it.totalAmount) } ?: "Rp 0",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = expenseColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Ratio & Transaction Stats Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Aktivitas Kas",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${summary.totalCount} Total Catatan",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Rata2 ${formatRupiah(analytics.averageTransactionAmount)}/tx",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 3. Tab Selector for Chart Visualizations (Compact non-wrapping tabs)
        item {
            PrimaryTabRow(
                selectedTabIndex = selectedChartTab,
                containerColor = MaterialTheme.colorScheme.surface,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(selectedChartTab, matchContentSize = false),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("report_chart_tabs")
            ) {
                Tab(
                    selected = selectedChartTab == 0,
                    onClick = { selectedChartTab = 0 },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tren Saldo",
                                fontWeight = if (selectedChartTab == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedChartTab == 1,
                    onClick = { selectedChartTab = 1 },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Pengeluaran",
                                fontWeight = if (selectedChartTab == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedChartTab == 2,
                    onClick = { selectedChartTab = 2 },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Pemasukan",
                                fontWeight = if (selectedChartTab == 2) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
            }
        }

        // 4. Primary Chart Section based on selected tab
        item {
            when (selectedChartTab) {
                0 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Simple Compose Monthly Income vs Expense Bar Chart
                        MonthlyCashflowBarChart(monthlyPoints = analytics.monthlyTrendPoints)
                        // Monthly Balance Trend Visualization
                        MonthlyBalanceTrendChart(monthlyPoints = analytics.monthlyTrendPoints)
                        // Daily Cashflow Trend Chart
                        TrendComparisonChart(points = analytics.trendPoints)
                    }
                }
                1 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryDonutChart(
                            categoryStats = analytics.expenseCategoryStats,
                            title = "Distribusi Pengeluaran Berdasarkan Kategori",
                            onCategoryClick = { selectedCategoryDrilldown = it }
                        )
                        CategoryComparisonBars(
                            categoryStats = analytics.expenseCategoryStats,
                            isIncome = false,
                            onCategoryClick = { selectedCategoryDrilldown = it }
                        )
                    }
                }
                2 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryDonutChart(
                            categoryStats = analytics.incomeCategoryStats,
                            title = "Distribusi Pemasukan Berdasarkan Kategori",
                            onCategoryClick = { selectedCategoryDrilldown = it }
                        )
                        CategoryComparisonBars(
                            categoryStats = analytics.incomeCategoryStats,
                            isIncome = true,
                            onCategoryClick = { selectedCategoryDrilldown = it }
                        )
                    }
                }
            }
        }

        // 5. Category Drilldown Detail Section
        if (selectedCategoryDrilldown != null) {
            val drillCategory = selectedCategoryDrilldown!!

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detail: ${KasCategory.fromString(drillCategory).displayName}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedCategoryDrilldown = null },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Tutup Filter",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (matchingDrilldownTransactions.isEmpty()) {
                item {
                    Text(
                        text = "Tidak ada transaksi dalam kategori ini",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
            } else {
                items(matchingDrilldownTransactions, key = { it.id }) { tx ->
                    TransactionItemCard(
                        transaction = tx,
                        onClick = { onSelectTransaction(tx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
