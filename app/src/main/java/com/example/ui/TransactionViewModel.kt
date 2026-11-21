package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.DriveBackupManager
import com.example.data.KasCategory
import com.example.data.KasDatabase
import com.example.data.KasRepository
import com.example.data.TransactionEntity
import com.example.data.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransactionDashboardUiState(
    val isLoading: Boolean = true,
    val transactions: List<TransactionEntity> = emptyList(),
    val totalBalance: Long = 0L,
    val totalIncome: Long = 0L,
    val totalExpense: Long = 0L,
    val transactionCount: Int = 0
)

/**
 * ViewModel responsible for managing the state of the Room-backed transaction list
 * and exposing the real-time total balance calculation for the main dashboard.
 */
class TransactionViewModel(
    application: Application,
    private val repository: KasRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        repository = KasRepository(KasDatabase.getDatabase(application).transactionDao())
    )

    val searchQuery = MutableStateFlow("")
    val selectedFilterType = MutableStateFlow(FilterType.ALL)
    val selectedCategoryFilter = MutableStateFlow<String?>(null)

    private val _hasLoadedFromRoom = MutableStateFlow(false)

    val transactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .map { list ->
            _hasLoadedFromRoom.value = true
            list
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val summary: StateFlow<KasSummary> = transactions
        .map { computeSummary(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = KasSummary()
        )

    val totalBalance: StateFlow<Long> = summary
        .map { it.balance }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0L
        )

    val totalIncome: StateFlow<Long> = summary
        .map { it.totalIncome }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0L
        )

    val totalExpense: StateFlow<Long> = summary
        .map { it.totalExpense }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0L
        )

    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        transactions,
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
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val uiState: StateFlow<TransactionDashboardUiState> = combine(
        _hasLoadedFromRoom,
        filteredTransactions,
        summary
    ) { loaded, visibleTransactions, currentSummary ->
        TransactionDashboardUiState(
            isLoading = !loaded,
            transactions = visibleTransactions,
            totalBalance = currentSummary.balance,
            totalIncome = currentSummary.totalIncome,
            totalExpense = currentSummary.totalExpense,
            transactionCount = currentSummary.totalCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TransactionDashboardUiState()
    )

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

    fun calculateTotalBalance(txList: List<TransactionEntity>): Long {
        return computeSummary(txList).balance
    }

    fun computeSummary(txList: List<TransactionEntity>): KasSummary {
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
}
