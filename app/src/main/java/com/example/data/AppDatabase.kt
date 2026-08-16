package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// version bumped 5 -> 6 to add the `holdings` table (investment portfolio
// tracker). fallbackToDestructiveMigration() below means this recreates the
// local database from scratch on first run after the update — any locally
// stored transactions/goals/budgets/categories will be cleared. Fine for a
// pre-release debug build, but call this out before rebuilding.
@Database(entities = [Transaction::class, Goal::class, Budget::class, Category::class, Holding::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun goalDao(): GoalDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao
    abstract fun holdingDao(): HoldingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_tracker_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
