package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.TransactionRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

class FinanceApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { 
        TransactionRepository(
            database.transactionDao(),
            database.goalDao(),
            database.budgetDao(),
            database.categoryDao()
        ) 
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
