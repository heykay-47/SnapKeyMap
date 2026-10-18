package com.heykay.snapkeymap.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.heykay.snapkeymap.Gesture
import com.heykay.snapkeymap.SnapEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapTheme { SnapKeyMapApp() }
        }
    }

    override fun onResume() {
        super.onResume()
        SnapEngine.onUiResumed()
    }

    override fun onStop() {
        SnapEngine.onUiStopped()
        super.onStop()
    }
}

@Composable
private fun SnapKeyMapApp() {
    // "home", "learn", "log-access" or "pick:<GESTURE>"
    var route by rememberSaveable { mutableStateOf("home") }
    val goHome = { route = "home" }
    if (route != "home") BackHandler(onBack = goHome)

    when {
        route == "learn" -> LearnScreen(onDone = goHome)
        route == "log-access" -> LogAccessScreen(onDone = goHome)
        route.startsWith("pick:") -> ActionPickerScreen(
            gesture = Gesture.valueOf(route.removePrefix("pick:")),
            onDone = goHome,
        )
        else -> HomeScreen(
            onPick = { route = "pick:${it.name}" },
            onLearn = { route = "learn" },
            onLogAccess = { route = "log-access" },
        )
    }
}

@Composable
private fun SnapTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
