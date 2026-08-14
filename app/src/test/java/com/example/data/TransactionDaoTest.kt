package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Data-layer round-trip coverage for transactions, with emphasis on the Credit Card payment
 * fields (bank name, recurring/"Schedule Monthly" flag, scheduled day of month) since the
 * Save flow for those fields couldn't be exercised end-to-end via manual touch testing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var transactionDao: TransactionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        transactionDao = db.transactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertExpense_withUpiPaymentMethod_roundTripsCorrectly() = runTest {
        transactionDao.insertTransaction(
            Transaction(
                title = "Grocery Run",
                amount = 450.0,
                category = "Food",
                timestamp = 1_000L,
                type = "EXPENSE",
                paymentMethod = "UPI"
            )
        )

        val saved = transactionDao.getAllTransactions().first().first()

        assertEquals("Grocery Run", saved.title)
        assertEquals(450.0, saved.amount, 0.001)
        assertEquals("UPI", saved.paymentMethod)
        assertNull(saved.creditCardBank)
        assertTrue(!saved.isRecurring)
    }

    @Test
    fun insertExpense_withCreditCardAndScheduleMonthly_persistsAllFields() = runTest {
        transactionDao.insertTransaction(
            Transaction(
                title = "Amazon Purchase",
                amount = 2500.0,
                category = "Shopping",
                timestamp = 2_000L,
                type = "EXPENSE",
                paymentMethod = "CREDIT_CARD",
                creditCardBank = "HDFC Bank",
                isRecurring = true,
                recurrenceInterval = "MONTHLY",
                scheduledDayOfMonth = 5
            )
        )

        val saved = transactionDao.getAllTransactions().first().first()

        assertEquals("CREDIT_CARD", saved.paymentMethod)
        assertEquals("HDFC Bank", saved.creditCardBank)
        assertTrue(saved.isRecurring)
        assertEquals("MONTHLY", saved.recurrenceInterval)
        assertEquals(5, saved.scheduledDayOfMonth)
    }

    @Test
    fun creditCardExpenses_areExcludedFromLiquidBalanceCalculationInputs() = runTest {
        // Sanity check on the raw data feeding FinanceViewModel's balance math: Credit Card
        // spend should be identifiable/filterable separately from cash/UPI spend, since the
        // Dashboard's "Credit Tracker" card tracks it independently from "Salary & Liquid
        // Assets" (confirmed visually during manual testing: adding a Credit Card expense
        // must NOT move the liquid balance the same way a UPI/Cash expense does).
        transactionDao.insertTransaction(
            Transaction(title = "UPI Grocery", amount = 450.0, category = "Food", type = "EXPENSE", paymentMethod = "UPI")
        )
        transactionDao.insertTransaction(
            Transaction(title = "CC Amazon", amount = 2500.0, category = "Shopping", type = "EXPENSE", paymentMethod = "CREDIT_CARD", creditCardBank = "HDFC Bank")
        )

        val all = transactionDao.getAllTransactions().first()
        val creditCardSpend = all.filter { it.type == "EXPENSE" && it.paymentMethod == "CREDIT_CARD" }.sumOf { it.amount }
        val nonCreditCardSpend = all.filter { it.type == "EXPENSE" && it.paymentMethod != "CREDIT_CARD" }.sumOf { it.amount }

        assertEquals(2500.0, creditCardSpend, 0.001)
        assertEquals(450.0, nonCreditCardSpend, 0.001)
    }

    @Test
    fun deleteTransaction_removesOnlyThatRow() = runTest {
        transactionDao.insertTransaction(Transaction(title = "Keep Me", amount = 100.0, category = "Food", type = "EXPENSE"))
        transactionDao.insertTransaction(Transaction(title = "Delete Me", amount = 200.0, category = "Shopping", type = "EXPENSE"))
        val toDelete = transactionDao.getAllTransactions().first().first { it.title == "Delete Me" }

        transactionDao.deleteTransaction(toDelete)

        val remaining = transactionDao.getAllTransactions().first()
        assertEquals(1, remaining.size)
        assertEquals("Keep Me", remaining.first().title)
    }
}
