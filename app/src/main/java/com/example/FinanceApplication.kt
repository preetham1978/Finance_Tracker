package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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

        const val BILL_REMINDER_CHANNEL_ID = "bill_reminders"
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
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BILL_REMINDER_CHANNEL_ID,
                "Bill & EMI Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for recurring bills and EMIs coming due"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
