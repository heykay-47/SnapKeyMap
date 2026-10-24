package com.heykay.snapkeymap.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.heykay.snapkeymap.Gesture
import com.heykay.snapkeymap.SnapEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val config by SnapEngine.store.config.collectAsStateWithLifecycle()
            val dark = resolveDark(config.theme)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose { }
            }
            SnapTheme(config.theme) { SnapKeyMapApp() }
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
    val config by SnapEngine.store.config.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(initialAppRoute(config.introSeen)) }
    var setupPage by rememberSaveable { mutableStateOf<String?>(null) }
    val goHome = { route = "home" }
    if (route != "home" && route != "intro") BackHandler(onBack = goHome)

    when {
        route == "intro" -> {
            LaunchedEffect(Unit) {
                SnapEngine.store.update { it.copy(introSeen = true) }
            }
            IntroScreen(onContinue = goHome)
        }
        route == "help" -> HelpScreen(onDone = goHome, onRunSetup = {
            setupPage = null
            SnapEngine.store.update {
                it.copy(setupComplete = false, grantMethod = null, setupSkipped = emptySet())
            }
            goHome()
        })
        route == "about" -> AboutScreen(onDone = goHome, onLicenses = { route = "licenses" })
        route == "licenses" -> LicensesScreen(onDone = { route = "about" })
        route == "learn" -> LearnScreen(onDone = goHome)
        route == "log-access" -> LogAccessScreen(onDone = goHome)
        route.startsWith("pick:") -> ActionPickerScreen(
            gesture = Gesture.valueOf(route.removePrefix("pick:")),
            onDone = goHome,
        )
        !config.setupComplete -> SetupScreen(
            pageName = setupPage,
            onPageChange = { setupPage = it },
            onLearn = { route = "learn" },
            onPick = { route = "pick:${it.name}" },
            onFinish = goHome,
        )
        else -> HomeScreen(
            onPick = { route = "pick:${it.name}" },
            onLearn = { route = "learn" },
            onLogAccess = { route = "log-access" },
            onAbout = { route = "about" },
            onHelp = { route = "help" },
        )
    }
}
