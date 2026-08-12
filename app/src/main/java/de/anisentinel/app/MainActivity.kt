package de.anisentinel.app

import android.os.Bundle
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import de.anisentinel.app.domain.repository.ThemePreference
import de.anisentinel.app.ui.theme.resolveDarkTheme
import de.anisentinel.app.ui.AniSentinelApp
import de.anisentinel.app.ui.theme.AniSentinelTheme

class MainActivity : ComponentActivity() {
    private var notificationTarget by mutableStateOf<Uri?>(null)
    private val appViewModel: AppViewModel by viewModels()
    private var notificationPermissionResult: ((Boolean) -> Unit)? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionResult?.invoke(granted)
        notificationPermissionResult = null
    }
    private var diagnosticDocumentResult: ((Uri?) -> Unit)? = null
    private val diagnosticDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        diagnosticDocumentResult?.invoke(uri)
        diagnosticDocumentResult = null
    }

    fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        notificationPermissionResult = onResult
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    fun openDiagnosticJson(onResult: (Uri?) -> Unit) {
        diagnosticDocumentResult = onResult
        diagnosticDocumentLauncher.launch(arrayOf("application/json"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationTarget = intent?.data
        enableEdgeToEdge()
        setContent {
            val state by appViewModel.state.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = resolveDarkTheme(state.settings.theme, systemDark)
            AniSentinelTheme(
                darkTheme = useDarkTheme,
                languageTag = state.settings.languageTag
            ) {
                if (state.loaded) {
                    AniSentinelApp(notificationTarget) { notificationTarget = null }
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTarget = intent.data
    }
}
