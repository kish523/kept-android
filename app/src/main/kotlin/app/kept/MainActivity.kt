package app.kept

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kept.ui.theme.isDark
import androidx.lifecycle.lifecycleScope
import app.kept.data.db.CaptureSource
import app.kept.ui.KeptNavHost
import app.kept.ui.theme.KeptTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** A receipt created from the share sheet, waiting for the UI to open its review card. */
    private val sharedReceiptId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KeptApplication).container
        if (savedInstanceState == null) handleIntent(intent)
        lifecycleScope.launch { container.updater.checkOnLaunch() }
        setContent {
            val mode by container.themeSettings.mode.collectAsStateWithLifecycle()
            val dark = isDark(mode)
            // Status and navigation bar icons follow Kept's own light/dark choice, not just the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose {}
            }
            KeptTheme(dark = dark) {
                KeptNavHost(container = container, sharedReceiptId = sharedReceiptId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        isInForeground = true
    }

    override fun onStop() {
        super.onStop()
        isInForeground = false
        // Leaving the app is the least disruptive moment to install a waiting update.
        (application as KeptApplication).container.updater.installIfQuietlyPossible()
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN_RECEIPT)?.let { sharedReceiptId.value = it; return }
        if (intent?.getBooleanExtra(EXTRA_INSTALL_UPDATE, false) == true) {
            (application as KeptApplication).container.updater.install()
            return
        }
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri: Uri = (
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            ) ?: return
        val isPdf = intent.type == "application/pdf"
        val container = (application as KeptApplication).container
        lifecycleScope.launch {
            runCatching { container.repository.createDraft(listOf(uri), CaptureSource.SHARE_SHEET, isPdf) }
                .onSuccess { id ->
                    container.appScope.launch { container.extraction.run(id) }
                    sharedReceiptId.value = id
                }
                .onFailure {
                    Toast.makeText(this@MainActivity, "Couldn't open that file", Toast.LENGTH_LONG).show()
                }
        }
    }

    companion object {
        /** Open this receipt (from a reminder notification). */
        const val EXTRA_OPEN_RECEIPT = "openReceipt"
        /** Install the downloaded update (from the "update ready" notification). */
        const val EXTRA_INSTALL_UPDATE = "installUpdate"

        @Volatile var isInForeground: Boolean = false
            private set
    }
}
