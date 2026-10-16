package io.github.stub1t.chronicals

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.stub1t.chronicals.ui.AppScaffold
import io.github.stub1t.chronicals.ui.theme.ChronicalsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val diary = (application as ChronicalsApp).diary
        setContent {
            ChronicalsTheme {
                AppScaffold(diary)
            }
        }
    }
}
