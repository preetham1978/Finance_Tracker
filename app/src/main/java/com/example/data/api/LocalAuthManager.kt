package com.example.data.api

import android.content.Context
import android.content.SharedPreferences

object LocalAuthManager {
    private const val PREFS_NAME = "local_auth_prefs"
    private const val KEY_PREFIX_USER = "user_"
    private const val KEY_CURRENT_USER = "current_user_email"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun signUp(context: Context, email: String, password: String): Pair<Boolean, String> {
        val prefs = getPrefs(context)
        val userKey = KEY_PREFIX_USER + email.lowercase().trim()
        
        if (prefs.contains(userKey)) {
            return Pair(false, "User already exists with this email.")
        }
        
        prefs.edit()
            .putString(userKey, password)
            .putString(KEY_CURRENT_USER, email.lowercase().trim())
            .apply()
            
        return Pair(true, "Sign up successful.")
    }

    fun signIn(context: Context, email: String, password: String): Pair<Boolean, String> {
        val prefs = getPrefs(context)
        val userKey = KEY_PREFIX_USER + email.lowercase().trim()
        
        if (!prefs.contains(userKey)) {
            return Pair(false, "No account found with this email. Please sign up.")
        }
        
        val storedPassword = prefs.getString(userKey, null)
        if (storedPassword != password) {
            return Pair(false, "Invalid password.")
        }
        
        prefs.edit().putString(KEY_CURRENT_USER, email.lowercase().trim()).apply()
        return Pair(true, "Sign in successful.")
    }
    
    fun getCurrentUser(context: Context): String? {
        return getPrefs(context).getString(KEY_CURRENT_USER, null)
    }
    
    fun signOut(context: Context) {
        getPrefs(context).edit().remove(KEY_CURRENT_USER).apply()
    }
}
