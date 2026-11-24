@file:OptIn(ExperimentalMaterial3Api::class)

package app.kept.ui.home

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kept.BuildConfig
import app.kept.R
import app.kept.data.settings.ThemeMode
import app.kept.ui.components.IconTile
import app.kept.ui.components.SectionLabel
import app.kept.ui.components.keptClickable
import app.kept.ui.theme.Kept
import app.kept.ui.theme.KeptIcons
import app.kept.update.NoteBlock
import app.kept.update.UpdateState

/** Swipe a row left to delete it. A red tray with a bin shows underneath as it slides. */
@Composable
fun SwipeToDelete(shape: Shape, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val k = Kept.colors
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val active = state.targetValue == SwipeToDismissBoxValue.EndToStart
            val bg by animateColorAsState(if (active) k.soon else k.soonContainer, tween(200), label = "swipeBg")
            val fg = if (!active) k.soon else if (k.dark) Color(0xFF0F1012) else Color.White
            Row(
                Modifier.fillMaxSize().clip(shape).background(bg).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Delete", style = MaterialTheme.typography.labelLarge, color = fg)
                Spacer(Modifier.width(8.dp))
                Icon(KeptIcons.Trash, contentDescription = null, tint = fg)
            }
        },
    ) { content() }
}

/** "Kept 0.2.2 is ready": shown at the top of Home while an update downloads or waits. */
@Composable
fun UpdateCard(state: UpdateState, canInstall: Boolean, onInstall: () -> Unit, onAllow: () -> Unit, modifier: Modifier = Modifier) {
    val k = Kept.colors
    val info = when (state) {
        is UpdateState.Downloading -> state.info
        is UpdateState.Ready -> state.info
        is UpdateState.Installing -> state.info
        else -> return
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(k.accentContainer)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(KeptIcons.Download, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (state) {
                        is UpdateState.Downloading -> "Downloading Kept ${info.versionName}…"
                        is UpdateState.Installing -> "Installing Kept ${info.versionName}…"
                        else -> "Kept ${info.versionName} is ready"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = k.ink,
                )
                Text(
                    when {
                        state is UpdateState.Ready && !canInstall -> "Allow Kept to install updates, once."
                        state is UpdateState.Ready -> "Kept restarts to finish. You'll see what's new."
                        else -> "You can keep using Kept."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = k.muted,
                )
            }
            if (state is UpdateState.Ready) {
                Spacer(Modifier.width(8.dp))
                Text(
                    if (canInstall) "Update" else "Allow",
                    style = MaterialTheme.typography.labelLarge,
                    color = k.onAccent,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(k.accent)
                        .keptClickable(pressed = 0.94f) { if (canInstall) onInstall() else onAllow() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        if (state is UpdateState.Downloading) {
            LinearProgressIndicator(
                progress = { state.progress },
                color = k.accent,
                trackColor = k.surface,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(CircleShape),
            )
        }
    }
}

/** Shown once after an update: what this version brought. */
@Composable
fun WhatsNewSheet(whatsNew: WhatsNew, onDismiss: () -> Unit) {
    val k = Kept.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = k.background, contentColor = k.ink) {
        WhatsNewContent(whatsNew, onDismiss)
    }
}

@Composable
fun WhatsNewContent(whatsNew: WhatsNew, onDismiss: () -> Unit) {
    val k = Kept.colors
    run {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(KeptIcons.Sparkle, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("What's new in ${whatsNew.version}", style = MaterialTheme.typography.headlineSmall)
                    whatsNew.title?.let { Text(it.replaceFirstChar(Char::titlecase), style = MaterialTheme.typography.bodyMedium, color = k.muted) }
                }
            }
            Column(
                Modifier.padding(top = 16.dp).heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                whatsNew.blocks.forEach { b ->
                    when (b) {
                        is NoteBlock.Heading -> SectionLabel(b.text, Modifier.padding(top = 10.dp))
                        is NoteBlock.Bullet -> Row(Modifier.padding(horizontal = 6.dp)) {
                            Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(k.accent))
                            Spacer(Modifier.width(12.dp))
                            Text(rich(b.text), style = MaterialTheme.typography.bodyMedium, color = k.ink)
                        }
                        is NoteBlock.Paragraph -> Text(rich(b.text), style = MaterialTheme.typography.bodyMedium, color = k.muted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp))
                    }
                }
            }
            Text(
                "Got it",
                style = MaterialTheme.typography.titleSmall,
                color = k.onAccent,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(k.accent)
                    .keptClickable(onClick = onDismiss)
                    .padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** `**bold**` to bold spans, and `code` marks dropped. */
private fun rich(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.replace("`", "").split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(part)
        if (i % 2 == 1) pop()
    }
}

@Composable
fun SettingsSheet(viewModel: HomeViewModel, mode: ThemeMode, dark: Boolean, onDismiss: () -> Unit) {
    val k = Kept.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = k.background, contentColor = k.ink, scrimColor = Color.Black.copy(alpha = 0.32f)) {
        SettingsContent(viewModel, mode, dark, onDismiss)
    }
}

@Composable
fun SettingsContent(viewModel: HomeViewModel, mode: ThemeMode, dark: Boolean, onDismiss: () -> Unit) {
    val k = Kept.colors
    val context = LocalContext.current
    val update by viewModel.update.collectAsStateWithLifecycle()
    val autoUpdate by viewModel.autoUpdate.collectAsStateWithLifecycle()
    // Re-read things the user can change in system settings when they come back.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notificationsOn = remember(tick) { viewModel.notificationsAllowed() }
    val canInstall = remember(tick) { viewModel.canInstallUpdates() }
    var finished by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(tick) { finished = viewModel.finishedCount() }
    var confirmCleanUp by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onNotificationsAllowed()
        tick++
    }
    val openNotificationSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }

    run {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 6.dp))

            SectionLabel("Reminders", Modifier.padding(top = 20.dp))
            Group {
                ActionRow(
                    icon = KeptIcons.Bell,
                    title = "Notifications",
                    subtitle = if (notificationsOn) "On. Reminders at 9:00: 7, 3 and 1 day before a return closes, 30 and 7 before a warranty ends."
                    else "Off. Kept can't remind you. Tap to allow.",
                    action = if (notificationsOn) null else "Allow",
                    onClick = {
                        if (!notificationsOn && Build.VERSION.SDK_INT >= 33 && !viewModel.askedForNotifications) {
                            viewModel.askedForNotifications = true
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings()
                        }
                    },
                )
            }

            SectionLabel("Appearance", Modifier.padding(top = 20.dp))
            Group {
                SwitchRow(
                    icon = KeptIcons.Settings,
                    title = "Match phone setting",
                    subtitle = "Light or dark with the rest of your phone",
                    checked = mode == ThemeMode.SYSTEM,
                    onChange = { on -> viewModel.setTheme(if (on) ThemeMode.SYSTEM else if (dark) ThemeMode.DARK else ThemeMode.LIGHT) },
                )
                Line()
                SwitchRow(
                    icon = KeptIcons.Moon,
                    title = "Dark mode",
                    subtitle = if (mode == ThemeMode.SYSTEM) "Following your phone: ${if (dark) "on" else "off"}" else if (dark) "On" else "Off",
                    checked = dark,
                    onChange = { on -> viewModel.setTheme(if (on) ThemeMode.DARK else ThemeMode.LIGHT) },
                )
            }

            SectionLabel("Updates", Modifier.padding(top = 20.dp))
            Group {
                SwitchRow(
                    icon = KeptIcons.Download,
                    title = "Automatic updates",
                    subtitle = "Checks GitHub once a day and installs new versions. Nothing about your receipts is sent.",
                    checked = autoUpdate,
                    onChange = viewModel::setAutoUpdate,
                )
                Line()
                val (status, action) = when (val u = update) {
                    UpdateState.Checking -> "Checking…" to null
                    is UpdateState.Downloading -> "Downloading ${u.info.versionName}… ${(u.progress * 100).toInt()}%" to null
                    is UpdateState.Ready -> "Kept ${u.info.versionName} is ready" to (if (canInstall) "Install" else "Allow")
                    is UpdateState.Installing -> "Installing ${u.info.versionName}…" to null
                    UpdateState.UpToDate -> "You have the latest version" to "Check"
                    is UpdateState.Failed -> u.message to "Retry"
                    UpdateState.Idle -> "Check now for a newer version" to "Check"
                }
                ActionRow(
                    icon = KeptIcons.Sparkle,
                    title = "Kept ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    subtitle = status,
                    action = action,
                    onClick = {
                        when {
                            update is UpdateState.Ready && !canInstall -> context.startActivity(viewModel.installPermissionIntent())
                            update is UpdateState.Ready -> viewModel.installUpdate()
                            action != null -> viewModel.checkForUpdate()
                        }
                    },
                )
                Line()
                ActionRow(icon = KeptIcons.Sparkle, title = "What's new", subtitle = "What this version brought", action = null, onClick = {
                    onDismiss()
                    viewModel.showWhatsNew()
                })
            }

            SectionLabel("Storage", Modifier.padding(top = 20.dp))
            Group {
                val n = finished
                ActionRow(
                    icon = KeptIcons.Trash,
                    title = "Clean up finished receipts",
                    subtitle = when (n) {
                        null -> "Looking…"
                        0 -> "Nothing to clean up. Receipts with an open return or warranty are kept."
                        1 -> "1 receipt has nothing left to track (returned, or every window closed)."
                        else -> "$n receipts have nothing left to track (returned, or every window closed)."
                    },
                    action = if ((n ?: 0) > 0) "Delete" else null,
                    onClick = { if ((n ?: 0) > 0) confirmCleanUp = true },
                )
            }

            SectionLabel("About", Modifier.padding(top = 20.dp))
            Group {
                ActionRow(
                    icon = KeptIcons.Shield,
                    title = "Free and open source",
                    subtitle = "Apache License 2.0. Anyone can read how Kept treats your data.",
                    action = "Code",
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/kish523/kept-android")))
                        }
                    },
                )
            }

            Row(Modifier.padding(top = 18.dp, start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0F6E56)), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.requiredSize(60.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Kept · version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge, color = k.ink)
                    Text("Free and open source. Your receipts stay on this phone.", style = MaterialTheme.typography.bodySmall, color = k.muted)
                }
            }

            // Credit to Kept's creator. Tapping it opens their GitHub profile.
            Text(
                buildAnnotatedString {
                    append("Made with care by ")
                    pushStyle(SpanStyle(color = k.accent, fontWeight = FontWeight.Bold))
                    append("Kishara Ransara")
                    pop()
                },
                style = MaterialTheme.typography.bodySmall,
                color = k.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .clip(CircleShape)
                    .keptClickable(pressed = 0.97f, role = Role.Button) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/kish523")))
                        }
                    }
                    .semantics { contentDescription = "Made by Kishara Ransara. Opens their GitHub profile." }
                    .padding(vertical = 12.dp),
            )
        }
    }

    if (confirmCleanUp) {
        val n = finished ?: 0
        AlertDialog(
            onDismissRequest = { confirmCleanUp = false },
            title = { Text(if (n == 1) "Delete 1 finished receipt?" else "Delete $n finished receipts?") },
            text = { Text("Their photos and details are removed from this phone. You can undo right after.") },
            confirmButton = {
                TextButton(onClick = { confirmCleanUp = false; onDismiss(); viewModel.cleanUpFinished() }) { Text("Delete", color = k.soon) }
            },
            dismissButton = { TextButton(onClick = { confirmCleanUp = false }) { Text("Cancel") } },
            containerColor = k.surface,
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(22.dp)).background(Kept.colors.surface)) { content() }
}

@Composable
private fun Line() {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Kept.colors.line))
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, action: String?, onClick: () -> Unit) {
    val k = Kept.colors
    Row(
        Modifier.fillMaxWidth().keptClickable(pressed = 0.98f, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = k.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = k.muted, modifier = Modifier.padding(top = 1.dp))
        }
        action?.let {
            Spacer(Modifier.width(10.dp))
            Text(it, style = MaterialTheme.typography.labelLarge, color = k.accent, modifier = Modifier.clip(CircleShape).background(k.accentContainer).padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val k = Kept.colors
    Row(
        Modifier
            .fillMaxWidth()
            .keptClickable(pressed = 0.98f, role = Role.Switch) { onChange(!checked) }
            .semantics(mergeDescendants = true) { stateDescription = if (checked) "On" else "Off" }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = k.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = k.muted, modifier = Modifier.padding(top = 1.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = if (checked) ({ Icon(KeptIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = k.accent,
                checkedThumbColor = k.onAccent,
                checkedIconColor = k.accent,
                uncheckedTrackColor = k.surface2,
                uncheckedThumbColor = k.muted,
                uncheckedBorderColor = k.lineStrong,
            ),
        )
    }
}
