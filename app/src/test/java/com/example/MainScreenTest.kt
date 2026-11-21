package com.example

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.KasCategory
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.ui.KasSummary
import com.example.ui.KasViewModel
import com.example.ui.ReportAnalyticsData
import com.example.ui.components.RealtimeBalanceCard
import com.example.ui.components.TransactionItemCard
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.backup.BackupJsonCodec
import com.example.backup.BackupUiState
import com.example.backup.DriveBackupManager
import com.example.backup.DriveBackupMetadata
import com.example.backup.DriveBackupSettings
import com.example.backup.GoogleDriveApi
import com.example.backup.RestoreMode
import com.example.ui.KasBackupContent
import com.example.ui.KasHomeScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.ui.components.MonthlyBalanceTrendChart
import com.example.ui.components.QuickIuranSheet
import com.example.ui.components.ShareReportSheet
import org.junit.Assert.assertEquals

import com.example.data.KasPdfExporter
import com.example.reminder.KasReminderManager
import com.example.reminder.ReminderSettings
import com.example.ui.KasReminderContent
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.robolectric.annotation.GraphicsMode
import android.content.Context
import androidx.room.Room
import com.example.data.KasDatabase
import com.example.data.KasRepository
import com.example.ui.MainDashboardScreen
import com.example.ui.MonthlyTrendPoint
import com.example.ui.TransactionViewModel
import com.example.ui.components.AddTransactionScreen
import com.example.ui.components.BulkStudentImportDialog
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import org.junit.Assert.assertNotNull
import com.example.ui.components.MonthlyCashflowBarChart
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    qualifiers = RobolectricDeviceQualifiers.Pixel8,
    sdk = [36],
    shadows = [ShadowPdfDocument::class, ShadowPdfDocumentPage::class]
)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun balanceCard_and_transactionItem_displayCorrectly() {
        val sampleTx = TransactionEntity(
            id = 42L,
            type = TransactionType.PEMASUKAN.name,
            title = "Iuran Minggu Ke-4",
            amount = 25000L,
            category = KasCategory.IURAN_MINGGUAN.name,
            personName = "Andi"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    RealtimeBalanceCard(
                        summary = KasSummary(
                            totalIncome = 500000L,
                            totalExpense = 125000L,
                            balance = 375000L,
                            totalCount = 12
                        )
                    )
                    TransactionItemCard(
                        transaction = sampleTx,
                        onClick = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("realtime_balance_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Current Balance").assertIsDisplayed()
        composeTestRule.onNodeWithTag("current_balance_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("transaction_item_42").assertIsDisplayed()
        composeTestRule.onNodeWithText("Iuran Minggu Ke-4").assertIsDisplayed()
    }

    @Test
    fun shareReport_includesPaidPayersList() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = KasViewModel(app)
        val txs = listOf(
            TransactionEntity(
                id = 1L,
                type = TransactionType.PEMASUKAN.name,
                title = "Iuran Minggu 1",
                amount = 5000L,
                category = KasCategory.IURAN_MINGGUAN.name,
                personName = "Budi"
            ),
            TransactionEntity(
                id = 2L,
                type = TransactionType.PEMASUKAN.name,
                title = "Iuran Minggu 2",
                amount = 5000L,
                category = KasCategory.IURAN_MINGGUAN.name,
                personName = "budi"
            ),
            TransactionEntity(
                id = 3L,
                type = TransactionType.PEMASUKAN.name,
                title = "Iuran Minggu 1",
                amount = 10000L,
                category = KasCategory.IURAN_MINGGUAN.name,
                personName = "Siti"
            ),
            TransactionEntity(
                id = 4L,
                type = TransactionType.PENGELUARAN.name,
                title = "Beli Spidol",
                amount = 8000L,
                category = KasCategory.ATK_KELAS.name,
                personName = "Toko Maju"
            )
        )

        val report = viewModel.formatShareReportText(
            currentSummary = KasSummary(totalIncome = 20000L, totalExpense = 8000L, balance = 12000L, totalCount = 4),
            transactions = txs,
            analytics = ReportAnalyticsData(),
            dateStr = "17 November 2026, 20:00"
        )

        assertTrue(report.contains("Daftar Yang Telah Bayar (2 Orang)"))
        assertTrue(report.contains("Budi"))
        assertTrue(report.contains("(2 kali)"))
        assertTrue(report.contains("Siti"))
        assertFalse(report.contains("1. Toko Maju"))
    }

    @Test
    fun quickIuranSheet_selectAll_and_bulkAdd_works() {
        var submittedStudents: List<String> = emptyList()
        var submittedAmount = 0L

        composeTestRule.setContent {
            MyApplicationTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                QuickIuranSheet(
                    sheetState = sheetState,
                    onDismiss = {},
                    onSubmitBatch = { amount, students, _, _ ->
                        submittedAmount = amount
                        submittedStudents = students
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Bulk add 3 comma-separated students
        composeTestRule.onNodeWithTag("student_name_input").performScrollTo().performTextInput("Dimas, Siti, Budi")
        composeTestRule.onNodeWithTag("add_student_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Clear all, then tap Pilih Semua
        composeTestRule.onNodeWithTag("clear_all_students_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("select_all_students_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Submit batch
        composeTestRule.onNodeWithTag("submit_quick_iuran_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(5000L, submittedAmount)
        assertEquals(listOf("Dimas", "Siti", "Budi"), submittedStudents)
    }

    @Test
    fun shareReportSheet_displaysPaidPayers_and_pdfButtons() {
        val payers = listOf(
            KasViewModel.PaidPayerSummary(name = "Andi", totalPaid = 10000L, paymentCount = 2),
            KasViewModel.PaidPayerSummary(name = "Siti", totalPaid = 5000L, paymentCount = 1)
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ShareReportSheet(
                    sheetState = sheetState,
                    reportText = "Laporan Kas\nDaftar Yang Telah Bayar:\n1. Andi\n2. Siti",
                    paidPayers = payers,
                    onDismiss = {},
                    onCopyReport = {},
                    onShareExternal = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("print_pdf_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_pdf_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_pdf_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_paid_payers_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Sudah Bayar (2 Orang)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_report_preview_text").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun pdfExporter_generatesValidMultiPagePdf() {
        val txs = (1..35).map { idx ->
            TransactionEntity(
                id = idx.toLong(),
                type = if (idx % 4 == 0) TransactionType.PENGELUARAN.name else TransactionType.PEMASUKAN.name,
                title = "Transaksi Kas #$idx",
                amount = 5000L * idx,
                category = if (idx % 4 == 0) KasCategory.ATK_KELAS.name else KasCategory.IURAN_MINGGUAN.name,
                personName = "Siswa $idx"
            )
        }
        val payers = (1..10).map { idx ->
            KasViewModel.PaidPayerSummary(name = "Siswa $idx", totalPaid = 15000L, paymentCount = 2)
        }
        val out = ByteArrayOutputStream()

        KasPdfExporter.writePdfToOutputStream(
            outputStream = out,
            summary = KasSummary(totalIncome = 500000L, totalExpense = 120000L, balance = 380000L, totalCount = txs.size),
            transactions = txs,
            paidPayers = payers
        )

        val bytes = out.toByteArray()
        assertTrue("PDF output should not be empty", bytes.isNotEmpty())
        val content = String(bytes, Charsets.US_ASCII)
        val header = content.take(5)
        assertEquals("%PDF-", header)
        assertFalse("35 transactions should span multiple pages", content.contains("/Count 1 "))
    }

    @Test
    fun transactionItem_displaysCategoryBadge_forIncomeAndExpense() {
        val incomeTx = TransactionEntity(
            id = 101L,
            type = TransactionType.PEMASUKAN.name,
            title = "Setoran Budi",
            amount = 10000L,
            category = KasCategory.IURAN_MINGGUAN.name,
            personName = "Budi"
        )
        val atkTx = TransactionEntity(
            id = 102L,
            type = TransactionType.PENGELUARAN.name,
            title = "Beli Spidol Snowman",
            amount = 18000L,
            category = KasCategory.ATK_KELAS.name,
            personName = "Siti"
        )
        val eventTx = TransactionEntity(
            id = 103L,
            type = TransactionType.PENGELUARAN.name,
            title = "Dekor Classmeeting",
            amount = 50000L,
            category = KasCategory.ACARA_KELAS.name,
            personName = "Panitia"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    TransactionItemCard(transaction = incomeTx, onClick = {})
                    TransactionItemCard(transaction = atkTx, onClick = {})
                    TransactionItemCard(transaction = eventTx, onClick = {})
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("category_badge_101", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Uang Kas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_badge_102", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Pembelian Alat Tulis").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_badge_103", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Acara Kelas").assertIsDisplayed()
    }

    @Test
    fun monthlyBalanceTrend_computesAndDisplaysInteractiveChart() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = KasViewModel(app)

        fun millisFor(year: Int, monthZeroBased: Int, day: Int): Long {
            return Calendar.getInstance().apply {
                set(year, monthZeroBased, day, 10, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val txs = listOf(
            TransactionEntity(
                id = 1L,
                type = TransactionType.PEMASUKAN.name,
                title = "Uang Kas September",
                amount = 200000L,
                category = KasCategory.IURAN_MINGGUAN.name,
                dateMillis = millisFor(2026, Calendar.SEPTEMBER, 5)
            ),
            TransactionEntity(
                id = 2L,
                type = TransactionType.PENGELUARAN.name,
                title = "Pembelian Alat Tulis",
                amount = 50000L,
                category = KasCategory.ATK_KELAS.name,
                dateMillis = millisFor(2026, Calendar.OCTOBER, 10)
            ),
            TransactionEntity(
                id = 3L,
                type = TransactionType.PEMASUKAN.name,
                title = "Uang Kas Oktober",
                amount = 150000L,
                category = KasCategory.IURAN_MINGGUAN.name,
                dateMillis = millisFor(2026, Calendar.OCTOBER, 15)
            ),
            TransactionEntity(
                id = 4L,
                type = TransactionType.PENGELUARAN.name,
                title = "Acara Kelas",
                amount = 80000L,
                category = KasCategory.ACARA_KELAS.name,
                dateMillis = millisFor(2026, Calendar.NOVEMBER, 2)
            )
        )

        val monthly = viewModel.computeMonthlyTrends(txs)
        assertEquals(3, monthly.size)
        assertEquals("2026-09", monthly[0].monthKey)
        assertEquals(200000L, monthly[0].endingBalance)
        assertEquals("2026-10", monthly[1].monthKey)
        assertEquals(300000L, monthly[1].endingBalance)
        assertEquals("2026-11", monthly[2].monthKey)
        assertEquals(220000L, monthly[2].endingBalance)

        composeTestRule.setContent {
            MyApplicationTheme {
                MonthlyBalanceTrendChart(monthlyPoints = monthly)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("monthly_balance_trend_chart").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monthly_balance_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monthly_trend_tooltip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("month_pill_2026-09").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("200.000", substring = true).assertIsDisplayed()
    }

    @Test
    fun pdfExporter_includesUnpaidMembersAndPeriodLabel() {
        val txs = listOf(
            TransactionEntity(
                id = 1L,
                type = TransactionType.PEMASUKAN.name,
                title = "Iuran Kas November",
                amount = 10000L,
                category = KasCategory.IURAN_BULANAN.name,
                personName = "Andi",
                notes = "Lunas awal bulan"
            )
        )
        val payers = listOf(KasViewModel.PaidPayerSummary(name = "Andi", totalPaid = 10000L, paymentCount = 1))
        val out = ByteArrayOutputStream()

        KasPdfExporter.writePdfToOutputStream(
            outputStream = out,
            summary = KasSummary(totalIncome = 10000L, totalExpense = 0L, balance = 10000L, totalCount = 1),
            transactions = txs,
            paidPayers = payers,
            unpaidMembers = listOf("Budi", "Siti", "Dimas"),
            periodLabel = "Bulan November 2026"
        )

        val bytes = out.toByteArray()
        assertTrue(bytes.isNotEmpty())
        assertEquals("%PDF-", String(bytes, Charsets.US_ASCII).take(5))
    }

    @Test
    fun unpaidMembers_computesMonthlyStatus_andFormatsShareMessage() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val nov15 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 15, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val oct20 = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 20, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val roster = listOf("Andi", "Budi", "Siti", "Dimas")
        val txs = listOf(
            TransactionEntity(
                id = 1L,
                type = TransactionType.PEMASUKAN.name,
                title = "Iuran Nov",
                amount = 10000L,
                category = KasCategory.IURAN_BULANAN.name,
                personName = "andi",
                dateMillis = nov15
            ),
            // October payment should NOT mark Budi as paid for November
            TransactionEntity(
                id = 2L,
                type = TransactionType.PEMASUKAN.name,
                title = "Iuran Okt",
                amount = 10000L,
                category = KasCategory.IURAN_BULANAN.name,
                personName = "Budi",
                dateMillis = oct20
            )
        )

        val unpaid = KasReminderManager.computeUnpaidMembersForMonth(roster, txs, nowMillis = nov15)
        assertEquals(listOf("Andi"), unpaid.paidMembers)
        assertEquals(listOf("Budi", "Siti", "Dimas"), unpaid.unpaidMembers)

        // Reminder text formatting lists all unpaid members & target amount for WhatsApp group sharing
        val msg = KasReminderManager.formatReminderShareMessage(unpaid, 10000L, "Bayar ke bendahara.")
        assertTrue(msg.contains("Rp 10.000"))
        assertTrue(msg.contains("Budi"))
        assertTrue(msg.contains("Siti"))
        assertTrue(msg.contains("Dimas"))
        assertTrue(msg.contains("Bayar ke bendahara."))
        assertFalse(msg.contains("1. Andi"))

        // Verify dues settings persist locally without notification scheduling
        val settings = ReminderSettings(monthlyTargetAmount = 15000L, customNote = "Transfer ke kas kelas")
        KasReminderManager.saveSettings(app, settings)
        val loaded = KasReminderManager.getSettings(app)
        assertEquals(15000L, loaded.monthlyTargetAmount)
        assertEquals("Transfer ke kas kelas", loaded.customNote)
    }

    @Test
    fun reminderScreen_displaysUnpaidMembers_andTriggersActions() {
        var savedSettings: ReminderSettings? = null
        var markedPaidName: String? = null
        var shareClicked = false

        val unpaidSummary = com.example.reminder.UnpaidSummary(
            monthLabel = "November 2026",
            totalRosterCount = 3,
            paidMembers = listOf("Andi"),
            unpaidMembers = listOf("Dimas", "Budi")
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                KasReminderContent(
                    settings = ReminderSettings(monthlyTargetAmount = 15000L),
                    unpaidSummary = unpaidSummary,
                    onSaveSettings = { savedSettings = it },
                    onAddStudents = {},
                    onRemoveStudent = {},
                    onMarkStudentPaid = { name, _ -> markedPaidName = name },
                    onCopyReminder = {},
                    onShareReminder = { shareClicked = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("kas_reminder_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reminder_hero_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dues_settings_card").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("save_dues_settings_button").performScrollTo().performClick()
        assertEquals(15000L, savedSettings?.monthlyTargetAmount)
        assertFalse(savedSettings?.enabled == true)

        composeTestRule.onNodeWithText("Dimas").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("mark_paid_Dimas").performScrollTo().performClick()
        assertEquals("Dimas", markedPaidName)

        composeTestRule.onNodeWithTag("share_reminder_button").performScrollTo().performClick()
        assertTrue(shareClicked)
    }

    @Test
    fun roomDatabase_persistsIncomeAndExpense_acrossCloseAndReopen() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dbName = "test_kas_persistence_${System.currentTimeMillis()}.db"
        val dbFile = app.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()

        val db1 = Room.databaseBuilder(app, KasDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val dao1 = db1.transactionDao()

        val incomeId = dao1.insertTransaction(
            TransactionEntity(
                type = TransactionType.PEMASUKAN.name,
                title = "Iuran Kas Oktober",
                amount = 150000L,
                category = KasCategory.IURAN_BULANAN.name,
                personName = "Andi"
            )
        )
        val expenseId = dao1.insertTransaction(
            TransactionEntity(
                type = TransactionType.PENGELUARAN.name,
                title = "Pembelian Spidol",
                amount = 35000L,
                category = KasCategory.ATK_KELAS.name,
                personName = "Bendahara"
            )
        )
        assertTrue(incomeId > 0L)
        assertTrue(expenseId > 0L)
        db1.close()

        // Reopen database from disk (simulating app restart after closing)
        val db2 = Room.databaseBuilder(app, KasDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val persisted = db2.transactionDao().getAllTransactionsSync()
        db2.close()
        File(dbFile.absolutePath).delete()

        assertEquals(2, persisted.size)
        assertTrue(persisted.any { it.isIncome && it.title == "Iuran Kas Oktober" && it.amount == 150000L })
        assertTrue(persisted.any { !it.isIncome && it.title == "Pembelian Spidol" && it.amount == 35000L })
    }

    @Test
    fun monthlyCashflowBarChart_displaysAndSwitchesMonthSelection() {
        val points = listOf(
            MonthlyTrendPoint(
                monthKey = "2026-09",
                label = "Sep '26",
                fullLabel = "September 2026",
                income = 300000L,
                expense = 75000L,
                netChange = 225000L,
                endingBalance = 225000L,
                transactionCount = 5,
                timestamp = 1L
            ),
            MonthlyTrendPoint(
                monthKey = "2026-10",
                label = "Okt '26",
                fullLabel = "Oktober 2026",
                income = 180000L,
                expense = 90000L,
                netChange = 90000L,
                endingBalance = 315000L,
                transactionCount = 4,
                timestamp = 2L
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                MonthlyCashflowBarChart(monthlyPoints = points)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("monthly_cashflow_chart").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cashflow_selected_summary").assertIsDisplayed()
        composeTestRule.onNodeWithText("Oktober 2026").assertIsDisplayed()

        composeTestRule.onNodeWithTag("cashflow_month_bar_2026-09").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("September 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("+Rp 300.000", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("-Rp 75.000", substring = true).assertIsDisplayed()
    }

    @Test
    fun smartRosterParser_and_bulkImportDialog_extractsAndImportsNames() {
        val rawWhatsappAndCsv = """
            No,Nama
            1. Andi Pratama
            2) Budi Santoso
            - Siti Aminah
            • Dimas Wijaya
            1. Andi Pratama
            99
        """.trimIndent()

        val parsed = KasReminderManager.parseStudentNames(rawWhatsappAndCsv)
        assertEquals(
            listOf("Andi Pratama", "Budi Santoso", "Siti Aminah", "Dimas Wijaya"),
            parsed
        )

        var importedResult: List<String> = emptyList()
        composeTestRule.setContent {
            MyApplicationTheme {
                BulkStudentImportDialog(
                    existingRoster = listOf("Andi Pratama"),
                    pastPayerNames = listOf("Rina Putri"),
                    onDismiss = {},
                    onImportNames = { importedResult = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bulk_student_import_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bulk_import_text_input")
            .performTextInput("1. Andi Pratama\n2. Budi Santoso\n- Siti Aminah")
        composeTestRule.onNodeWithTag("bulk_import_history_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("confirm_bulk_import_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("Budi Santoso", "Siti Aminah", "Rina Putri"), importedResult)
    }

    @Test
    fun csvImportParser_handlesExcelSemicolonBomQuotedNamesAndHeaderlessRows() {
        // 1. Google Sheets / Excel comma CSV with UTF-8 BOM, multi-column & quoted "Last, First"
        val excelCommaCsv = "\uFEFF" + listOf(
            "No,Nama Siswa,NIS,Kelas,Status",
            "1,Andi Pratama,202601,XI-A,Aktif",
            "2,\"Santoso, Budi\",202602,XI-A,Aktif",
            "3,\"Aulia, \"\"Siti\"\"\",202603,XI-A,Lunas"
        ).joinToString("\n")

        val commaResult = KasReminderManager.parseCsvStudentNames(excelCommaCsv)
        assertEquals("Nama Siswa", commaResult.detectedColumnName)
        assertEquals(3, commaResult.rowCount)
        assertEquals(
            listOf("Andi Pratama", "Santoso, Budi", "Aulia, \"Siti\""),
            commaResult.names
        )

        // 2. Indonesian Excel semicolon-delimited CSV
        val excelSemicolonCsv = """
            No;Nama Lengkap;L/P
            1;Dimas Wijaya;L
            2;Rina Putri;P
            3;Fajar Nugroho;L
        """.trimIndent()

        val semicolonResult = KasReminderManager.parseCsvStudentNames(excelSemicolonCsv)
        assertEquals("Nama Lengkap", semicolonResult.detectedColumnName)
        assertEquals(
            listOf("Dimas Wijaya", "Rina Putri", "Fajar Nugroho"),
            semicolonResult.names
        )

        // 3. Headerless multi-column CSV infers student name column
        val headerlessCsv = """
            1,Dewi Lestari,X-2
            2,Bagas Kara,X-2
            3,Citra Kirana,X-2
        """.trimIndent()

        val headerlessResult = KasReminderManager.parseCsvStudentNames(headerlessCsv)
        assertEquals(
            listOf("Dewi Lestari", "Bagas Kara", "Citra Kirana"),
            headerlessResult.names
        )
    }

    @Test
    fun darkThemeToggle_updatesStatePersistsAndRendersDarkMode() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("kas_theme_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val viewModel = KasViewModel(app)
        assertFalse(viewModel.isDarkTheme.value)

        composeTestRule.setContent {
            val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = isDark) {
                KasHomeScreen(viewModel = viewModel)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("theme_toggle_button").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Aktifkan mode gelap").assertIsDisplayed()

        // Toggle to dark mode via TopBar button
        composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(viewModel.isDarkTheme.value)
        composeTestRule.onNodeWithContentDescription("Aktifkan mode terang").assertIsDisplayed()

        // Persistence across ViewModel recreation (app restart simulation)
        val recreatedVm = KasViewModel(app)
        assertTrue(recreatedVm.isDarkTheme.value)

        // Toggle back to light mode
        composeTestRule.onNodeWithTag("theme_toggle_button").performClick()
        composeTestRule.waitForIdle()
        assertFalse(viewModel.isDarkTheme.value)
        assertFalse(KasViewModel(app).isDarkTheme.value)
    }

    @Test
    fun driveBackup_serializesRestoresMergeAndReplace_andSchedulesWorkManager() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences(DriveBackupManager.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)

        val dbName = "test_drive_backup_${System.currentTimeMillis()}.db"
        val dbFile = app.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()

        val db = Room.databaseBuilder(app, KasDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val dao = db.transactionDao()

        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.PEMASUKAN.name,
                title = "Iuran Minggu 1 - Andi",
                amount = 10000L,
                category = KasCategory.IURAN_MINGGUAN.name,
                dateMillis = 1763500000000L,
                personName = "Andi"
            )
        )
        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.PENGELUARAN.name,
                title = "Beli Spidol",
                amount = 4000L,
                category = KasCategory.ATK_KELAS.name,
                dateMillis = 1763503600000L,
                personName = "Bendahara"
            )
        )

        KasReminderManager.saveStudentRoster(app, listOf("Andi", "Budi"))
        KasReminderManager.saveSettings(
            app,
            ReminderSettings(enabled = true, dayOfMonth = 15, hour = 9, minute = 0, monthlyTargetAmount = 20000L)
        )

        var storedJson = ""
        var storedMeta: DriveBackupMetadata? = null
        val fakeDriveApi = object : GoogleDriveApi {
            override suspend fun fetchUserEmail(accessToken: String): String = "bendahara@gmail.com"
            override suspend fun findLatestBackupFile(accessToken: String): DriveBackupMetadata? = storedMeta
            override suspend fun uploadBackupJson(
                accessToken: String,
                jsonContent: String,
                existingFileId: String?,
                transactionCount: Int,
                rosterCount: Int,
                balance: Long
            ): DriveBackupMetadata {
                storedJson = jsonContent
                val meta = DriveBackupMetadata(
                    fileId = "drive_file_123",
                    fileName = "kaskelas_room_backup.json",
                    modifiedTimeMillis = System.currentTimeMillis(),
                    sizeBytes = jsonContent.length.toLong(),
                    transactionCount = transactionCount,
                    rosterCount = rosterCount,
                    balance = balance
                )
                storedMeta = meta
                return meta
            }

            override suspend fun downloadBackupJson(accessToken: String, fileId: String): String = storedJson
        }

        // 1. Perform Backup Now
        val backupRes = DriveBackupManager.performBackupNow(app, "token-123", fakeDriveApi, dao)
        assertTrue(backupRes.isSuccess)
        assertTrue(storedJson.isNotBlank())
        val decoded = BackupJsonCodec.decodePayload(storedJson)
        assertEquals(2, decoded.transactions.size)
        assertEquals(listOf("Andi", "Budi"), decoded.studentRoster)
        assertEquals(15, decoded.reminderSettings.dayOfMonth)

        val savedPrefs = DriveBackupManager.getSettings(app)
        assertEquals("bendahara@gmail.com", savedPrefs.connectedEmail)
        assertEquals(2, savedPrefs.lastBackedUpTransactionCount)

        // 2. Simulate reinstall / new local transaction + Merge Restore
        dao.deleteAllTransactions()
        KasReminderManager.saveStudentRoster(app, listOf("Siti"))
        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.PEMASUKAN.name,
                title = "Iuran Baru Lokal",
                amount = 15000L,
                category = KasCategory.IURAN_BULANAN.name,
                dateMillis = 1763510000000L,
                personName = "Siti"
            )
        )

        val mergeRes = DriveBackupManager.restoreFromDrive(app, "token-123", RestoreMode.MERGE_MISSING, fakeDriveApi, dao)
        assertTrue(mergeRes.isSuccess)
        val mergeSummary = mergeRes.getOrThrow()
        assertEquals(2, mergeSummary.restoredTransactionsCount)
        assertEquals(3, mergeSummary.totalLocalTransactionsAfter)
        assertEquals(listOf("Siti", "Andi", "Budi"), KasReminderManager.getStudentRoster(app))

        // Running Merge again skips duplicates cleanly
        val secondMerge = DriveBackupManager.restoreFromDrive(app, "token-123", RestoreMode.MERGE_MISSING, fakeDriveApi, dao)
        assertEquals(0, secondMerge.getOrThrow().restoredTransactionsCount)
        assertEquals(2, secondMerge.getOrThrow().skippedDuplicateCount)

        // 3. Test Replace All Restore
        val replaceRes = DriveBackupManager.restoreFromDrive(app, "token-123", RestoreMode.REPLACE_ALL, fakeDriveApi, dao)
        assertTrue(replaceRes.isSuccess)
        assertEquals(2, dao.getAllTransactionsSync().size)
        assertEquals(listOf("Andi", "Budi"), KasReminderManager.getStudentRoster(app))

        // 4. Verify WorkManager automatic periodic + debounced work scheduling
        val autoSettings = DriveBackupManager.getSettings(app).copy(
            autoBackupEnabled = true,
            intervalHours = 24,
            syncOnDataChange = true,
            connectedEmail = "bendahara@gmail.com"
        )
        DriveBackupManager.saveSettings(app, autoSettings)
        DriveBackupManager.syncWorkSchedule(app, autoSettings)
        DriveBackupManager.scheduleDebouncedBackupOnDataChange(app)

        val wm = WorkManager.getInstance(app)
        val periodicInfos = wm.getWorkInfosForUniqueWork(DriveBackupManager.UNIQUE_PERIODIC_WORK).get()
        val immediateInfos = wm.getWorkInfosForUniqueWork(DriveBackupManager.UNIQUE_IMMEDIATE_WORK).get()
        assertTrue(periodicInfos.any { it.state == WorkInfo.State.ENQUEUED })
        assertTrue(immediateInfos.any { it.state == WorkInfo.State.ENQUEUED })

        db.close()
        File(dbFile.absolutePath).delete()
        Unit
    }

    @Test
    fun backupScreen_displaysControlsAndTriggersActions() {
        var updatedSettings: DriveBackupSettings? = null
        var triggeredRestoreMode: RestoreMode? = null
        var connectClicked = false
        var exportJsonClicked = false
        var importJsonClicked = false

        val sampleSettings = DriveBackupSettings(
            autoBackupEnabled = false,
            intervalHours = 24,
            wifiOnly = false,
            syncOnDataChange = true,
            connectedEmail = "bendahara@gmail.com",
            lastBackupMillis = 1763500000000L,
            lastBackupStatus = "Berhasil dicadangkan (12 transaksi)",
            lastBackedUpTransactionCount = 12
        )
        val sampleUiState = BackupUiState(
            isBusy = false,
            remoteBackup = DriveBackupMetadata(
                fileId = "file_abc",
                fileName = "kaskelas_room_backup.json",
                modifiedTimeMillis = 1763500000000L,
                sizeBytes = 2048L,
                transactionCount = 12,
                rosterCount = 30,
                balance = 350000L
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                KasBackupContent(
                    settings = sampleSettings,
                    uiState = sampleUiState,
                    localTransactionCount = 12,
                    localRosterCount = 30,
                    onConnectOrBackupClick = { connectClicked = true },
                    onUpdateSettings = { updatedSettings = it },
                    onCheckRemoteBackup = {},
                    onRestoreBackup = { triggeredRestoreMode = it },
                    onDisconnect = {},
                    onExportLocalJsonClick = { exportJsonClicked = true },
                    onImportLocalJsonClick = { importJsonClicked = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("kas_backup_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("backup_hero_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("bendahara@gmail.com").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drive_connect_or_backup_button").performClick()
        assertTrue(connectClicked)

        composeTestRule.onNodeWithTag("auto_backup_switch").performScrollTo().performClick()
        assertTrue(updatedSettings?.autoBackupEnabled == true)

        composeTestRule.onNodeWithTag("kas_backup_screen")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("remote_backup_info_box"))
        composeTestRule.onNodeWithTag("remote_backup_info_box").assertIsDisplayed()
        composeTestRule.onNodeWithTag("kas_backup_screen")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("restore_drive_button"))
        composeTestRule.onNodeWithTag("restore_drive_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("restore_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("restore_mode_merge").performClick()
        assertEquals(RestoreMode.MERGE_MISSING, triggeredRestoreMode)

        composeTestRule.onNodeWithTag("kas_backup_screen")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("local_json_backup_card"))
        composeTestRule.onNodeWithTag("export_local_json_button").performClick()
        assertTrue(exportJsonClicked)
        composeTestRule.onNodeWithTag("import_local_json_button").performClick()
        assertTrue(importJsonClicked)
    }

    @Test
    fun localJsonBackup_exportsAndRestoresOfflineWithoutGoogleAuth() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dbName = "test_local_json_backup_${System.currentTimeMillis()}.db"
        val dbFile = app.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()

        val db = Room.databaseBuilder(app, KasDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val dao = db.transactionDao()

        dao.insertTransaction(
            TransactionEntity(
                type = TransactionType.PEMASUKAN.name,
                title = "Kas Mingguan - Rina",
                amount = 20000L,
                category = KasCategory.IURAN_MINGGUAN.name,
                dateMillis = 1763520000000L,
                personName = "Rina"
            )
        )
        KasReminderManager.saveStudentRoster(app, listOf("Rina", "Fajar"))

        val exportedJson = DriveBackupManager.exportLocalBackupJson(app, dao).getOrThrow()
        assertTrue(exportedJson.contains("Kas Mingguan - Rina"))

        // Clear DB & roster to simulate fresh APK install on another device
        dao.deleteAllTransactions()
        KasReminderManager.saveStudentRoster(app, emptyList())
        assertEquals(0, dao.getAllTransactionsSync().size)

        val restoreSummary = DriveBackupManager.restoreFromLocalJson(
            context = app,
            rawJson = exportedJson,
            mode = RestoreMode.REPLACE_ALL,
            dao = dao
        ).getOrThrow()

        assertEquals(1, restoreSummary.restoredTransactionsCount)
        assertEquals(2, restoreSummary.restoredRosterCount)
        assertEquals(1, dao.getAllTransactionsSync().size)
        assertEquals(listOf("Rina", "Fajar"), KasReminderManager.getStudentRoster(app))

        db.close()
        File(dbFile.absolutePath).delete()
        Unit
    }

    @Test
    fun addTransactionScreen_radioButtonsDatePickerAndRoomDao_workEndToEnd() = kotlinx.coroutines.runBlocking {
        var savedType: TransactionType? = null
        var savedTitle = ""
        var savedAmount = 0L
        var savedCategory = ""
        var savedDateMillis = 0L

        composeTestRule.setContent {
            MyApplicationTheme {
                AddTransactionScreen(
                    onNavigateBack = {},
                    onSave = { type, title, amount, category, dateMillis, _, _ ->
                        savedType = type
                        savedTitle = title
                        savedAmount = amount
                        savedCategory = category
                        savedDateMillis = dateMillis
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_transaction_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("transaction_type_radio_group").assertIsDisplayed()
        composeTestRule.onNodeWithTag("type_income_button").assertIsSelected()
        composeTestRule.onNodeWithTag("type_expense_button").assertIsNotSelected()

        // Switch RadioButton to Pengeluaran (Expense)
        composeTestRule.onNodeWithTag("type_expense_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("type_expense_button").assertIsSelected()
        composeTestRule.onNodeWithTag("type_income_button").assertIsNotSelected()

        // Fill amount & description
        composeTestRule.onNodeWithTag("amount_input_field").performScrollTo().performTextInput("25000")
        composeTestRule.onNodeWithTag("title_input_field").performScrollTo().performTextInput("Beli Kertas HVS")

        // Open Material 3 DatePickerDialog and confirm selection
        composeTestRule.onNodeWithTag("date_picker_field").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("date_picker_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_date_picker_button").performClick()
        composeTestRule.waitForIdle()

        // Also verify quick-date shortcut updates date cleanly
        composeTestRule.onNodeWithTag("quick_date_yesterday").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Submit form
        composeTestRule.onNodeWithTag("save_transaction_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(TransactionType.PENGELUARAN, savedType)
        assertEquals("Beli Kertas HVS", savedTitle)
        assertEquals(25000L, savedAmount)
        assertEquals(KasCategory.ATK_KELAS.name, savedCategory)
        assertTrue(savedDateMillis > 0L)

        // Verify Room Entity & DAO persistence including getTransactionById
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dbFile = app.getDatabasePath("test_add_tx_dao.db")
        if (dbFile.exists()) dbFile.delete()
        val db = Room.databaseBuilder(app, KasDatabase::class.java, "test_add_tx_dao.db")
            .allowMainThreadQueries()
            .build()
        val dao = db.transactionDao()

        val insertedId = dao.insertTransaction(
            TransactionEntity(
                type = savedType!!.name,
                title = savedTitle,
                amount = savedAmount,
                category = savedCategory,
                dateMillis = savedDateMillis
            )
        )
        val loaded = dao.getTransactionById(insertedId)
        assertNotNull(loaded)
        assertEquals(TransactionType.PENGELUARAN.name, loaded!!.type)
        assertEquals("Beli Kertas HVS", loaded.title)
        assertEquals(25000L, loaded.amount)
        assertEquals(savedDateMillis, loaded.dateMillis)

        db.close()
        File(dbFile.absolutePath).delete()
        Unit
    }

    @Test
    fun transactionViewModel_and_mainDashboardScreen_computeBalanceAndDisplayRecentTransactions() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dbFile = app.getDatabasePath("test_tx_viewmodel_dashboard.db")
        if (dbFile.exists()) dbFile.delete()
        val db = Room.databaseBuilder(app, KasDatabase::class.java, "test_tx_viewmodel_dashboard.db")
            .allowMainThreadQueries()
            .build()
        val dao = db.transactionDao()
        val repository = KasRepository(dao)

        runBlocking {
            dao.insertTransaction(
                TransactionEntity(
                    type = TransactionType.PEMASUKAN.name,
                    title = "Iuran Kas Kelas XI",
                    amount = 150000L,
                    category = KasCategory.IURAN_BULANAN.name,
                    dateMillis = 1763510000000L,
                    personName = "Rina"
                )
            )
            dao.insertTransaction(
                TransactionEntity(
                    type = TransactionType.PENGELUARAN.name,
                    title = "Pembelian Spidol Papan",
                    amount = 35000L,
                    category = KasCategory.ATK_KELAS.name,
                    dateMillis = 1763515000000L,
                    personName = "Bendahara"
                )
            )
            val syncRows = dao.getAllTransactionsSync()
            assertEquals(2, syncRows.size)
        }

        val txViewModel = TransactionViewModel(app, repository)

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                MainDashboardScreen(transactionViewModel = txViewModel)
            }
        }

        composeTestRule.waitForIdle()
        try {
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                txViewModel.totalBalance.value == 115000L
            }
        } catch (e: Throwable) {
            db.close()
            File(dbFile.absolutePath).delete()
            throw AssertionError(
                "Timed out waiting for balance=115000; actual balance=${txViewModel.totalBalance.value}, " +
                    "txCount=${txViewModel.transactions.value.size}, uiState=${txViewModel.uiState.value}",
                e
            )
        }

        assertEquals(115000L, txViewModel.totalBalance.value)
        assertEquals(150000L, txViewModel.totalIncome.value)
        assertEquals(35000L, txViewModel.totalExpense.value)
        assertEquals(2, txViewModel.transactions.value.size)

        composeTestRule.onNodeWithTag("realtime_balance_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("current_balance_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rp 115.000", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_transactions_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_transactions_list")
            .performScrollToNode(androidx.compose.ui.test.hasText("Pembelian Spidol Papan"))
        composeTestRule.onNodeWithText("Pembelian Spidol Papan").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_transactions_list")
            .performScrollToNode(androidx.compose.ui.test.hasText("Iuran Kas Kelas XI"))
        composeTestRule.onNodeWithText("Iuran Kas Kelas XI").assertIsDisplayed()

        db.close()
        File(dbFile.absolutePath).delete()
    }
}
