package app.kept

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            KeptTheme {
                KeptNavHost(container = container, sharedReceiptId = sharedReceiptId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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
}
