@file:OptIn(ExperimentalMaterial3Api::class)

package app.kept.ui.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kept.data.db.DeadlineRow
import app.kept.data.db.ReceiptEntity
import app.kept.data.db.ReceiptStatus
import app.kept.ui.findActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenReceipt: (String) -> Unit,
    savedMessage: String?,
    onMessageShown: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(savedMessage) {
        if (savedMessage != null) {
            onMessageShown()
            snackbar.showSnackbar(savedMessage)
        }
    }

    // Google's document scanner: edge detection, multi-page, cleanup, gallery import. No camera permission.
    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setGalleryImportAllowed(true)
                .setPageLimit(10)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .build(),
        )
    }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.map { it.imageUri }.orEmpty()
        if (pages.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { viewModel.createDraftFromScan(pages) }
                .onSuccess(onOpenReceipt)
                .onFailure { snackbar.showSnackbar("Couldn't save the scan. Please try again.") }
        }
    }
    val startScan: () -> Unit = {
        val activity = context.findActivity()
        if (activity != null) {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener {
                    scope.launch { snackbar.showSnackbar("The scanner isn't available. Update Google Play services and try again.") }
                }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Kept") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = startScan,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Scan receipt") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search stores, items, serial numbers") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
            if (state.isEmpty) {
                item { EmptyState(onScan = startScan) }
            }
            section("Needs review", state.unfinished) { receipt ->
                UnfinishedRow(receipt, onClick = { onOpenReceipt(receipt.id) })
            }
            section("Returns closing", state.returnsClosing) { row ->
                DeadlineItem(row, row.returnBy!!, "Return by", state.today, onClick = { onOpenReceipt(row.receiptId) })
            }
            section("Warranties ending", state.warrantiesEnding) { row ->
                DeadlineItem(row, row.warrantyEnds!!, "Warranty until", state.today, onClick = { onOpenReceipt(row.receiptId) })
            }
            section("Everything else", state.everythingElse) { row ->
                val next = listOfNotNull(
                    row.returnBy?.takeIf { !it.isBefore(state.today) }?.let { it to "Return by" },
                    row.warrantyEnds?.takeIf { !it.isBefore(state.today) }?.let { it to "Warranty until" },
                ).minByOrNull { it.first }
                if (next != null) {
                    DeadlineItem(row, next.first, next.second, state.today, onClick = { onOpenReceipt(row.receiptId) })
                } else {
                    ListItem(
                        headlineContent = { Text(row.itemName, maxLines = 2) },
                        supportingContent = { Text(listOfNotNull(row.merchantName, "No open deadlines").joinToString(" · ")) },
                        modifier = Modifier.clickable { onOpenReceipt(row.receiptId) },
                    )
                }
            }
        }
    }
}

private fun <T> LazyListScope.section(title: String, rows: List<T>, row: @Composable (T) -> Unit) {
    if (rows.isEmpty()) return
    item(key = "header-$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() },
        )
    }
    items(rows) { row(it) }
}

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val SPOKEN_DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

@Composable
private fun DeadlineItem(row: DeadlineRow, due: LocalDate, label: String, today: LocalDate, onClick: () -> Unit) {
    val days = ChronoUnit.DAYS.between(today, due)
    val pill = when (days) {
        0L -> "Today"
        1L -> "1 day"
        else -> "$days days"
    }
    // TalkBack reads the whole row as one sentence.
    val spoken = listOfNotNull(row.itemName, row.merchantName, "$label ${due.format(SPOKEN_DATE)}", "$pill left")
        .joinToString(", ")
    ListItem(
        headlineContent = { Text(row.itemName, maxLines = 2) },
        supportingContent = { Text(listOfNotNull(row.merchantName, "$label ${due.format(SHORT_DATE)}").joinToString(" · ")) },
        trailingContent = { DeadlinePill(pill, urgent = days <= 3) },
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    )
}

@Composable
private fun DeadlinePill(text: String, urgent: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (urgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun UnfinishedRow(receipt: ReceiptEntity, onClick: () -> Unit) {
    val status = when (receipt.status) {
        ReceiptStatus.DRAFT -> "Reading…"
        else -> "Tap to check and save"
    }
    ListItem(
        headlineContent = { Text(receipt.merchantName ?: "New receipt") },
        supportingContent = { Text(status) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun EmptyState(onScan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Never miss a return or warranty again.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan a receipt and Kept reminds you before the deadline. Your receipts stay on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onScan) { Text("Scan your first receipt") }
    }
}
