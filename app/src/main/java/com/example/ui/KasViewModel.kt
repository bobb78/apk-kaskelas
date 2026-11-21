package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.BackupUiState
import com.example.backup.DriveAuthExpiredException
import com.example.backup.DriveBackupManager
import com.example.backup.DriveBackupSettings
import com.example.backup.RestoreMode
import com.example.data.KasCategory
import com.example.data.KasDatabase
import com.example.data.KasRepository
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import com.example.data.formatRupiah
import com.example.reminder.KasReminderManager
import com.example.reminder.ReminderSettings
import com.example.reminder.UnpaidSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class FilterType(val label: String) {
    ALL("Semua"),
    INCOME("Pemasukan"),
    EXPENSE("Pengeluaran")
}

data class KasSummary(
    val totalIncome: Long = 0L,
    val totalExpense: Long = 0L,
    val balance: Long = 0L,
    val totalCount: Int = 0
)

data class CategoryStat(
    val categoryName: String,
    val displayName: String,
    val totalAmount: Long,
    val percentage: Float,
    val isIncome: Boolean,
    val count: Int
)

data class TimeTrendPoint(
    val label: String,
    val income: Long,
    val expense: Long,
    val balance: Long,
    val timestamp: Long
)

data class MonthlyTrendPoint(
    val monthKey: String,
    val label: String,
    val fullLabel: String,
    val income: Long,
    val expense: Long,
    val netChange: Long,
    val endingBalance: Long,
    val transactionCount: Int,
    val timestamp: Long
)

data class ReportAnalyticsData(
    val trendPoints: List<TimeTrendPoint> = emptyList(),
    val monthlyTrendPoints: List<MonthlyTrendPoint> = emptyList(),
    val incomeCategoryStats: List<CategoryStat> = emptyList(),
    val expenseCategoryStats: List<CategoryStat> = emptyList(),
    val highestExpenseCategory: CategoryStat? = null,
    val highestIncomeCategory: CategoryStat? = null,
    val averageTransactionAmount: Long = 0L,
    val totalSavingsRate: Float = 0f
)

class KasViewModel(application: Application) : AndroidViewModel(application) {

    private val idLocale = Locale.forLanguageTag("id-ID")
    private val repository: KasRepository
    val allTransactions: StateFlow<List<TransactionEntity>>

    val searchQuery = MutableStateFlow("")
    val selectedFilterType = MutableStateFlow(FilterType.ALL)
    val selectedCategoryFilter = MutableStateFlow<String?>(null)

    // Summary calculation derived from all transactions
    val summary: StateFlow<KasSummary>

    // Filtered transaction list for display
    val filteredTransactions: StateFlow<List<TransactionEntity>>

    // Category breakdown statistics
    val categoryStats: StateFlow<List<CategoryStat>>

    // Rich analytics & trends
    val analyticsData: StateFlow<ReportAnalyticsData>

    // Student roster & monthly reminder states
    private val _studentRoster = MutableStateFlow(KasReminderManager.getStudentRoster(application))
    val studentRoster: StateFlow<List<String>> = _studentRoster

    private val _reminderSettings = MutableStateFlow(KasReminderManager.getSettings(application))
    val reminderSettings: StateFlow<ReminderSettings> = _reminderSettings

    private val themePrefs = application.getSharedPreferences("kas_theme_prefs", Context.MODE_PRIVATE)
    private val _isDarkTheme = MutableStateFlow(themePrefs.getBoolean("is_dark_theme", false))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme

    private val _driveBackupSettings = MutableStateFlow(DriveBackupManager.getSettings(application))
    val driveBackupSettings: StateFlow<DriveBackupSettings> = _driveBackupSettings

    private val _backupUiState = MutableStateFlow(BackupUiState())
    val backupUiState: StateFlow<BackupUiState> = _backupUiState

    val unpaidSummary: StateFlow<UnpaidSummary>

    init {
        val database = KasDatabase.getDatabase(application, viewModelScope)
        repository = KasRepository(database.transactionDao())

        allTransactions = repository.allTransactions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        unpaidSummary = combine(_studentRoster, allTransactions) { roster, txList ->
            KasReminderManager.computeUnpaidMembersForMonth(roster = roster, transactions = txList)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = KasReminderManager.computeUnpaidMembersForMonth(
                roster = _studentRoster.value,
                transactions = emptyList()
            )
        )

        summary = allTransactions.combine(MutableStateFlow(Unit)) { txList, _ ->
            computeSummaryFor(txList)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = KasSummary()
        )

        filteredTransactions = combine(
            allTransactions,
            searchQuery,
            selectedFilterType,
            selectedCategoryFilter
        ) { list, query, filterType, categoryFilter ->
            list.filter { tx ->
                val resolvedCategory = KasCategory.fromString(tx.category)
                val matchesQuery = if (query.isBlank()) true else {
                    tx.title.contains(query, ignoreCase = true) ||
                        tx.personName.contains(query, ignoreCase = true) ||
                        tx.category.contains(query, ignoreCase = true) ||
                        resolvedCategory.displayName.contains(query, ignoreCase = true) ||
                        tx.notes.contains(query, ignoreCase = true)
                }

                val matchesType = when (filterType) {
                    FilterType.ALL -> true
                    FilterType.INCOME -> tx.isIncome
                    FilterType.EXPENSE -> !tx.isIncome
                }

                val matchesCategory = if (categoryFilter == null) true else {
                    resolvedCategory.name.equals(categoryFilter, ignoreCase = true) ||
                        tx.category.equals(categoryFilter, ignoreCase = true)
                }

                matchesQuery && matchesType && matchesCategory
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        categoryStats = allTransactions.combine(selectedFilterType) { txList, filterType ->
            val targetList = when (filterType) {
                FilterType.ALL -> txList
                FilterType.INCOME -> txList.filter { it.isIncome }
                FilterType.EXPENSE -> txList.filter { !it.isIncome }
            }

            val totalForFilter = targetList.sumOf { it.amount }
            val grouped = targetList.groupBy { KasCategory.fromString(it.category).name }

            grouped.map { (catName, items) ->
                val sum = items.sumOf { it.amount }
                val pct = if (totalForFilter > 0) (sum.toFloat() / totalForFilter) else 0f
                val catEnum = KasCategory.fromString(catName)
                CategoryStat(
                    categoryName = catName,
                    displayName = catEnum.displayName,
                    totalAmount = sum,
                    percentage = pct,
                    isIncome = items.firstOrNull()?.isIncome ?: true,
                    count = items.size
                )
            }.sortedByDescending { it.totalAmount }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        analyticsData = allTransactions.combine(summary) { txList, sum ->
            computeAnalytics(txList, sum)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReportAnalyticsData()
        )
    }

    internal fun computeAnalytics(txList: List<TransactionEntity>, sum: KasSummary): ReportAnalyticsData {
        if (txList.isEmpty()) {
            return ReportAnalyticsData()
        }

        // 1. Category stats for income and expense (normalized via KasCategory)
        val incomeList = txList.filter { it.isIncome }
        val expenseList = txList.filter { !it.isIncome }

        val totalIncome = sum.totalIncome
        val totalExpense = sum.totalExpense

        val incomeStats = incomeList.groupBy { KasCategory.fromString(it.category).name }.map { (cat, items) ->
            val amt = items.sumOf { it.amount }
            val pct = if (totalIncome > 0) amt.toFloat() / totalIncome else 0f
            CategoryStat(
                categoryName = cat,
                displayName = KasCategory.fromString(cat).displayName,
                totalAmount = amt,
                percentage = pct,
                isIncome = true,
                count = items.size
            )
        }.sortedByDescending { it.totalAmount }

        val expenseStats = expenseList.groupBy { KasCategory.fromString(it.category).name }.map { (cat, items) ->
            val amt = items.sumOf { it.amount }
            val pct = if (totalExpense > 0) amt.toFloat() / totalExpense else 0f
            CategoryStat(
                categoryName = cat,
                displayName = KasCategory.fromString(cat).displayName,
                totalAmount = amt,
                percentage = pct,
                isIncome = false,
                count = items.size
            )
        }.sortedByDescending { it.totalAmount }

        // 2. Daily trend points chronologically
        val sortedAsc = txList.sortedBy { it.dateMillis }
        val sdf = SimpleDateFormat("dd MMM", idLocale)
        val dayGrouped = sortedAsc.groupBy { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.dateMillis }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }

        var runningBalance = 0L
        val trendPoints = dayGrouped.map { (dayMillis, dayTxs) ->
            val dayIncome = dayTxs.filter { it.isIncome }.sumOf { it.amount }
            val dayExpense = dayTxs.filter { !it.isIncome }.sumOf { it.amount }
            runningBalance += (dayIncome - dayExpense)
            TimeTrendPoint(
                label = sdf.format(Date(dayMillis)),
                income = dayIncome,
                expense = dayExpense,
                balance = runningBalance,
                timestamp = dayMillis
            )
        }

        // 3. Monthly balance trend points chronologically
        val monthlyTrendPoints = computeMonthlyTrends(sortedAsc)

        val avgAmount = if (txList.isNotEmpty()) txList.sumOf { it.amount } / txList.size else 0L
        val savingsRate = if (totalIncome > 0) ((totalIncome - totalExpense).toFloat() / totalIncome).coerceAtLeast(0f) else 0f

        return ReportAnalyticsData(
            trendPoints = trendPoints,
            monthlyTrendPoints = monthlyTrendPoints,
            incomeCategoryStats = incomeStats,
            expenseCategoryStats = expenseStats,
            highestExpenseCategory = expenseStats.firstOrNull(),
            highestIncomeCategory = incomeStats.firstOrNull(),
            averageTransactionAmount = avgAmount,
            totalSavingsRate = savingsRate
        )
    }

    internal fun computeMonthlyTrends(txList: List<TransactionEntity>): List<MonthlyTrendPoint> {
        if (txList.isEmpty()) return emptyList()

        val sortedAsc = txList.sortedBy { it.dateMillis }
        val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val shortFmt = SimpleDateFormat("MMM ''yy", idLocale)
        val fullFmt = SimpleDateFormat("MMMM yyyy", idLocale)

        val monthGrouped = sortedAsc.groupBy { tx ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = tx.dateMillis
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        }

        var cumulativeBalance = 0L
        return monthGrouped.entries.sortedBy { it.key }.map { (monthStartMillis, monthTxs) ->
            val monthDate = Date(monthStartMillis)
            val mIncome = monthTxs.filter { it.isIncome }.sumOf { it.amount }
            val mExpense = monthTxs.filter { !it.isIncome }.sumOf { it.amount }
            val net = mIncome - mExpense
            cumulativeBalance += net
            MonthlyTrendPoint(
                monthKey = keyFmt.format(monthDate),
                label = shortFmt.format(monthDate),
                fullLabel = fullFmt.format(monthDate),
                income = mIncome,
                expense = mExpense,
                netChange = net,
                endingBalance = cumulativeBalance,
                transactionCount = monthTxs.size,
                timestamp = monthStartMillis
            )
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setFilterType(type: FilterType) {
        selectedFilterType.value = type
        val currentCat = selectedCategoryFilter.value
        if (currentCat != null) {
            val catEnum = KasCategory.fromString(currentCat)
            val compatible = when (type) {
                FilterType.ALL -> true
                FilterType.INCOME -> catEnum.defaultType == TransactionType.PEMASUKAN
                FilterType.EXPENSE -> catEnum.defaultType == TransactionType.PENGELUARAN
            }
            if (!compatible) {
                selectedCategoryFilter.value = null
            }
        }
    }

    fun setCategoryFilter(category: String?) {
        selectedCategoryFilter.value = category
    }

    fun resetFilters() {
        searchQuery.value = ""
        selectedFilterType.value = FilterType.ALL
        selectedCategoryFilter.value = null
    }

    fun setDarkTheme(dark: Boolean) {
        _isDarkTheme.value = dark
        themePrefs.edit().putBoolean("is_dark_theme", dark).apply()
    }

    fun toggleTheme() {
        setDarkTheme(!_isDarkTheme.value)
    }

    fun addTransaction(
        type: TransactionType,
        title: String,
        amount: Long,
        category: String,
        dateMillis: Long = System.currentTimeMillis(),
        personName: String = "",
        notes: String = ""
    ) {
        viewModelScope.launch {
            val entity = TransactionEntity(
                type = type.name,
                title = title.trim(),
                amount = amount,
                category = category,
                dateMillis = dateMillis,
                personName = personName.trim(),
                notes = notes.trim()
            )
            repository.insert(entity)
            DriveBackupManager.scheduleDebouncedBackupOnDataChange(getApplication())
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.update(transaction)
            DriveBackupManager.scheduleDebouncedBackupOnDataChange(getApplication())
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.delete(transaction)
            DriveBackupManager.scheduleDebouncedBackupOnDataChange(getApplication())
        }
    }

    fun computeSummaryFor(txList: List<TransactionEntity>): KasSummary {
        var income = 0L
        var expense = 0L
        for (tx in txList) {
            if (tx.isIncome) {
                income += tx.amount
            } else {
                expense += tx.amount
            }
        }
        return KasSummary(
            totalIncome = income,
            totalExpense = expense,
            balance = income - expense,
            totalCount = txList.size
        )
    }

    fun getCurrentMonthTransactions(
        txList: List<TransactionEntity> = allTransactions.value,
        nowMillis: Long = System.currentTimeMillis()
    ): List<TransactionEntity> {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val targetYear = nowCal.get(Calendar.YEAR)
        val targetMonth = nowCal.get(Calendar.MONTH)
        val txCal = Calendar.getInstance()
        return txList.filter { tx ->
            txCal.timeInMillis = tx.dateMillis
            txCal.get(Calendar.YEAR) == targetYear && txCal.get(Calendar.MONTH) == targetMonth
        }
    }

    fun addStudentsToRoster(rawNames: String): Int {
        val entries = KasReminderManager.parseStudentNames(rawNames)
        if (entries.isEmpty()) return 0
        val current = _studentRoster.value.toMutableList()
        var added = 0
        for (entry in entries) {
            if (current.none { it.equals(entry, ignoreCase = true) }) {
                current.add(entry)
                added++
            }
        }
        if (added > 0) {
            KasReminderManager.saveStudentRoster(getApplication(), current)
            _studentRoster.value = KasReminderManager.getStudentRoster(getApplication())
            DriveBackupManager.scheduleDebouncedBackupOnDataChange(getApplication())
        }
        return added
    }

    fun removeStudentFromRoster(name: String) {
        val updated = _studentRoster.value.filterNot { it.equals(name, ignoreCase = true) }
        KasReminderManager.saveStudentRoster(getApplication(), updated)
        _studentRoster.value = updated
        DriveBackupManager.scheduleDebouncedBackupOnDataChange(getApplication())
    }

    fun markStudentPaidThisMonth(
        studentName: String,
        amount: Long = _reminderSettings.value.monthlyTargetAmount
    ) {
        val monthLabel = SimpleDateFormat("MMMM yyyy", idLocale).format(Date())
        addTransaction(
            type = TransactionType.PEMASUKAN,
            title = "Iuran Kas $monthLabel - $studentName",
            amount = amount,
            category = KasCategory.IURAN_BULANAN.name,
            personName = studentName.trim(),
            notes = "Dicatat dari Anggota & Tagihan Kas"
        )
    }

    fun updateReminderSettings(newSettings: ReminderSettings) {
        val app = getApplication<Application>()
        KasReminderManager.saveSettings(app, newSettings)
        _reminderSettings.value = newSettings
        DriveBackupManager.scheduleDebouncedBackupOnDataChange(app)
    }

    fun generateReminderMessage(): String {
        val s = _reminderSettings.value
        return KasReminderManager.formatReminderShareMessage(
            unpaidSummary = unpaidSummary.value,
            targetAmount = s.monthlyTargetAmount,
            customNote = s.customNote
        )
    }

    fun recordQuickBatchIuran(
        amountPerStudent: Long,
        studentNames: List<String>,
        weekTitle: String,
        notes: String
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val txList = studentNames.mapIndexed { index, name ->
                TransactionEntity(
                    type = TransactionType.PEMASUKAN.name,
                    title = "$weekTitle - $name",
                    amount = amountPerStudent,
                    category = KasCategory.IURAN_MINGGUAN.name,
                    dateMillis = now + index,
                    personName = name.trim(),
                    notes = notes
                )
            }
            repository.insertAll(txList)
            DriveBackupManager.scheduleDebouncedBackupOnDataChange(getApplication())
        }
    }

    // --- Google Drive Backup & Restore Actions ---

    fun updateDriveBackupSettings(newSettings: DriveBackupSettings) {
        val app = getApplication<Application>()
        DriveBackupManager.saveSettings(app, newSettings)
        _driveBackupSettings.value = newSettings
        DriveBackupManager.syncWorkSchedule(app, newSettings)
    }

    fun handleGoogleDriveConnected(
        accessToken: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _backupUiState.value = _backupUiState.value.copy(
                isBusy = true,
                busyMessage = "Memeriksa akun & backup di Google Drive...",
                needsReauth = false
            )
            val checkRes = DriveBackupManager.checkRemoteBackup(app, accessToken)
            checkRes.onSuccess { remoteMeta ->
                val refreshed = DriveBackupManager.getSettings(app)
                val updated = refreshed.copy(
                    autoBackupEnabled = true,
                    lastBackupStatus = if (remoteMeta != null) {
                        "Terhubung • Backup ditemukan di Google Drive"
                    } else {
                        "Terhubung • Siap mencadangkan otomatis"
                    }
                )
                DriveBackupManager.saveSettings(app, updated)
                DriveBackupManager.syncWorkSchedule(app, updated)
                _driveBackupSettings.value = updated
                _backupUiState.value = BackupUiState(isBusy = false, remoteBackup = remoteMeta)

                val localTxCount = allTransactions.value.size
                if (remoteMeta == null && localTxCount > 0) {
                    // First backup on this Drive account with existing local data
                    backupToDriveWithToken(accessToken, onResult)
                } else {
                    val msg = if (remoteMeta != null) {
                        "Google Drive terhubung (${updated.connectedEmail ?: "Akun Google"}). Backup lama ditemukan (${remoteMeta.transactionCount ?: 0} transaksi)."
                    } else {
                        "Google Drive terhubung! Backup otomatis telah diaktifkan."
                    }
                    onResult(true, msg)
                }
            }.onFailure { err ->
                _driveBackupSettings.value = DriveBackupManager.getSettings(app)
                _backupUiState.value = _backupUiState.value.copy(
                    isBusy = false,
                    busyMessage = null,
                    needsReauth = err is DriveAuthExpiredException
                )
                onResult(false, err.localizedMessage ?: "Gagal memeriksa Google Drive.")
            }
        }
    }

    fun backupToDriveWithToken(
        accessToken: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _backupUiState.value = _backupUiState.value.copy(
                isBusy = true,
                busyMessage = "Mencadangkan Room Database ke Google Drive...",
                needsReauth = false
            )
            val result = DriveBackupManager.performBackupNow(app, accessToken)
            val latestSettings = DriveBackupManager.getSettings(app)
            _driveBackupSettings.value = latestSettings
            result.onSuccess { meta ->
                _backupUiState.value = BackupUiState(
                    isBusy = false,
                    remoteBackup = meta,
                    needsReauth = false
                )
                onResult(
                    true,
                    "Backup berhasil! ${meta.transactionCount ?: latestSettings.lastBackedUpTransactionCount} transaksi tersimpan di Google Drive."
                )
            }.onFailure { err ->
                _backupUiState.value = _backupUiState.value.copy(
                    isBusy = false,
                    busyMessage = null,
                    needsReauth = err is DriveAuthExpiredException
                )
                onResult(false, err.localizedMessage ?: "Gagal mencadangkan data ke Google Drive.")
            }
        }
    }

    fun checkRemoteDriveBackupWithToken(
        accessToken: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _backupUiState.value = _backupUiState.value.copy(
                isBusy = true,
                busyMessage = "Memeriksa file backup di Google Drive...",
                needsReauth = false
            )
            val result = DriveBackupManager.checkRemoteBackup(app, accessToken)
            _driveBackupSettings.value = DriveBackupManager.getSettings(app)
            result.onSuccess { meta ->
                _backupUiState.value = BackupUiState(
                    isBusy = false,
                    remoteBackup = meta,
                    needsReauth = false
                )
                val msg = if (meta != null) {
                    "Backup ditemukan: ${meta.transactionCount ?: 0} transaksi."
                } else {
                    "Belum ada file backup Kas Kelas di akun Google Drive ini."
                }
                onResult(true, msg)
            }.onFailure { err ->
                _backupUiState.value = _backupUiState.value.copy(
                    isBusy = false,
                    busyMessage = null,
                    needsReauth = err is DriveAuthExpiredException
                )
                onResult(false, err.localizedMessage ?: "Gagal memeriksa Google Drive.")
            }
        }
    }

    fun restoreFromDriveWithToken(
        accessToken: String,
        mode: RestoreMode,
        onResult: (Boolean, String) -> Unit
    ) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _backupUiState.value = _backupUiState.value.copy(
                isBusy = true,
                busyMessage = "Memulihkan data dari Google Drive...",
                needsReauth = false
            )
            val result = DriveBackupManager.restoreFromDrive(app, accessToken, mode)
            _driveBackupSettings.value = DriveBackupManager.getSettings(app)
            _studentRoster.value = KasReminderManager.getStudentRoster(app)
            _reminderSettings.value = KasReminderManager.getSettings(app)
            result.onSuccess { summary ->
                _backupUiState.value = _backupUiState.value.copy(
                    isBusy = false,
                    busyMessage = null,
                    needsReauth = false
                )
                val msg = if (mode == RestoreMode.REPLACE_ALL) {
                    "Pemulihan berhasil! ${summary.restoredTransactionsCount} transaksi & ${summary.restoredRosterCount} anggota dipulihkan."
                } else {
                    "Berhasil menggabungkan ${summary.restoredTransactionsCount} transaksi baru (${summary.skippedDuplicateCount} duplikat dilewati)."
                }
                onResult(true, msg)
            }.onFailure { err ->
                _backupUiState.value = _backupUiState.value.copy(
                    isBusy = false,
                    busyMessage = null,
                    needsReauth = err is DriveAuthExpiredException
                )
                onResult(false, err.localizedMessage ?: "Gagal memulihkan data dari Google Drive.")
            }
        }
    }

    fun disconnectDriveAccount(onComplete: () -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            DriveBackupManager.disconnectAccount(app)
            _driveBackupSettings.value = DriveBackupManager.getSettings(app)
            _backupUiState.value = BackupUiState()
            onComplete()
        }
    }

    fun exportLocalBackupJson(onResult: (Result<String>) -> Unit) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _backupUiState.value = _backupUiState.value.copy(
                isBusy = true,
                busyMessage = "Menyiapkan file backup JSON lokal..."
            )
            val result = DriveBackupManager.exportLocalBackupJson(app)
            _backupUiState.value = _backupUiState.value.copy(
                isBusy = false,
                busyMessage = null
            )
            onResult(result)
        }
    }

    fun restoreFromLocalJson(
        rawJson: String,
        mode: RestoreMode,
        onResult: (Boolean, String) -> Unit
    ) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _backupUiState.value = _backupUiState.value.copy(
                isBusy = true,
                busyMessage = "Memulihkan data dari file JSON..."
            )
            val result = DriveBackupManager.restoreFromLocalJson(app, rawJson, mode)
            _studentRoster.value = KasReminderManager.getStudentRoster(app)
            _reminderSettings.value = KasReminderManager.getSettings(app)
            _backupUiState.value = _backupUiState.value.copy(
                isBusy = false,
                busyMessage = null
            )
            result.onSuccess { summary ->
                val msg = if (mode == RestoreMode.REPLACE_ALL) {
                    "Pemulihan file berhasil! ${summary.restoredTransactionsCount} transaksi & ${summary.restoredRosterCount} anggota dipulihkan."
                } else {
                    "Berhasil menggabungkan ${summary.restoredTransactionsCount} transaksi dari file (${summary.skippedDuplicateCount} duplikat dilewati)."
                }
                onResult(true, msg)
            }.onFailure { err ->
                onResult(false, err.localizedMessage ?: "File backup JSON tidak valid.")
            }
        }
    }

    data class PaidPayerSummary(
        val name: String,
        val totalPaid: Long,
        val paymentCount: Int
    )

    fun getPaidPayers(transactions: List<TransactionEntity> = allTransactions.value): List<PaidPayerSummary> {
        return transactions
            .filter { it.isIncome && it.personName.isNotBlank() }
            .groupBy { it.personName.trim().lowercase(idLocale) }
            .values
            .map { txs ->
                PaidPayerSummary(
                    name = txs.first().personName.trim(),
                    totalPaid = txs.sumOf { it.amount },
                    paymentCount = txs.size
                )
            }
            .sortedBy { it.name.lowercase(idLocale) }
    }

    fun getHistoricalPayerNames(transactions: List<TransactionEntity> = allTransactions.value): List<String> {
        return transactions
            .asSequence()
            .filter { it.isIncome && it.personName.isNotBlank() }
            .map { it.personName.trim() }
            .distinctBy { it.lowercase(idLocale) }
            .sortedBy { it.lowercase(idLocale) }
            .toList()
    }

    fun generateReportText(): String {
        val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", idLocale)
        return formatShareReportText(
            currentSummary = summary.value,
            transactions = allTransactions.value,
            analytics = analyticsData.value,
            dateStr = sdf.format(Date())
        )
    }

    internal fun formatShareReportText(
        currentSummary: KasSummary,
        transactions: List<TransactionEntity>,
        analytics: ReportAnalyticsData,
        dateStr: String
    ): String {
        val builder = StringBuilder()
        builder.append("📊 *LAPORAN KEUANGAN KAS KELAS*\n")
        builder.append("📅 Per Tanggal: $dateStr\n")
        builder.append("═══════════════════════\n")
        builder.append("💰 *Saldo Kas Sekarang:* ${formatRupiah(currentSummary.balance)}\n")
        builder.append("📥 *Total Pemasukan:* ${formatRupiah(currentSummary.totalIncome)}\n")
        builder.append("📤 *Total Pengeluaran:* ${formatRupiah(currentSummary.totalExpense)}\n")
        builder.append("═══════════════════════\n\n")

        val paidPeople = getPaidPayers(transactions)

        builder.append("✅ *Daftar Yang Telah Bayar (${paidPeople.size} Orang):*\n")
        if (paidPeople.isEmpty()) {
            builder.append(" (Belum ada nama pembayar yang tercatat)\n\n")
        } else {
            paidPeople.forEachIndexed { index, payer ->
                val countSuffix = if (payer.paymentCount > 1) " (${payer.paymentCount} kali)" else ""
                builder.append(" ${index + 1}. ${payer.name} — ${formatRupiah(payer.totalPaid)}$countSuffix\n")
            }
            builder.append("\n")
        }

        if (analytics.expenseCategoryStats.isNotEmpty()) {
            builder.append("📌 *Ringkasan Pengeluaran Terbesar:*\n")
            analytics.expenseCategoryStats.take(4).forEach {
                val pct = (it.percentage * 100).toInt()
                builder.append(" • ${it.displayName}: ${formatRupiah(it.totalAmount)} ($pct%)\n")
            }
            builder.append("\n")
        }

        if (analytics.incomeCategoryStats.isNotEmpty()) {
            builder.append("📌 *Sumber Pemasukan Kas:*\n")
            analytics.incomeCategoryStats.take(4).forEach {
                val pct = (it.percentage * 100).toInt()
                builder.append(" • ${it.displayName}: ${formatRupiah(it.totalAmount)} ($pct%)\n")
            }
            builder.append("\n")
        }

        builder.append("═══════════════════════\n")
        builder.append("📝 *Catatan Transaksi Terakhir:*\n")
        val recent = transactions.take(6)
        if (recent.isEmpty()) {
            builder.append("(Belum ada transaksi)\n")
        } else {
            for (tx in recent) {
                val symbol = if (tx.isIncome) "🟢 [+]" else "🔴 [-]"
                val catName = KasCategory.fromString(tx.category).displayName
                val person = if (tx.personName.isNotBlank()) " • ${tx.personName}" else ""
                builder.append("$symbol ${tx.title} [$catName]$person: ${tx.formattedAmount}\n")
            }
        }
        builder.append("═══════════════════════\n")
        builder.append("🎓 Dikelola Transparan & Real-Time via Aplikasi Kas Kelas")
        return builder.toString()
    }
}
