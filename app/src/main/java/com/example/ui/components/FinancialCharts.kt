package com.example.ui.components

import android.graphics.Paint as NativePaint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.formatRupiah
import com.example.data.formatRupiahCompact
import com.example.ui.CategoryStat
import com.example.ui.MonthlyTrendPoint
import com.example.ui.TimeTrendPoint
import com.example.ui.theme.CategoryPalette
import com.example.ui.theme.kasExpenseColor
import com.example.ui.theme.kasIncomeColor
import kotlin.math.abs

/**
 * Interactive Monthly Balance Trend Visualization (Area Curve + Monthly Income/Expense Bars + Tooltip)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyBalanceTrendChart(
    monthlyPoints: List<MonthlyTrendPoint>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val incomeColor = kasIncomeColor()
    val expenseColor = kasExpenseColor()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monthly_balance_trend_chart"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Grafik Tren Saldo Bulanan",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Perkembangan saldo akhir, pemasukan & pengeluaran per bulan",
                fontSize = 11.sp,
                color = subtitleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChartLegendItem(color = primaryColor, label = "Saldo Akhir")
                ChartLegendItem(color = incomeColor.copy(alpha = 0.8f), label = "Pemasukan")
                ChartLegendItem(color = expenseColor.copy(alpha = 0.8f), label = "Pengeluaran")
            }

            if (monthlyPoints.isEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada data transaksi untuk grafik bulanan",
                        fontSize = 12.sp,
                        color = subtitleColor
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
                animProgress.animateTo(1f, animationSpec = tween(750))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selected Month Interactive Tooltip Card
            val pt = monthlyPoints[safeIdx]
            val prevBalance = if (safeIdx > 0) monthlyPoints[safeIdx - 1].endingBalance else 0L
            val delta = pt.endingBalance - prevBalance

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monthly_trend_tooltip")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pt.fullLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = surfaceColor.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${pt.transactionCount} transaksi",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TooltipMetricBadge(
                            label = "Saldo Akhir",
                            value = formatRupiah(pt.endingBalance),
                            valueColor = primaryColor,
                            modifier = Modifier.weight(1f)
                        )
                        TooltipMetricBadge(
                            label = "Masuk",
                            value = "+${formatRupiahCompact(pt.income)}",
                            valueColor = incomeColor,
                            modifier = Modifier.weight(1f)
                        )
                        TooltipMetricBadge(
                            label = "Keluar",
                            value = "-${formatRupiahCompact(pt.expense)}",
                            valueColor = expenseColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (safeIdx > 0) {
                        val deltaSign = if (delta >= 0) "+" else "-"
                        val deltaColor = if (delta >= 0) incomeColor else expenseColor
                        Text(
                            text = "Perubahan vs bulan sebelumnya: $deltaSign${formatRupiah(abs(delta))}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = deltaColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val minVal = remember(monthlyPoints) {
                minOf(0L, monthlyPoints.minOf { it.endingBalance })
            }
            val maxVal = remember(monthlyPoints) {
                val rawMax = monthlyPoints.maxOf { maxOf(it.endingBalance, it.income, it.expense) }
                if (rawMax <= 0L) 100_000L else (rawMax * 1.18).toLong()
            }
            val range = (maxVal - minVal).coerceAtLeast(1L).toFloat()

            // Canvas Chart with Y-Axis Ticks, Bars, Smooth Area Curve & Tap Gesture
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(155.dp)
            ) {
                val axisTextColor = subtitleColor.toArgb()
                val axisPaint = remember(axisTextColor) {
                    NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                        color = axisTextColor
                        textSize = 26f
                        textAlign = NativePaint.Align.RIGHT
                    }
                }

                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .testTag("monthly_balance_canvas")
                        .pointerInput(monthlyPoints) {
                            detectTapGestures { offset ->
                                val leftGutter = 56.dp.toPx()
                                val plotW = (size.width - leftGutter - 12.dp.toPx()).coerceAtLeast(1f)
                                val count = monthlyPoints.size
                                if (count == 1) {
                                    selectedIndex = 0
                                } else if (count > 1) {
                                    val relX = (offset.x - leftGutter).coerceIn(0f, plotW)
                                    val slotW = plotW / count
                                    selectedIndex = (relX / slotW).toInt().coerceIn(0, count - 1)
                                }
                            }
                        }
                ) {
                    val leftPadding = 56.dp.toPx()
                    val rightPadding = 12.dp.toPx()
                    val topPadding = 12.dp.toPx()
                    val bottomPadding = 24.dp.toPx()
                    val plotW = (size.width - leftPadding - rightPadding).coerceAtLeast(1f)
                    val plotH = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)

                    fun valueToY(value: Long): Float {
                        val norm = ((value - minVal).toFloat() / range).coerceIn(0f, 1f)
                        return topPadding + plotH * (1f - norm)
                    }

                    // Horizontal Grid Lines + Y-Axis Labels
                    val steps = 3
                    for (i in 0..steps) {
                        val ratio = i.toFloat() / steps
                        val y = topPadding + plotH * ratio
                        val tickValue = (maxVal - ratio * range).toLong()

                        drawLine(
                            color = borderColor.copy(alpha = 0.7f),
                            start = Offset(leftPadding, y),
                            end = Offset(size.width - rightPadding, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            formatRupiahCompact(tickValue),
                            leftPadding - 6.dp.toPx(),
                            y + 4.dp.toPx(),
                            axisPaint
                        )
                    }

                    val count = monthlyPoints.size
                    val slotW = plotW / count.coerceAtLeast(1)
                    val zeroY = valueToY(0L)

                    // Draw Monthly Income & Expense Bars
                    val barWidth = (slotW * 0.22f).coerceIn(6.dp.toPx(), 16.dp.toPx())
                    val barGap = 3.dp.toPx()

                    val nodeOffsets = mutableListOf<Offset>()

                    monthlyPoints.forEachIndexed { idx, monthPt ->
                        val centerX = leftPadding + slotW * (idx + 0.5f)
                        val incTopY = valueToY((monthPt.income * animProgress.value).toLong())
                        val expTopY = valueToY((monthPt.expense * animProgress.value).toLong())

                        // Income Bar
                        val incH = (zeroY - incTopY).coerceAtLeast(0f)
                        if (incH > 0f) {
                            drawRoundRect(
                                color = incomeColor.copy(alpha = 0.34f),
                                topLeft = Offset(centerX - barWidth - barGap / 2f, incTopY),
                                size = Size(barWidth, incH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }

                        // Expense Bar
                        val expH = (zeroY - expTopY).coerceAtLeast(0f)
                        if (expH > 0f) {
                            drawRoundRect(
                                color = expenseColor.copy(alpha = 0.32f),
                                topLeft = Offset(centerX + barGap / 2f, expTopY),
                                size = Size(barWidth, expH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }

                        val animatedBalance = (monthPt.endingBalance * animProgress.value).toLong()
                        nodeOffsets.add(Offset(centerX, valueToY(animatedBalance)))
                    }

                    // Draw Smooth Cubic Bezier Balance Trend Line + Area Fill
                    if (nodeOffsets.size >= 2) {
                        val linePath = Path().apply {
                            moveTo(nodeOffsets[0].x, nodeOffsets[0].y)
                            for (i in 1 until nodeOffsets.size) {
                                val prev = nodeOffsets[i - 1]
                                val curr = nodeOffsets[i]
                                val ctrlX = (prev.x + curr.x) / 2f
                                cubicTo(ctrlX, prev.y, ctrlX, curr.y, curr.x, curr.y)
                            }
                        }
                        val areaPath = Path().apply {
                            addPath(linePath)
                            lineTo(nodeOffsets.last().x, zeroY)
                            lineTo(nodeOffsets.first().x, zeroY)
                            close()
                        }

                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.24f),
                                    primaryColor.copy(alpha = 0.02f)
                                ),
                                startY = topPadding,
                                endY = zeroY
                            )
                        )

                        drawPath(
                            path = linePath,
                            color = primaryColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Selected Vertical Guide + Data Point Dots
                    if (safeIdx in nodeOffsets.indices) {
                        val selOffset = nodeOffsets[safeIdx]
                        drawLine(
                            color = primaryColor.copy(alpha = 0.45f),
                            start = Offset(selOffset.x, topPadding),
                            end = Offset(selOffset.x, topPadding + plotH),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }

                    nodeOffsets.forEachIndexed { idx, offset ->
                        val isSelected = idx == safeIdx
                        if (isSelected) {
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.22f),
                                radius = 9.dp.toPx(),
                                center = offset
                            )
                        }
                        drawCircle(
                            color = surfaceColor,
                            radius = if (isSelected) 5.5.dp.toPx() else 4.dp.toPx(),
                            center = offset
                        )
                        drawCircle(
                            color = primaryColor,
                            radius = if (isSelected) 3.8.dp.toPx() else 2.6.dp.toPx(),
                            center = offset
                        )
                    }
                }
            }

            // Clickable Month Selector Pills along X-Axis (Zero overlap)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                monthlyPoints.forEachIndexed { idx, monthPt ->
                    val isSelected = idx == safeIdx
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedIndex = idx }
                            .testTag("month_pill_${monthPt.monthKey}")
                    ) {
                        Text(
                            text = monthPt.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else subtitleColor,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TooltipMetricBadge(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontSize = 11.sp,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Interactive Dual Trend Line/Bar Chart showing Daily Income, Expense & Balance progression
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrendComparisonChart(
    points: List<TimeTrendPoint>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val incomeColor = kasIncomeColor()
    val expenseColor = kasExpenseColor()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(surfaceColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Belum cukup data transaksi untuk tren harian",
                fontSize = 13.sp,
                color = subtitleColor
            )
        }
        return
    }

    var selectedIndex by remember(points.size) { mutableIntStateOf(points.lastIndex) }
    val safeIdx = selectedIndex.coerceIn(0, points.lastIndex)
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(points) {
        selectedIndex = points.lastIndex
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(700))
    }

    val maxVal = remember(points) {
        val maxPointVal = points.maxOfOrNull { maxOf(it.income, it.expense, it.balance) } ?: 100000L
        if (maxPointVal <= 0L) 100000L else (maxPointVal * 1.15).toLong()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trend_comparison_chart"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Tren Harian Arus Kas",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Pemasukan, pengeluaran & akumulasi saldo per hari",
                fontSize = 11.sp,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ChartLegendItem(color = incomeColor, label = "Masuk")
                ChartLegendItem(color = expenseColor, label = "Keluar")
                ChartLegendItem(color = primaryColor, label = "Saldo")
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selected Data Tooltip Info Pill (No clipping on narrow screens)
            val pt = points[safeIdx]
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = pt.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "+${formatRupiahCompact(pt.income)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = incomeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "-${formatRupiahCompact(pt.expense)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = expenseColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Saldo: ${formatRupiahCompact(pt.balance)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val count = points.size
                                if (count > 1) {
                                    val stepX = size.width / (count - 1).toFloat()
                                    val idx = ((offset.x + stepX / 2f) / stepX).toInt().coerceIn(0, count - 1)
                                    selectedIndex = idx
                                } else {
                                    selectedIndex = 0
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val topPadding = 10.dp.toPx()
                    val paddingBottom = 16.dp.toPx()
                    val chartH = (h - topPadding - paddingBottom).coerceAtLeast(1f)
                    val count = points.size
                    val stepX = if (count > 1) w / (count - 1) else w / 2f

                    fun valToY(v: Long): Float {
                        val norm = ((v.toFloat() / maxVal) * animProgress.value).coerceIn(0f, 1f)
                        return topPadding + chartH * (1f - norm)
                    }

                    // Draw horizontal grid lines
                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val y = topPadding + chartH * (i.toFloat() / gridSteps)
                        drawLine(
                            color = borderColor.copy(alpha = 0.6f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }

                    if (count == 1) {
                        val single = points[0]
                        val x = w / 2f
                        drawCircle(color = incomeColor, radius = 6.dp.toPx(), center = Offset(x - 28.dp.toPx(), valToY(single.income)))
                        drawCircle(color = expenseColor, radius = 6.dp.toPx(), center = Offset(x, valToY(single.expense)))
                        drawCircle(color = primaryColor, radius = 7.dp.toPx(), center = Offset(x + 28.dp.toPx(), valToY(single.balance)))
                        return@Canvas
                    }

                    // Build Paths for Income & Expense and Balance
                    val incomePath = Path()
                    val expensePath = Path()
                    val balancePath = Path()
                    val balanceAreaPath = Path()
                    val baselineY = topPadding + chartH

                    points.forEachIndexed { i, point ->
                        val x = i * stepX
                        val incY = valToY(point.income)
                        val expY = valToY(point.expense)
                        val balY = valToY(point.balance)

                        if (i == 0) {
                            incomePath.moveTo(x, incY)
                            expensePath.moveTo(x, expY)
                            balancePath.moveTo(x, balY)
                            balanceAreaPath.moveTo(x, baselineY)
                            balanceAreaPath.lineTo(x, balY)
                        } else {
                            incomePath.lineTo(x, incY)
                            expensePath.lineTo(x, expY)
                            balancePath.lineTo(x, balY)
                            balanceAreaPath.lineTo(x, balY)
                        }
                    }

                    balanceAreaPath.lineTo((count - 1) * stepX, baselineY)
                    balanceAreaPath.close()

                    // Fill Balance Area with soft brand blue gradient
                    drawPath(
                        path = balanceAreaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.22f),
                                primaryColor.copy(alpha = 0.02f)
                            ),
                            startY = topPadding,
                            endY = baselineY
                        )
                    )

                    drawPath(
                        path = balancePath,
                        color = primaryColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = incomePath,
                        color = incomeColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = expensePath,
                        color = expenseColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Highlight Selected Point Column
                    if (safeIdx in points.indices) {
                        val selX = safeIdx * stepX
                        drawLine(
                            color = primaryColor.copy(alpha = 0.4f),
                            start = Offset(selX, topPadding),
                            end = Offset(selX, baselineY),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        val curBalY = valToY(points[safeIdx].balance)
                        drawCircle(color = surfaceColor, radius = 6.dp.toPx(), center = Offset(selX, curBalY))
                        drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = Offset(selX, curBalY))
                    }
                }
            }

            // X-Axis Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (points.isNotEmpty()) {
                    Text(text = points.first().label, fontSize = 10.sp, color = subtitleColor)
                    if (points.size > 2) {
                        val midIndex = points.size / 2
                        Text(text = points[midIndex].label, fontSize = 10.sp, color = subtitleColor)
                    }
                    if (points.size > 1) {
                        Text(text = points.last().label, fontSize = 10.sp, color = subtitleColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Category Donut Breakdown Chart with center stats
 */
@Composable
fun CategoryDonutChart(
    categoryStats: List<CategoryStat>,
    title: String,
    modifier: Modifier = Modifier,
    onCategoryClick: ((String) -> Unit)? = null
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (categoryStats.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(surfaceColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Belum ada transaksi di bagian ini", fontSize = 12.sp, color = subtitleColor)
        }
        return
    }

    val totalSum = remember(categoryStats) { categoryStats.sumOf { it.totalAmount } }
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(categoryStats) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, animationSpec = tween(800))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_donut_chart"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Donut Visual
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(120.dp)) {
                        val strokeWidth = 20.dp.toPx()
                        val arcSize = size.width - strokeWidth
                        var startAngle = -90f

                        categoryStats.forEachIndexed { index, stat ->
                            val sweepAngle = stat.percentage * 360f * animProgress.value
                            val color = CategoryPalette[index % CategoryPalette.size]

                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                size = Size(arcSize, arcSize),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += sweepAngle
                        }
                    }

                    // Center count indicator
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${categoryStats.size}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Kategori",
                            fontSize = 10.sp,
                            color = subtitleColor
                        )
                    }
                }

                // Top Categories List
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categoryStats.take(4).forEachIndexed { index, stat ->
                        val color = CategoryPalette[index % CategoryPalette.size]
                        val pct = (stat.percentage * 100).toInt()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onCategoryClick?.invoke(stat.categoryName) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stat.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "$pct%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Total footer
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total Akumulasi:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatRupiah(totalSum),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Category Comparison Bar Rankings
 */
@Composable
fun CategoryComparisonBars(
    categoryStats: List<CategoryStat>,
    isIncome: Boolean,
    modifier: Modifier = Modifier,
    onCategoryClick: (String) -> Unit
) {
    if (categoryStats.isEmpty()) return

    val maxAmount = remember(categoryStats) {
        categoryStats.maxOfOrNull { it.totalAmount } ?: 1L
    }
    val amountColor = if (isIncome) kasIncomeColor() else kasExpenseColor()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_comparison_bars_${if (isIncome) "income" else "expense"}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isIncome) "Perbandingan Sumber Pemasukan" else "Perbandingan Pos Pengeluaran",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                Text(
                    text = "${categoryStats.size} Kategori",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            categoryStats.forEachIndexed { index, stat ->
                val ratio = (stat.totalAmount.toFloat() / maxAmount).coerceIn(0f, 1f)
                val color = CategoryPalette[index % CategoryPalette.size]

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCategoryClick(stat.categoryName) }
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stat.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = "${formatRupiah(stat.totalAmount)} (${stat.count}x)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = amountColor,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(ratio)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}
