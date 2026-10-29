package com.gullak.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.gullak.app.navigation.GullakNavHost
import com.gullak.core.designsystem.theme.GullakTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single activity. Extends AppCompatActivity so per-app language works back to API 26. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GullakTheme {
                GullakNavHost()
            }
        }
    }
}
