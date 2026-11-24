@file:OptIn(ExperimentalMaterial3Api::class)

package app.kept.ui.home

import android.app.Activity
import android.Manifest
import android.os.Build
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kept.BuildConfig
import app.kept.R
import app.kept.data.db.ReceiptEntity
import app.kept.data.db.ReceiptStatus
import app.kept.data.settings.ThemeMode
import app.kept.ui.components.DaysPill
import app.kept.ui.components.GlassSnackbar
import app.kept.ui.components.IconTile
import app.kept.ui.components.SectionLabel
import app.kept.ui.components.ambientGlow
import app.kept.ui.components.enterRise
import app.kept.ui.components.keptClickable
import app.kept.ui.findActivity
import app.kept.ui.theme.Kept
import app.kept.ui.theme.KeptIcons
import app.kept.ui.theme.KeptMotion
import app.kept.ui.theme.isDark
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val SPOKEN: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
private val HEADER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenReceipt: (String) -> Unit,
    savedMessage: String?,
    onMessageShown: () -> Unit,
    deleteRequest: String? = null,
    onDeleteHandled: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val k = Kept.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Rows rise in one after another the first time Home appears, not every time they scroll back in.
    var intro by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1600); intro = false }

    val update by viewModel.update.collectAsStateWithLifecycle()
    val whatsNew by viewModel.whatsNew.collectAsStateWithLifecycle()
    var installTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { installTick++ }
    val canInstall = remember(installTick) { viewModel.canInstallUpdates() }

    // Ask for notifications right after the first receipt is saved: the moment reminders start to matter.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onNotificationsAllowed()
    }
    LaunchedEffect(savedMessage) {
        if (savedMessage != null) {
            onMessageShown()
            if (Build.VERSION.SDK_INT >= 33 && !viewModel.notificationsAllowed() && !viewModel.askedForNotifications) {
                viewModel.askedForNotifications = true
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            snackbar.showSnackbar(savedMessage)
        }
    }
    LaunchedEffect(deleteRequest) {
        if (deleteRequest != null) {
            onDeleteHandled()
            viewModel.delete(deleteRequest)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            val result = snackbar.showSnackbar(
                msg.text,
                actionLabel = if (msg.undo != null) "Undo" else null,
                duration = if (msg.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(msg) else viewModel.expire(msg)
        }
    }

    // Google's document scanner: edge detection, multi-page, cleanup, gallery import. No camera permission.
    // Created on first tap, not on first frame: keeps start-up light and Home renderable without Play services.
    val scanner by lazy {
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
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        val activity = context.findActivity()
        if (activity != null) {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener {
                    scope.launch { snackbar.showSnackbar("The scanner isn't available. Update Google Play services and try again.") }
                }
        }
    }

    val density = LocalDensity.current
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > with(density) { 56.dp.toPx() } }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dark = isDark(themeMode)
    val c = state.content

    Box(Modifier.fillMaxSize().background(k.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 14.dp, bottom = bottom + 160.dp),
        ) {
            item(key = "header") {
                Header(
                    today = state.today,
                    dark = dark,
                    onToggleTheme = { viewModel.setTheme(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) },
                    onSettings = { showSettings = true },
                    modifier = Modifier.ambientGlow(),
                )
            }
            item(key = "update") {
                UpdateCard(
                    state = update,
                    canInstall = canInstall,
                    onInstall = viewModel::installUpdate,
                    onAllow = { context.startActivity(viewModel.installPermissionIntent()) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (c.hero != null || state.loaded) {
                item(key = "hero") {
                    HeroCard(
                        hero = c.hero,
                        today = state.today,
                        onOpen = onOpenReceipt,
                        onKeep = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); viewModel.keep(it) },
                        onReturned = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); viewModel.returned(it) },
                        modifier = Modifier.enterRise(0, intro, baseMs = 40),
                    )
                }
            }
            if (!state.isEmpty) {
                item(key = "search") {
                    SearchField(state.query, viewModel::onQueryChange, Modifier.padding(top = 16.dp).enterRise(1, intro, baseMs = 40))
                }
                item(key = "filter") {
                    FilterControl(
                        selected = state.filter,
                        counts = Triple(c.allCount, c.returnsCount, c.warrantiesCount),
                        onSelect = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); viewModel.onFilter(it) },
                        modifier = Modifier.padding(top = 12.dp).enterRise(2, intro, baseMs = 40),
                    )
                }
            } else {
                item(key = "empty") { EmptyState(onScan = startScan, modifier = Modifier.enterRise(1, intro)) }
            }

            var index = 3
            if (c.unfinished.isNotEmpty()) {
                item(key = "sec-unfinished") { SectionLabel("Needs review", Modifier.padding(top = 26.dp).animateItem()) }
                itemsIndexed(c.unfinished, key = { _, r -> "u-${r.id}" }) { i, r ->
                    val n = index++
                    Box(Modifier.animateItem().enterRise(n, intro)) {
                        SwipeToDelete(rowShape(i == 0, i == c.unfinished.lastIndex), onDelete = { viewModel.delete(r.id) }) {
                            UnfinishedRow(r, first = i == 0, last = i == c.unfinished.lastIndex, onClick = { onOpenReceipt(r.id) },
                                onDelete = { viewModel.delete(r.id) })
                        }
                    }
                }
            }
            c.sections.forEach { section ->
                item(key = "sec-${section.title}") { SectionLabel(section.title, Modifier.padding(top = 26.dp).animateItem()) }
                itemsIndexed(section.rows, key = { _, r -> "r-${r.itemId}" }) { i, row ->
                    val n = index++
                    Box(Modifier.animateItem().enterRise(n, intro)) {
                        SwipeToDelete(rowShape(i == 0, i == section.rows.lastIndex), onDelete = { viewModel.delete(row.receiptId) }) {
                            DeadlineRowItem(row, state.today, first = i == 0, last = i == section.rows.lastIndex,
                                animateBar = intro, barDelay = n,
                                onClick = { onOpenReceipt(row.receiptId) },
                                onDelete = { viewModel.delete(row.receiptId) })
                        }
                    }
                }
            }
            if (state.loaded && c.sections.isEmpty() && state.query.isNotBlank() && c.unfinished.isEmpty()) {
                item(key = "nomatch") {
                    Text(
                        "No receipts match “${state.query.trim()}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = k.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    )
                }
            }
        }

        // Compact frosted title bar that appears once the big "Kept" title scrolls away.
        AnimatedVisibility(
            visible = scrolled,
            enter = fadeIn(tween(220)) + slideInVertically(tween(320, easing = KeptMotion.Settle)) { -it / 6 },
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .hazeEffect(hazeState, k.glassBar)
                    .drawBehind { drawLine(k.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                    .padding(top = top)
                    .height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Kept", style = MaterialTheme.typography.titleMedium, color = k.ink)
            }
        }

        // Content blurs progressively as it slides under the Scan button.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottom + 130.dp)
                .hazeEffect(hazeState, k.glassBar) {
                    progressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f)
                },
        )

        SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 100.dp),
        ) { GlassSnackbar(it) }

        ScanButton(onClick = startScan, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 24.dp))
    }

    if (showSettings) {
        SettingsSheet(viewModel = viewModel, mode = themeMode, dark = dark, onDismiss = { showSettings = false })
    }
    whatsNew?.let { WhatsNewSheet(it, onDismiss = viewModel::dismissWhatsNew) }
}

@Composable
private fun Header(today: LocalDate, dark: Boolean, onToggleTheme: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val k = Kept.colors
    Row(
        modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp).enterRise(0, baseMs = 0),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(today.format(HEADER_DATE), style = MaterialTheme.typography.labelLarge, color = k.muted)
            Text("Kept", style = MaterialTheme.typography.displaySmall, color = k.ink, modifier = Modifier.padding(top = 2.dp))
        }
        Row(Modifier.offset(x = 8.dp)) {
            IconButton(onClick = onToggleTheme) {
                AnimatedContent(dark, transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.6f)) togetherWith fadeOut(tween(150))
                }, label = "theme") { isDark ->
                    Icon(
                        if (isDark) KeptIcons.Sun else KeptIcons.Moon,
                        contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                        tint = k.ink,
                    )
                }
            }
            IconButton(onClick = onSettings) { Icon(KeptIcons.Settings, contentDescription = "Settings", tint = k.ink) }
        }
    }
}

@Composable
private fun HeroCard(
    hero: HeroModel?,
    today: LocalDate,
    onOpen: (String) -> Unit,
    onKeep: (String) -> Unit,
    onReturned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = hero,
        contentKey = { it?.itemId to it?.kind },
        transitionSpec = {
            (fadeIn(tween(320, 120)) + slideInHorizontally(tween(480, easing = KeptMotion.Settle)) { it / 8 }) togetherWith
                (fadeOut(tween(180)) + slideOutHorizontally(tween(320, easing = KeptMotion.Settle)) { -it / 8 }) using
                SizeTransform(clip = false)
        },
        modifier = modifier,
        label = "hero",
    ) { h ->
        if (h == null) {
            Spacer(Modifier.fillMaxWidth().height(0.dp))
        } else {
            HeroContent(h, today, onOpen, onKeep, onReturned)
        }
    }
}

@Composable
private fun HeroContent(h: HeroModel, today: LocalDate, onOpen: (String) -> Unit, onKeep: (String) -> Unit, onReturned: (String) -> Unit) {
    val k = Kept.colors
    val isReturn = h.kind == DeadlineKind.RETURN
    val dueText = if (isReturn) "Return by ${h.due.format(SHORT)}" else "Warranty ends ${h.due.format(SHORT)}"
    val unit = when (h.daysLeft) { 0L -> "last day"; 1L -> "day left"; else -> "days left" }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(k.heroBackground.copy(alpha = 0.88f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(28.dp))
            .padding(6.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .keptClickable(pressed = 0.98f) { onOpen(h.receiptId) }
                .semantics(mergeDescendants = true) {
                    contentDescription = "Next up: ${h.name}${h.merchant?.let { ", $it" }.orEmpty()}. $dueText, " +
                        "${h.daysLeft} $unit. ${h.due.format(SPOKEN)}"
                }
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text("NEXT UP", style = MaterialTheme.typography.labelSmall, color = k.heroInk.copy(alpha = 0.72f), modifier = Modifier.weight(1f))
                Text(
                    (if (isReturn) "Return window" else "Warranty").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = k.heroInk.copy(alpha = 0.72f),
                )
            }
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                DaysRing(h.daysLeft, unit, h.fractionLeft)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize), color = k.heroInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    h.merchant?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = k.heroInk.copy(alpha = 0.72f), modifier = Modifier.padding(top = 2.dp)) }
                    Text(
                        dueText,
                        style = MaterialTheme.typography.labelMedium,
                        color = k.heroArc,
                        modifier = Modifier.padding(top = 10.dp).clip(CircleShape).background(k.heroChip).padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (isReturn) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeroAction("Keeping it", KeptIcons.Check, Modifier.weight(1f)) { onKeep(h.itemId) }
                HeroAction("Returned it", KeptIcons.ReturnArrow, Modifier.weight(1f)) { onReturned(h.itemId) }
            }
        }
    }
}

@Composable
private fun HeroAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val k = Kept.colors
    Row(
        modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.09f))
            .keptClickable(pressed = 0.95f, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = k.heroInk, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = k.heroInk)
    }
}

/** The countdown ring. Fills in when it first appears, then animates to new values. */
@Composable
private fun DaysRing(days: Long, unit: String, fractionLeft: Float) {
    val k = Kept.colors
    val start = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(250); start.animateTo(1f, tween(1200, easing = KeptMotion.Settle)) }
    val target by animateFloatAsState(fractionLeft, tween(900, easing = KeptMotion.Settle), label = "ring")
    Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 7.dp.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
            drawArc(k.heroTrack, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w))
            drawArc(k.heroArc, -90f, 360f * target * start.value, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$days", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = k.heroInk)
            Text(unit, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing), color = k.heroInk.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val k = Kept.colors
    var focused by remember { mutableStateOf(false) }
    val ring by animateDpAsState(if (focused) 2.dp else 1.dp, label = "ring")
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = k.ink, fontSize = MaterialTheme.typography.titleSmall.fontSize),
        cursorBrush = SolidColor(k.accent),
        keyboardOptions = KeyboardOptions.Default,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Search receipts" }
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(k.surface)
                    .border(ring, if (focused) k.accent else k.line, CircleShape)
                    .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(KeptIcons.Search, contentDescription = null, tint = k.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search stores, items, serial numbers", style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize), color = k.muted, maxLines = 1)
                    inner()
                }
                AnimatedVisibility(query.isNotEmpty(), enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut()) {
                    IconButton(onClick = { onChange("") }) {
                        Icon(KeptIcons.Close, contentDescription = "Clear search", tint = k.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun FilterControl(selected: HomeFilter, counts: Triple<Int, Int, Int>, onSelect: (HomeFilter) -> Unit, modifier: Modifier = Modifier) {
    val k = Kept.colors
    val options = listOf(HomeFilter.ALL to "All", HomeFilter.RETURNS to "Returns", HomeFilter.WARRANTIES to "Warranties")
    val countFor = mapOf(HomeFilter.ALL to counts.first, HomeFilter.RETURNS to counts.second, HomeFilter.WARRANTIES to counts.third)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(k.ink.copy(alpha = 0.06f))
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * options.indexOfFirst { it.first == selected }, tween(450, easing = KeptMotion.Settle), label = "seg")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .height(42.dp)
                .clip(CircleShape)
                .background(if (k.dark) Color(0xFF2C3036) else Color.White),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEach { (f, label) ->
                val on = f == selected
                Row(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(CircleShape)
                        .keptClickable(pressed = 0.96f, role = Role.Tab) { onSelect(f) }
                        .semantics { this.selected = on },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) k.ink else k.muted)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${countFor[f]}",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing),
                        color = if (on) k.accent else k.muted,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (on) k.accentContainer else k.line)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

internal fun rowShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = if (first) 24.dp else 0.dp,
    topEnd = if (first) 24.dp else 0.dp,
    bottomStart = if (last) 24.dp else 0.dp,
    bottomEnd = if (last) 24.dp else 0.dp,
)

@Composable
private fun RowContainer(first: Boolean, last: Boolean, modifier: Modifier, spoken: String?, onClick: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val k = Kept.colors
    val shape = rowShape(first, last)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(k.surface)
            .drawBehind {
                if (!first) drawLine(k.line, Offset(72.dp.toPx(), 0f), Offset(size.width - 16.dp.toPx(), 0f), 1.dp.toPx())
            }
            .keptClickable(pressed = 0.985f, onClick = onClick)
            // Swiping isn't available to everyone: TalkBack and Switch Access get "Delete" as an action.
            .semantics(mergeDescendants = true) {
                if (spoken != null) contentDescription = spoken
                customActions = listOf(CustomAccessibilityAction("Delete") { onDelete(); true })
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) { content() }
}

@Composable
private fun DeadlineRowItem(
    row: HomeRow,
    today: LocalDate,
    first: Boolean,
    last: Boolean,
    animateBar: Boolean,
    barDelay: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val k = Kept.colors
    val spoken = listOfNotNull(
        row.name,
        row.merchant,
        row.label,
        row.daysLeft?.let { if (it <= 0) "last day today" else "${daysLabel(it)} left" },
    ).joinToString(", ")
    RowContainer(first, last, modifier, spoken, onClick, onDelete) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(KeptIcons.forCategory(row.category))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    // The date first: it's what matters, and the store name is the part that can be cut off.
                    listOfNotNull(row.label, row.merchant).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                row.windowUsed?.let { WindowBar(it, row.soon, animateBar, barDelay) }
            }
            row.daysLeft?.let {
                Spacer(Modifier.width(10.dp))
                DaysPill(daysLabel(it), row.soon)
            }
        }
    }
}

/** Thin bar: how much of the return or warranty window has already gone. */
@Composable
private fun WindowBar(used: Float, soon: Boolean, animate: Boolean, index: Int) {
    val k = Kept.colors
    val grow = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) { delay(300L + index * 45L); grow.animateTo(1f, tween(1100, easing = KeptMotion.Settle)) } }
    Box(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(k.line),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(used * grow.value)
                .clip(CircleShape)
                .background(if (soon) k.soon else k.accent),
        )
    }
}

@Composable
private fun UnfinishedRow(receipt: ReceiptEntity, first: Boolean, last: Boolean, onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val k = Kept.colors
    val reading = receipt.status == ReceiptStatus.DRAFT
    RowContainer(first, last, modifier, null, onClick, onDelete) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(KeptIcons.Scan)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(receipt.merchantName ?: "New receipt", style = MaterialTheme.typography.titleSmall, color = k.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (reading) "Reading…" else "Tap to check and save", style = MaterialTheme.typography.bodySmall, color = k.muted, modifier = Modifier.padding(top = 3.dp))
            }
            if (!reading) {
                Text(
                    "Check",
                    style = MaterialTheme.typography.labelMedium,
                    color = k.check,
                    modifier = Modifier.clip(CircleShape).background(k.checkContainer).padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onScan: () -> Unit, modifier: Modifier = Modifier) {
    val k = Kept.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(k.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(KeptIcons.ReturnArrow, size = 56.dp)
        Text(
            "Never miss a return or warranty again.",
            style = MaterialTheme.typography.titleLarge,
            color = k.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Scan a receipt and Kept reminds you before the deadline. Your receipts stay on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = k.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Scan your first receipt",
            style = MaterialTheme.typography.labelLarge,
            color = k.accent,
            modifier = Modifier
                .padding(top = 16.dp)
                .clip(CircleShape)
                .background(k.accentContainer)
                .keptClickable(onClick = onScan)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ScanButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val k = Kept.colors
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(400); appear.animateTo(1f, KeptMotion.pop()) }
    Surface(
        shape = CircleShape,
        color = k.accent,
        contentColor = k.onAccent,
        shadowElevation = 10.dp,
        modifier = modifier
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                val s = 0.7f + 0.3f * appear.value
                scaleX = s; scaleY = s
                translationY = (1f - appear.value) * 40.dp.toPx()
            }
            .heightIn(min = 60.dp)
            .clip(CircleShape)
            .keptClickable(pressed = 0.93f, onClick = onClick),
    ) {
        Row(Modifier.padding(start = 22.dp, end = 26.dp, top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(KeptIcons.Scan, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("Scan receipt", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}
