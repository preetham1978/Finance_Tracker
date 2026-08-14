package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.data.Transaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object CloudSyncManager {
    private const val TAG = "CloudSyncManager"
    private const val PREFS_NAME = "vantage_cloud_sync_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"

    /**
     * Sanitizes a Gmail ID so that it's a valid path identifier for Google Firebase Cloud database keys.
     */
    fun sanitizeEmail(email: String): String {
        return email.trim().lowercase()
            .replace("@", "_at_")
            .replace(".", "_dot_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")
    }

    fun isLoggedIn(context: Context): Boolean {
        return FirebaseAuth.getInstance().currentUser != null
    }

    fun getUserEmail(context: Context): String? {
        return FirebaseAuth.getInstance().currentUser?.email
    }

    fun getUserName(context: Context): String? {
        return FirebaseAuth.getInstance().currentUser?.displayName
    }

    fun getLastSyncTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    fun setLoggedIn(context: Context, email: String, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_EMAIL, email.trim())
            .putString(KEY_USER_NAME, name.trim())
            .commit() // Use commit for critical auth state
        Log.d(TAG, "User logged in: $email")
    }

    fun setLoggedOut(context: Context) {
        FirebaseAuth.getInstance().signOut()
        Log.d(TAG, "User logged out.")
    }

    fun setLastSyncTime(context: Context, timestamp: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_SYNC_TIME, timestamp)
            .apply()
    }

    /**
     * Automatically backs up all current transactions to Google Cloud if the user is authenticated.
     */
    suspend fun backupToCloud(context: Context, transactions: List<Transaction>): Boolean = withContext(Dispatchers.IO) {
        if (!isLoggedIn(context)) {
            Log.d(TAG, "Skipping cloud backup: User is not signed in.")
            return@withContext false
        }

        val email = getUserEmail(context) ?: return@withContext false
        val sanitizedId = sanitizeEmail(email)

        try {
            val firestore = FirebaseFirestore.getInstance()
            val userTransactionsRef = firestore.collection("users").document(sanitizedId).collection("transactions")
            
            // Get currently existing cloud transaction IDs
            val snapshot = userTransactionsRef.get().await()
            val existingIds = snapshot.documents.map { it.id }
            
            // We use batch writes to be efficient and atomic
            val batch = firestore.batch()
            val localIds = transactions.map { it.id.toString() }
            
            // 1. Delete transactions from Firestore that are no longer in the local Room DB
            for (id in existingIds) {
                if (id !in localIds) {
                    batch.delete(userTransactionsRef.document(id))
                }
            }
            
            // 2. Set/Update all current transactions
            for (tx in transactions) {
                batch.set(userTransactionsRef.document(tx.id.toString()), tx)
            }
            
            // Commit the batch of changes to Cloud Firestore
            batch.commit().await()
            
            setLastSyncTime(context, System.currentTimeMillis())
            Log.d(TAG, "Successfully backed up ${transactions.size} transactions to Google Cloud Firestore.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error performing cloud backup: ${e.message}", e)
            false
        }
    }

    /**
     * Downloads and retrieves backed up transactions from Google Cloud for the signed-in user.
     */
    suspend fun restoreFromCloud(context: Context): List<Transaction>? = withContext(Dispatchers.IO) {
        if (!isLoggedIn(context)) {
            Log.d(TAG, "Skipping cloud restore: User is not signed in.")
            return@withContext null
        }

        val email = getUserEmail(context) ?: return@withContext null
        val sanitizedId = sanitizeEmail(email)

        try {
            val firestore = FirebaseFirestore.getInstance()
            val userTransactionsRef = firestore.collection("users").document(sanitizedId).collection("transactions")
            
            val snapshot = userTransactionsRef.get().await()
            val transactions = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Transaction::class.java)
            }
            
            setLastSyncTime(context, System.currentTimeMillis())
            Log.d(TAG, "Successfully downloaded ${transactions.size} transactions from Google Cloud Firestore.")
            transactions
        } catch (e: Exception) {
            Log.e(TAG, "Error performing cloud restore: ${e.message}", e)
            null
        }
    }
}
