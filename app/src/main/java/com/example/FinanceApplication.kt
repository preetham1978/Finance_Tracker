package com.example

import android.app.Application
import android.content.Context
import com.example.data.AppDatabase
import com.example.data.TransactionRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

class FinanceApplication : Application() {

    companion object {
        // Static app Context so singletons like RetrofitClient (GeminiApiService.kt) can read
        // the package name / signing certificate without threading Context through every call
        // site. Set once in onCreate(), read-only afterward.
        lateinit var appContext: Context
            private set
    }

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
        appContext = applicationContext
        FirebaseApp.initializeApp(this)
    }
}
