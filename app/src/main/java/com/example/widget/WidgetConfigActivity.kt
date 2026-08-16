package com.example.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FinanceTrackerTheme

/**
 * Shown once when the user drags the balance widget onto their home screen
 * (declared via android:configure in balance_widget_info.xml), and
 * re-openable later by long-pressing the widget -> Edit/Configure on most
 * launchers. Lets them pick a background theme and opacity so the widget
 * can blend with their wallpaper rather than being a flat block of color.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the user backs out without saving, the launcher should treat
        // this as a cancelled widget placement.
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            FinanceTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConfigScreen(
                        onSave = { theme, opacity ->
                            val prefs = getSharedPreferences("vantage_prefs", MODE_PRIVATE)
                            prefs.edit()
                                .putString("widget_theme_$appWidgetId", theme)
                                .putInt("widget_opacity_$appWidgetId", opacity)
                                .apply()

                            BalanceWidgetProvider.refreshAll(this)

                            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            setResult(Activity.RESULT_OK, resultValue)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

private data class ThemeOption(val key: String, val label: String, val swatch: Color)

private val THEME_OPTIONS = listOf(
    ThemeOption("GOLD", "Vantage Gold", Color(0xFFFFD60A)),
    ThemeOption("DARK", "Dark Glass", Color(0xFF1A1A1A)),
    ThemeOption("LIGHT", "Light Glass", Color(0xFFFFFFFF)),
    ThemeOption("AUTO", "Match System Theme", Color(0xFF888888))
)

@Composable
private fun ConfigScreen(onSave: (theme: String, opacity: Int) -> Unit) {
    var selectedTheme by remember { mutableStateOf("GOLD") }
    var opacity by remember { mutableStateOf(100f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Customize Widget", fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(
            "Pick a look that fits your wallpaper — you can reopen this by long-pressing the widget later.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Theme", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            THEME_OPTIONS.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selectedTheme == option.key) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { selectedTheme = option.key }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(option.swatch)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                    )
                    Text(option.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (selectedTheme == option.key) {
                        Text("✓", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Text("Opacity: ${opacity.toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            "Lower opacity lets your wallpaper show through the widget background.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = opacity,
            onValueChange = { opacity = it },
            valueRange = 20f..100f
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onSave(selectedTheme, opacity.toInt()) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Add Widget", fontWeight = FontWeight.Bold)
        }
    }
}
