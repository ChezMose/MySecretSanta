package io.github.chezmose.mysecretsanta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.chezmose.mysecretsanta.ui.HomeScreen
import io.github.chezmose.mysecretsanta.ui.share.ShareScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        setContent {
            MaterialTheme {
                // Navigation minimale tant que l'app n'a que deux écrans.
                var showShare by rememberSaveable { mutableStateOf(false) }
                if (showShare) {
                    BackHandler { showShare = false }
                    ShareScreen(onBack = { showShare = false })
                } else {
                    HomeScreen(version = version, onShare = { showShare = true })
                }
            }
        }
    }
}
