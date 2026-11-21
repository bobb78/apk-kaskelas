package com.example.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.formatRupiah
import com.example.reminder.ReminderSettings
import com.example.reminder.UnpaidSummary
import com.example.ui.components.BulkStudentImportDialog
import com.example.ui.theme.GeoBrandBlue
import com.example.ui.theme.GeoNavy
import com.example.ui.theme.GeoSoftBlueContainer
import com.example.ui.theme.LocalKasIsDark
import com.example.ui.theme.kasGoldColor
import com.example.ui.theme.kasGoldContainer
import com.example.ui.theme.kasIncomeColor
import com.example.ui.theme.kasIncomeContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KasReminderScreen(
    viewModel: KasViewModel,
    onShowMessage: (String) -> Unit,
    onCopyReminderText: (String) -> Unit,
    onShareReminderText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.reminderSettings.collectAsStateWithLifecycle()
    val unpaidSummary by viewModel.unpaidSummary.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val studentRoster by viewModel.studentRoster.collectAsStateWithLifecycle()
    val pastPayerNames = remember(allTransactions) {
        viewModel.getHistoricalPayerNames(allTransactions)
    }

    KasReminderContent(
        settings = settings,
        unpaidSummary = unpaidSummary,
        existingRoster = studentRoster,
        pastPayerNames = pastPayerNames,
        onSaveSettings = { updated ->
            viewModel.updateReminderSettings(updated)
            onShowMessage("Pengaturan target iuran & pesan tagihan disimpan.")
        },
        onAddStudents = { raw ->
            val added = viewModel.addStudentsToRoster(raw)
            if (added > 0) {
                onShowMessage("$added anggota kelas ditambahkan ke daftar anggota.")
            }
        },
        onRemoveStudent = { name ->
            viewModel.removeStudentFromRoster(name)
            onShowMessage("$name dihapus dari daftar anggota.")
        },
        onMarkStudentPaid = { name, amount ->
            viewModel.markStudentPaidThisMonth(name, amount)
            onShowMessage("Iuran kas $name (${formatRupiah(amount)}) tercatat lunas!")
        },
        onCopyReminder = {
            onCopyReminderText(viewModel.generateReminderMessage())
        },
        onShareReminder = {
            onShareReminderText(viewModel.generateReminderMessage())
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KasReminderContent(
    settings: ReminderSettings,
    unpaidSummary: UnpaidSummary,
    onSaveSettings: (ReminderSettings) -> Unit,
    onAddStudents: (String) -> Unit,
    onRemoveStudent: (String) -> Unit,
    onMarkStudentPaid: (String, Long) -> Unit,
    onCopyReminder: () -> Unit,
    onShareReminder: () -> Unit,
    modifier: Modifier = Modifier,
    existingRoster: List<String> = unpaidSummary.paidMembers + unpaidSummary.unpaidMembers,
    pastPayerNames: List<String> = emptyList()
) {
    var targetAmountText by remember(settings.monthlyTargetAmount) {
        mutableStateOf(settings.monthlyTargetAmount.toString())
    }
    var customNote by remember(settings.customNote) { mutableStateOf(settings.customNote) }
    var newStudentInput by remember { mutableStateOf("") }
    var showBulkImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        targetAmountText = settings.monthlyTargetAmount.toString()
        customNote = settings.customNote
    }

    val targetAmount = targetAmountText.toLongOrNull()?.coerceAtLeast(1000L) ?: settings.monthlyTargetAmount

    fun buildEditedSettings(): ReminderSettings {
        return settings.copy(
            enabled = false,
            monthlyTargetAmount = targetAmount,
            customNote = customNote.ifBlank { "Mohon segera melunasi iuran kas kelas bulan ini." }
        )
    }

    val totalMembers = unpaidSummary.totalRosterCount
    val paidCount = unpaidSummary.paidMembers.size
    val unpaidCount = unpaidSummary.unpaidMembers.size
    val paidProgress = if (totalMembers > 0) paidCount.toFloat() / totalMembers else 0f
    val incomeClr = kasIncomeColor()
    val incomeBg = kasIncomeContainer()
    val goldClr = kasGoldColor()
    val goldBg = kasGoldContainer()
    val heroBg = if (LocalKasIsDark.current) Color(0xFF0E233B) else GeoNavy

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("kas_reminder_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Summary Banner for Class Dues Progress
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reminder_hero_card"),
                shape = RoundedCornerShape(24.dp),
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(GeoBrandBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Group,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Anggota & Tagihan Kas",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Target ${formatRupiah(targetAmount)} / bulan • Bagikan tagihan ke grup WA",
                                fontSize = 11.sp,
                                color = GeoSoftBlueContainer.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Progress for Current Month
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status ${unpaidSummary.monthLabel}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                        Text(
                            text = if (totalMembers > 0) "$paidCount/$totalMembers Lunas" else "Belum ada anggota",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (unpaidCount == 0 && totalMembers > 0) Color(0xFF86EFAC) else Color(0xFFFDE68A)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { paidProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (unpaidCount == 0 && totalMembers > 0) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                        trackColor = Color.White.copy(alpha = 0.18f)
                    )
                }
            }
        }

        // 2. Target Nominal & Share Message Settings Card
        item {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dues_settings_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Payments,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nominal Iuran & Pesan Tagihan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = targetAmountText,
                        onValueChange = { input -> targetAmountText = input.filter { it.isDigit() } },
                        label = { Text("Target Iuran Kas Bulanan (Rp)") },
                        prefix = {
                            Text(
                                "Rp ",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customNote,
                        onValueChange = { customNote = it },
                        label = { Text("Catatan Pesan Tagihan (untuk Grup WA)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { onSaveSettings(buildEditedSettings()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_dues_settings_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp),
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
                            text = "Simpan Pengaturan Tagihan",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 3. Unpaid Members Monitoring & Action Card
        item {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unpaid_members_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Anggota Belum Bayar (${unpaidSummary.monthLabel})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Daftar anggota otomatis tersinkron dengan menu Iuran Cepat & transaksi bulan ini.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Add Class Member Inline Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newStudentInput,
                            onValueChange = { newStudentInput = it },
                            label = { Text("Tambah Anggota Kelas") },
                            placeholder = { Text("Misal: Andi, Budi, Siti") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reminder_add_student_input"),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (newStudentInput.isNotBlank()) {
                                    onAddStudents(newStudentInput)
                                    newStudentInput = ""
                                }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newStudentInput.isNotBlank()) {
                                    onAddStudents(newStudentInput)
                                    newStudentInput = ""
                                }
                            },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("reminder_add_student_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = "Tambah anggota")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showBulkImportDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("reminder_open_bulk_import_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GroupAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Impor File CSV / Daftar Nama Massal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    when {
                        totalMembers == 0 -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PersonAdd,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Belum ada anggota kelas terdaftar",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Unggah file CSV absensi atau tempel daftar siswa di atas untuk memantau yang belum bayar tiap bulan.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        unpaidCount == 0 -> {
                            Surface(
                                color = incomeBg,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = incomeClr,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Semua $totalMembers anggota kelas sudah membayar iuran kas bulan ${unpaidSummary.monthLabel}!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = incomeClr
                                    )
                                }
                            }
                        }

                        else -> {
                            Text(
                                text = "Belum Bayar ($unpaidCount Orang)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = goldClr
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                unpaidSummary.unpaidMembers.forEach { memberName ->
                                    Surface(
                                        color = goldBg.copy(alpha = 0.72f),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, goldClr.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(goldClr.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = memberName.take(1).uppercase(),
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = goldClr,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = memberName,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "Tagihan: ${formatRupiah(targetAmount)}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                FilledTonalButton(
                                                    onClick = { onMarkStudentPaid(memberName, targetAmount) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("mark_paid_$memberName")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Bayar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                                IconButton(
                                                    onClick = { onRemoveStudent(memberName) },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = "Hapus $memberName",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (unpaidSummary.paidMembers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Sudah Bayar Bulan Ini ($paidCount Orang)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = incomeClr
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            unpaidSummary.paidMembers.forEach { member ->
                                Surface(
                                    color = incomeBg,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = incomeClr,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = member,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = incomeClr
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (totalMembers > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCopyReminder,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("copy_reminder_button"),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Salin Pesan",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = onShareReminder,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("share_reminder_button"),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Kirim Tagihan",
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
    }

    if (showBulkImportDialog) {
        BulkStudentImportDialog(
            existingRoster = existingRoster,
            pastPayerNames = pastPayerNames,
            onDismiss = { showBulkImportDialog = false },
            onImportNames = { imported ->
                onAddStudents(imported.joinToString("\n"))
                showBulkImportDialog = false
            }
        )
    }
}
