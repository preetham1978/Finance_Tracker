package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.ui.components.AppLockManager
import com.example.ui.components.AppLockMode
import com.example.ui.components.AppLockScreen
import com.example.ui.components.LoginScreen
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.ViewModelProvider
import com.example.ui.theme.FinanceTrackerTheme
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.viewmodel.FinanceViewModelFactory

class MainActivity : ComponentActivity() {

    // Backs the Share-target flow: long-press a bank SMS -> Share -> Vantage
    // Finance lands the shared text here, and DashboardScreen watches this
    // state to auto-open the Add Transaction sheet in Paste-Text mode.
    private val sharedText = mutableStateOf<String?>(null)

    // Backs the home-screen widget's "+" button: set to a fresh timestamp
    // (not a plain boolean) every time the intent carries the
    // OPEN_ADD_TRANSACTION extra, so DashboardScreen's LaunchedEffect always
    // re-fires on a new tap even if the sheet was already opened once.
    private val openAddTransactionTrigger = mutableStateOf(0L)

    // App Lock gate: reset to false whenever the app is backgrounded (see
    // onStop) so it re-locks. Only actually enforced when
    // AppLockManager.isEnabled() is true; otherwise ignored entirely.
    private val isUnlocked = mutableStateOf(false)

    private fun extractSharedText(intent: Intent?): String? {
        return if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null
    }

    private fun handleIntentExtras(intent: Intent?) {
        extractSharedText(intent)?.let { sharedText.value = it }
        if (intent?.getBooleanExtra("OPEN_ADD_TRANSACTION", false) == true) {
            openAddTransactionTrigger.value = System.currentTimeMillis()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    override fun onStop() {
        super.onStop()
        if (AppLockManager.isEnabled(this)) {
            isUnlocked.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntentExtras(intent)
        setContent {
            val app = applicationContext as FinanceApplication
            val viewModel: FinanceViewModel = ViewModelProvider(
                this,
                FinanceViewModelFactory(app.repository, this.application)
            )[FinanceViewModel::class.java]

            val incomingSharedText by sharedText
            val addTransactionSignal by openAddTransactionTrigger
            val unlocked by isUnlocked

            val themeMode by viewModel.darkThemeMode.collectAsState()

            val isDarkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            FinanceTrackerTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Re-read fresh on every recomposition (cheap SharedPreferences
                    // read) rather than `remember`-ing once, so enabling/disabling
                    // App Lock mid-session takes effect immediately.
                    val lockContext = LocalContext.current
                    val lockEnabled = AppLockManager.isEnabled(lockContext)

                    if (lockEnabled && !unlocked) {
                        AppLockScreen(
                            mode = AppLockMode.UNLOCK,
                            onUnlocked = { isUnlocked.value = true }
                        )
                    } else {
                        var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
                        var bypassLogin by remember { mutableStateOf(false) }

                        DisposableEffect(Unit) {
                            val listener = FirebaseAuth.AuthStateListener { auth ->
                                user = auth.currentUser
                            }
                            FirebaseAuth.getInstance().addAuthStateListener(listener)
                            onDispose {
                                FirebaseAuth.getInstance().removeAuthStateListener(listener)
                            }
                        }

                        if (user != null || bypassLogin) {
                            DashboardScreen(
                                providedViewModel = viewModel,
                                sharedText = incomingSharedText,
                                openAddTransactionSignal = addTransactionSignal
                            )
                        } else {
                            LoginScreen(onLoginSuccess = {
                                val currentUser = FirebaseAuth.getInstance().currentUser
                                if (currentUser != null) {
                                    user = currentUser
                                } else {
                                    bypassLogin = true
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}
