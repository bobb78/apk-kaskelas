package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.formatRupiah
import com.example.data.formatRupiahCompact
import com.example.ui.MonthlyTrendPoint
import com.example.ui.theme.kasExpenseColor
import com.example.ui.theme.kasExpenseContainer
import com.example.ui.theme.kasIncomeColor
import com.example.ui.theme.kasIncomeContainer
import kotlin.math.abs

/**
 * Simple Jetpack Compose bar chart summarizing monthly income & expenses
 * using pure Compose layout primitives (Row, Column, Box, Surface).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyCashflowBarChart(
    monthlyPoints: List<MonthlyTrendPoint>,
    modifier: Modifier = Modifier,
    onOpenDetailReport: (() -> Unit)? = null
) {
    val incomeColor = kasIncomeColor()
    val incomeBg = kasIncomeContainer()
    val expenseColor = kasExpenseColor()
    val expenseBg = kasExpenseContainer()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_cashflow_chart"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Ringkasan Arus Kas Bulanan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Perbandingan pemasukan & pengeluaran tiap bulan",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (onOpenDetailReport != null) {
                    Surface(
                        onClick = onOpenDetailReport,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("cashflow_detail_button")
                    ) {
                        Text(
                            text = "Detail",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CashflowLegendPill(color = incomeColor, label = "Pemasukan")
                CashflowLegendPill(color = expenseColor, label = "Pengeluaran")
            }

            if (monthlyPoints.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            RoundedCornerShape(14.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada transaksi bulanan untuk ditampilkan.\nCatat pemasukan atau pengeluaran untuk melihat grafik.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                return@Column
            }

            var selectedIndex by remember(monthlyPoints.size) {
                mutableIntStateOf(monthlyPoints.lastIndex.coerceAtLeast(0))
            }
            val safeIdx = selectedIndex.coerceIn(0, monthlyPoints.lastIndex)
            val animProgress = remember { Animatable(0f) }

            LaunchedEffect(monthlyPoints) {
                selectedIndex = monthlyPoints.lastIndex.coerceAtLeast(0)
                animProgress.snapTo(0f)
                animProgress.animateTo(1f, animationSpec = tween(600))
            }

            Spacer(modifier = Modifier.height(10.dp))

            val selectedPoint = monthlyPoints[safeIdx]
            val net = selectedPoint.netChange
            val isSurplus = net >= 0L

            // Selected Month Cashflow Summary Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cashflow_selected_summary")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedPoint.fullLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSurplus) incomeBg else expenseBg
                        ) {
                            Text(
                                text = if (isSurplus) {
                                    "Surplus +${formatRupiahCompact(net)}"
                                } else {
                                    "Defisit -${formatRupiahCompact(abs(net))}"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSurplus) incomeColor else expenseColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CashflowStatBox(
                            label = "Pemasukan",
                            value = "+${formatRupiah(selectedPoint.income)}",
                            valueColor = incomeColor,
                            modifier = Modifier.weight(1f)
                        )
                        CashflowStatBox(
                            label = "Pengeluaran",
                            value = "-${formatRupiah(selectedPoint.expense)}",
                            valueColor = expenseColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val maxBarValue = remember(monthlyPoints) {
                monthlyPoints.maxOfOrNull { maxOf(it.income, it.expense) }?.coerceAtLeast(1L) ?: 1L
            }
            val maxBarHeightDp = 112.dp

            // Pure Compose Bar Columns
            val scrollState = rememberScrollState()
            LaunchedEffect(monthlyPoints.size) {
                if (scrollState.maxValue > 0) {
                    scrollState.scrollTo(scrollState.maxValue)
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = if (monthlyPoints.size <= 4) {
                        Arrangement.SpaceEvenly
                    } else {
                        Arrangement.spacedBy(10.dp)
                    },
                    verticalAlignment = Alignment.Bottom
                ) {
                    monthlyPoints.forEachIndexed { idx, point ->
                        val isSelected = idx == safeIdx
                        val incomeRatio = (point.income.toFloat() / maxBarValue).coerceIn(0f, 1f)
                        val expenseRatio = (point.expense.toFloat() / maxBarValue).coerceIn(0f, 1f)

                        val incomeHeight = if (point.income > 0L) {
                            (maxBarHeightDp * incomeRatio * animProgress.value).coerceAtLeast(6.dp)
                        } else {
                            2.dp
                        }
                        val expenseHeight = if (point.expense > 0L) {
                            (maxBarHeightDp * expenseRatio * animProgress.value).coerceAtLeast(6.dp)
                        } else {
                            2.dp
                        }

                        Column(
                            modifier = Modifier
                                .widthIn(min = 66.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else Color.Transparent
                                )
                                .clickable { selectedIndex = idx }
                                .minimumInteractiveComponentSize()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("cashflow_month_bar_${point.monthKey}"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.height(maxBarHeightDp + 18.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (point.income > 0L) {
                                        Text(
                                            text = formatRupiahCompact(point.income).replace("Rp ", ""),
                                            fontSize = 9.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = incomeColor,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height(incomeHeight)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(
                                                if (point.income > 0L) {
                                                    if (isSelected) incomeColor else incomeColor.copy(alpha = 0.72f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                }
                                            )
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (point.expense > 0L) {
                                        Text(
                                            text = formatRupiahCompact(point.expense).replace("Rp ", ""),
                                            fontSize = 9.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = expenseColor,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height(expenseHeight)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(
                                                if (point.expense > 0L) {
                                                    if (isSelected) expenseColor else expenseColor.copy(alpha = 0.72f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                }
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ) {
                                Text(
                                    text = point.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Ketuk kolom bulan untuk melihat rincian arus kas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CashflowLegendPill(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CashflowStatBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
