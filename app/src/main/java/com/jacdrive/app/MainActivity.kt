package com.jacdrive.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.app.navigation.JacDriveApp
import com.jacdrive.core.model.ThemeMode
import com.jacdrive.ui.theme.JacDriveTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import com.jacdrive.obd.service.AutoConnect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single activity. Extends AppCompatActivity so per-app language (Arabic/English) switching via
 * AppCompatDelegate works on every supported Android version.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var autoConnect: AutoConnect

    override fun onStart() {
        super.onStart()
        // Reconnect to the saved adapter once per app launch, if the user enabled it.
        lifecycleScope.launch { autoConnect.onAppForeground() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !viewModel.state.value.loaded }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val dark = when (state.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            androidx.compose.runtime.LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            JacDriveTheme(themeMode = state.themeMode, accent = Color(state.accentArgb)) {
                JacDriveApp(sportMode = state.sportMode, onSport = viewModel::setSport)
            }
        }
    }
}
