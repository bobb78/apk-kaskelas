package com.example.data

import kotlinx.coroutines.flow.Flow

class KasRepository(private val transactionDao: TransactionDao) {

    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()

    fun getTransactionsByType(type: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByType(type)
    }

    suspend fun getById(id: Long): TransactionEntity? {
        return transactionDao.getTransactionById(id)
    }

    fun searchTransactions(query: String): Flow<List<TransactionEntity>> {
        return transactionDao.searchTransactions(query)
    }

    suspend fun insert(transaction: TransactionEntity): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun insertAll(transactions: List<TransactionEntity>) {
        transactionDao.insertTransactions(transactions)
    }

    suspend fun update(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun delete(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun deleteById(id: Long) {
        transactionDao.deleteTransactionById(id)
    }

    suspend fun deleteAll() {
        transactionDao.deleteAllTransactions()
    }
}
