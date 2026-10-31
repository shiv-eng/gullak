package com.shivangi.gullak.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.shivangi.gullak.app.navigation.GullakNavHost
import com.shivangi.gullak.core.designsystem.theme.GullakTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single activity. Extends AppCompatActivity so per-app language works back to API 26. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark status bar icons even if the phone is in dark mode, because Gullak is always light.
        val lightBars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = lightBars, navigationBarStyle = lightBars)
        super.onCreate(savedInstanceState)
        setContent {
            // The Paytm app is always light; Gullak matches it.
            GullakTheme(darkTheme = false) {
                GullakNavHost()
            }
        }
    }
}
