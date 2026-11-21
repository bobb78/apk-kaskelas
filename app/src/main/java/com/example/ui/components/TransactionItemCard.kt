package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.KasCategory
import com.example.data.TransactionEntity
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.ExpenseRedContainer
import com.example.ui.theme.GeoBrandBlue
import com.example.ui.theme.GeoSoftBlueContainer
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.IncomeGreenContainer
import com.example.ui.theme.LocalKasIsDark
import com.example.ui.theme.kasExpenseColor
import com.example.ui.theme.kasIncomeColor

fun getCategoryIcon(categoryName: String): ImageVector {
    return when (KasCategory.fromString(categoryName)) {
        KasCategory.IURAN_MINGGUAN -> Icons.Rounded.Payments
        KasCategory.IURAN_BULANAN -> Icons.Rounded.CalendarMonth
        KasCategory.DONASI -> Icons.Rounded.VolunteerActivism
        KasCategory.DENDA -> Icons.Rounded.Gavel
        KasCategory.SISA_ACARA -> Icons.Rounded.Savings
        KasCategory.PEMASUKAN_LAIN -> Icons.Rounded.AttachMoney
        KasCategory.ATK_KELAS -> Icons.Rounded.Edit
        KasCategory.ACARA_KELAS -> Icons.Rounded.Celebration
        KasCategory.KEBERSIHAN -> Icons.Rounded.CleaningServices
        KasCategory.FOTOKOPI -> Icons.Rounded.Print
        KasCategory.KONSUMSI -> Icons.Rounded.Restaurant
        KasCategory.SOSIAL -> Icons.Rounded.Favorite
        KasCategory.DEKORASI -> Icons.Rounded.Brush
        KasCategory.PENGELUARAN_LAIN -> Icons.AutoMirrored.Rounded.ReceiptLong
    }
}

fun getCategoryBadgeColors(categoryName: String, isIncome: Boolean): Pair<Color, Color> {
    return when (KasCategory.fromString(categoryName)) {
        KasCategory.IURAN_MINGGUAN,
        KasCategory.IURAN_BULANAN -> GeoSoftBlueContainer to GeoBrandBlue
        KasCategory.DONASI,
        KasCategory.SISA_ACARA -> IncomeGreenContainer to IncomeGreen
        KasCategory.DENDA -> Color(0xFFFEF3C7) to Color(0xFFB45309)
        KasCategory.PEMASUKAN_LAIN -> Color(0xFFE0F2FE) to Color(0xFF0369A1)
        KasCategory.ATK_KELAS -> Color(0xFFEDE9FE) to Color(0xFF6D28D9)
        KasCategory.ACARA_KELAS -> Color(0xFFFCE7F3) to Color(0xFFBE185D)
        KasCategory.KEBERSIHAN -> Color(0xFFCCFBF1) to Color(0xFF0F766E)
        KasCategory.FOTOKOPI -> Color(0xFFE0E7FF) to Color(0xFF4338CA)
        KasCategory.KONSUMSI -> Color(0xFFFFEDD5) to Color(0xFFC2410C)
        KasCategory.SOSIAL -> ExpenseRedContainer to ExpenseRed
        KasCategory.DEKORASI -> Color(0xFFFEF9C3) to Color(0xFFA16207)
        KasCategory.PENGELUARAN_LAIN -> if (isIncome) GeoSoftBlueContainer to GeoBrandBlue else Color(0xFFF1F5F9) to Color(0xFF475569)
    }
}

@Composable
fun getAdaptiveCategoryBadgeColors(categoryName: String, isIncome: Boolean): Pair<Color, Color> {
    val (lightBg, lightTint) = getCategoryBadgeColors(categoryName, isIncome)
    if (!LocalKasIsDark.current) return lightBg to lightTint
    return lightTint.copy(alpha = 0.22f) to lightBg
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionItemCard(
    transaction: TransactionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isIncome = transaction.isIncome
    val amountPrefix = if (isIncome) "+ " else "- "
    val amountColor = if (isIncome) kasIncomeColor() else kasExpenseColor()
    val resolvedCategory = KasCategory.fromString(transaction.category)
    val categoryDisplay = resolvedCategory.displayName
    val (badgeBg, badgeTint) = getAdaptiveCategoryBadgeColors(transaction.category, isIncome)

    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("transaction_item_${transaction.id}"),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Squircle Container for Category Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(transaction.category),
                    contentDescription = categoryDisplay,
                    tint = badgeTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Main Info Column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(5.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Explicit Category Pill Label
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("category_badge_${transaction.id}")
                    ) {
                        Text(
                            text = categoryDisplay,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    if (transaction.personName.isNotBlank()) {
                        Text(
                            text = transaction.personName,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Amount and Date Pill
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "$amountPrefix${transaction.formattedAmount}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = amountColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = if (isIncome) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = transaction.formattedDate.take(10),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isIncome) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
