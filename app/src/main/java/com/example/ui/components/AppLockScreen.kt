package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest

/**
 * A simple, dependency-free 4-digit PIN app lock. Chosen over
 * fingerprint/BiometricPrompt for this pass to avoid adding a new Gradle
 * dependency and converting MainActivity to a FragmentActivity — the PIN is
 * salted + SHA-256 hashed and only the hash is ever stored, never the PIN
 * itself.
 */
enum class AppLockMode { UNLOCK, SETUP, CONFIRM_TO_DISABLE, CHANGE }

object AppLockManager {
    private const val PREFS = "vantage_prefs"
    private const val KEY_ENABLED = "app_lock_enabled"
    private const val KEY_HASH = "app_lock_pin_hash"
    private const val KEY_SALT = "app_lock_pin_salt"
    const val PIN_LENGTH = 4

    fun isEnabled(context: android.content.Context): Boolean {
        return context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    // One-shot flag: set right before launching an external picker/scanner
    // activity (document scanner, gallery) that momentarily takes MainActivity
    // through onStop. Without this, App Lock would re-lock on every such trip
    // and the returning recomposition would tear down whatever screen/state
    // (e.g. the Add Transaction sheet mid-scan) was showing before it left.
    // Consumed (and cleared) by MainActivity.onStop() the very next time it
    // fires, so a real backgrounding right after still locks normally.
    @Volatile
    var suppressNextLock: Boolean = false

    private fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + pin).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setPin(context: android.content.Context, pin: String) {
        val salt = java.util.UUID.randomUUID().toString()
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_HASH, hash(pin, salt))
            .putBoolean(KEY_ENABLED, true)
            .apply()
    }

    fun verifyPin(context: android.content.Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val storedHash = prefs.getString(KEY_HASH, null) ?: return false
        return hash(pin, salt) == storedHash
    }

    fun disable(context: android.content.Context) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .apply()
    }
}

@Composable
fun AppLockScreen(
    mode: AppLockMode,
    onUnlocked: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var stage by remember { mutableStateOf(if (mode == AppLockMode.SETUP) 0 else 1) } // 0 = enter new PIN, 1 = confirm/verify
    var firstPin by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val title = when {
        mode == AppLockMode.SETUP && stage == 0 -> "Create a PIN"
        mode == AppLockMode.SETUP && stage == 1 -> "Confirm your PIN"
        mode == AppLockMode.CONFIRM_TO_DISABLE -> "Enter PIN to disable lock"
        mode == AppLockMode.CHANGE -> "Enter current PIN"
        else -> "Enter your PIN"
    }

    fun handleComplete(entered: String) {
        when (mode) {
            AppLockMode.SETUP -> {
                if (stage == 0) {
                    firstPin = entered
                    pin = ""
                    stage = 1
                } else {
                    if (entered == firstPin) {
                        AppLockManager.setPin(context, entered)
                        onUnlocked()
                    } else {
                        error = "PINs didn't match — try again"
                        pin = ""
                        firstPin = ""
                        stage = 0
                    }
                }
            }
            AppLockMode.UNLOCK, AppLockMode.CONFIRM_TO_DISABLE, AppLockMode.CHANGE -> {
                if (AppLockManager.verifyPin(context, entered)) {
                    if (mode == AppLockMode.CONFIRM_TO_DISABLE) AppLockManager.disable(context)
                    onUnlocked()
                } else {
                    error = "Incorrect PIN"
                    pin = ""
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(AppLockManager.PIN_LENGTH) { index ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (key.isNotEmpty()) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .then(
                                if (key.isNotEmpty()) {
                                    Modifier.clickable {
                                        error = ""
                                        if (key == "⌫") {
                                            if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        } else if (pin.length < AppLockManager.PIN_LENGTH) {
                                            pin += key
                                            if (pin.length == AppLockManager.PIN_LENGTH) {
                                                val entered = pin
                                                handleComplete(entered)
                                            }
                                        }
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "⌫") {
                            Icon(Icons.Filled.Backspace, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (key.isNotEmpty()) {
                            Text(key, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (onCancel != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}
