package com.example.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val goalDao: GoalDao,
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao,
    private val holdingDao: HoldingDao
) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allGoals: Flow<List<Goal>> = goalDao.getAllGoals()
    val allBudgets: Flow<List<Budget>> = budgetDao.getAllBudgets()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allHoldings: Flow<List<Holding>> = holdingDao.getAllHoldings()

    suspend fun insertCategory(category: Category) {
        categoryDao.insertCategory(category)
    }

    suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category)
    }

    suspend fun insert(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
    }

    suspend fun delete(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
    }


    suspend fun insertGoal(goal: Goal) {
        goalDao.insertGoal(goal)
    }

    suspend fun updateGoal(goal: Goal) {
        goalDao.updateGoal(goal)
    }

    suspend fun deleteGoal(goal: Goal) {
        goalDao.deleteGoal(goal)
    }

    suspend fun insertBudget(budget: Budget) {
        budgetDao.insertBudget(budget)
    }

    suspend fun deleteBudget(budget: Budget) {
        budgetDao.deleteBudget(budget)
    }

    suspend fun insertHolding(holding: Holding) {
        holdingDao.insertHolding(holding)
    }

    suspend fun updateHolding(holding: Holding) {
        holdingDao.updateHolding(holding)
    }

    suspend fun deleteHolding(holding: Holding) {
        holdingDao.deleteHolding(holding)
    }

    fun getTransactionById(id: Int): Flow<Transaction?> {
        return transactionDao.getTransactionById(id)
    }

    fun getTransactionsByType(type: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByType(type)
    }

    suspend fun deleteAll() {
        transactionDao.deleteAllTransactions()
    }
}
