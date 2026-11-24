@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package app.kept.ui.review

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth as fillWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kept.core.deadlines.Deadline
import app.kept.core.deadlines.DeadlineSource
import app.kept.core.deadlines.ReminderKind
import app.kept.core.deadlines.ReminderPlanner
import app.kept.core.parser.model.Category
import app.kept.core.parser.model.Money
import app.kept.ui.components.BouncingDots
import app.kept.ui.components.IconTile
import app.kept.ui.components.KeptChip
import app.kept.ui.components.SectionLabel
import app.kept.ui.components.Skeleton
import app.kept.ui.components.attentionPulse
import app.kept.ui.components.enterRise
import app.kept.ui.components.keptClickable
import app.kept.ui.theme.Kept
import app.kept.ui.theme.KeptIcons
import app.kept.ui.theme.KeptMotion
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private enum class DateTarget { PURCHASE, RETURN }

private val DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/** Fields the parser can flag, in the order they appear on the card. */
private val CHECKABLE = listOf("merchant", "purchaseDate", "total", "currency")

@Composable
fun ReviewScreen(viewModel: ReviewViewModel, onSaved: (String) -> Unit, onBack: () -> Unit, onDelete: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val k = Kept.colors
    val form = state.form
    val haze = remember { HazeState() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var datePicker by remember { mutableStateOf<DateTarget?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var quickDates by rememberSaveable { mutableStateOf(false) }
    val requesters = remember { CHECKABLE.associateWith { BringIntoViewRequester() } }
    val toCheck = CHECKABLE.filter { it in state.needsCheck }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val today = LocalDate.now()

    Box(Modifier.fillMaxSize().background(k.background).imePadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .hazeSource(haze)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(top = top + 72.dp, bottom = bottom + 124.dp),
        ) {
            PhotoCard(state.preview, reading = state.reading)

            if (state.reading) {
                Row(Modifier.padding(top = 20.dp, start = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BouncingDots(k.accent)
                    Spacer(Modifier.width(12.dp))
                    Text("Reading your receipt…", style = MaterialTheme.typography.titleSmall, color = k.ink)
                }
                repeat(5) { i -> Skeleton(Modifier.padding(top = 10.dp).fillMaxWidth(if (i == 3) 0.7f else 1f), height = if (i == 3) 40.dp else 58.dp) }
                return@Column
            }

            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.readFailed) {
                    Notice("Couldn't read this receipt. It's saved, so fill in the details below.", Modifier.enterRise(0, baseMs = 0))
                }

                Card(Modifier.enterRise(0, baseMs = 0)) {
                    KeptField(
                        label = "Store",
                        value = form.merchant,
                        onValueChange = viewModel::onMerchant,
                        check = "merchant" in state.needsCheck,
                        note = "Check this. The store name was hard to read.",
                        modifier = Modifier.bringIntoViewRequester(requesters.getValue("merchant")),
                    )
                    Divider()
                    DateRow(
                        date = form.purchaseDate,
                        today = today,
                        expanded = quickDates,
                        check = "purchaseDate" in state.needsCheck,
                        onToggle = { quickDates = !quickDates },
                        modifier = Modifier.bringIntoViewRequester(requesters.getValue("purchaseDate")),
                    )
                    AnimatedVisibility(quickDates, enter = expandVertically(tween(320, easing = KeptMotion.Settle)) + fadeIn(), exit = shrinkVertically(tween(260, easing = KeptMotion.Settle)) + fadeOut()) {
                        val ago = form.purchaseDate?.let { ChronoUnit.DAYS.between(it, today) }
                        FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0L to "Today", 1L to "Yesterday", 2L to "2 days ago", 7L to "A week ago").forEach { (n, label) ->
                                KeptChip(label, selected = ago == n, onClick = { viewModel.onDate(today.minusDays(n)); quickDates = false })
                            }
                            KeptChip("Pick a date…", selected = false, onClick = { datePicker = DateTarget.PURCHASE })
                        }
                    }
                    Divider()
                    val moneyCheck = "total" in state.needsCheck || "currency" in state.needsCheck
                    val moneyBg by animateColorAsState(if (moneyCheck) k.checkContainer else Color.Transparent, tween(300), label = "moneyBg")
                    Row(
                        Modifier
                            .background(moneyBg)
                            .attentionPulse(moneyCheck, k.check)
                            .bringIntoViewRequester(requesters.getValue("total")),
                    ) {
                        KeptField(
                            label = "Total",
                            value = form.total,
                            onValueChange = viewModel::onTotal,
                            check = "total" in state.needsCheck,
                            note = "Check this. The total was hard to read.",
                            tint = false,
                            keyboard = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f),
                        )
                        KeptField(
                            label = "Currency",
                            value = form.currency,
                            onValueChange = viewModel::onCurrency,
                            check = "currency" in state.needsCheck,
                            note = null,
                            tint = false,
                            modifier = Modifier.width(108.dp).bringIntoViewRequester(requesters.getValue("currency")),
                        )
                    }
                }

                Column(Modifier.enterRise(1, baseMs = 0, stepMs = 70)) {
                    SectionLabel("Category")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Category.entries.filter { it != Category.OTHER }.forEach { c ->
                            KeptChip(c.name.lowercase().replaceFirstChar { it.titlecase() }, form.category == c, { viewModel.onCategory(c) })
                        }
                    }
                }

                ReturnCard(form, today, viewModel, onPickDate = { datePicker = DateTarget.RETURN }, modifier = Modifier.enterRise(2, baseMs = 0, stepMs = 70))
                WarrantyCard(form, today, viewModel, modifier = Modifier.enterRise(3, baseMs = 0, stepMs = 70))

                Card(Modifier.enterRise(4, baseMs = 0, stepMs = 70)) {
                    KeptField("Serial number (optional)", form.serialNumber, viewModel::onSerial, check = false, placeholder = "Add it for warranty claims")
                }

                if (form.items.isNotEmpty()) ItemsList(form.items, form.currency, Modifier.enterRise(5, baseMs = 0, stepMs = 70))

                state.error?.let { Notice(it) }
            }
        }

        // Frosted top bar: back, title, and how many fields need a look (tap to jump there).
        Row(
            Modifier
                .fillMaxWidth()
                .hazeEffect(haze, k.glassBar)
                .drawBehind { drawLine(k.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                .padding(top = top)
                .height(64.dp)
                .padding(start = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(KeptIcons.Back, contentDescription = "Back", tint = k.ink) }
            Text(
                if (state.editing) "Receipt" else "Check receipt",
                style = MaterialTheme.typography.titleMedium,
                color = k.ink,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(!state.reading && toCheck.isNotEmpty(), enter = fadeIn() + scaleIn(initialScale = 0.6f, animationSpec = KeptMotion.pop()), exit = fadeOut()) {
                Row(
                    Modifier
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(k.checkContainer)
                        .keptClickable(pressed = 0.94f) {
                            scope.launch { requesters[toCheck.firstOrNull() ?: return@launch]?.bringIntoView() }
                        }
                        .semantics { contentDescription = "${toCheck.size} to check. Go to the first one." }
                        .padding(start = 10.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(KeptIcons.Warning, contentDescription = null, tint = k.check, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${toCheck.size} to check", style = MaterialTheme.typography.labelMedium, color = k.check)
                }
            }
            if (!state.reading) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(KeptIcons.Trash, contentDescription = "Delete receipt", tint = k.muted)
                }
            }
        }

        // Frosted bottom bar with the Save button that morphs into a spinner, then a check.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .hazeEffect(haze, k.glassBar)
                .drawBehind { drawLine(k.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = bottom + 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            SaveButton(
                label = if (state.editing) "Save changes" else "Save receipt",
                enabled = state.canSave || state.saving || state.saved,
                saving = state.saving,
                saved = state.saved,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.save(onSaved)
                },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this receipt?") },
            text = { Text("The photo and details are removed from this phone, and its reminders stop. You can undo right after.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = k.soon) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            containerColor = k.surface,
        )
    }

    datePicker?.let { target ->
        val initial = when (target) {
            DateTarget.PURCHASE -> form.purchaseDate
            DateTarget.RETURN -> (form.returnDeadline as? Deadline.On)?.date
        } ?: today
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePicker = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (target == DateTarget.PURCHASE) viewModel.onDate(picked) else viewModel.onReturnDate(picked)
                    }
                    datePicker = null
                    quickDates = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { datePicker = null }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Kept.colors.surface)) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Kept.colors.line))
}

@Composable
private fun Notice(text: String, modifier: Modifier = Modifier) {
    val k = Kept.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(k.checkContainer).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KeptIcons.Warning, contentDescription = null, tint = k.check, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = k.check)
    }
}

/** The receipt photo, with a scan line sweeping over it while it's being read. */
@Composable
private fun PhotoCard(preview: Bitmap?, reading: Boolean) {
    val k = Kept.colors
    val sweep = rememberInfiniteTransition(label = "sweep")
    val y by sweep.animateFloat(-0.35f, 1.05f, infiniteRepeatable(tween(1500, easing = KeptMotion.Emphasized)), label = "y")
    val lineAlpha by animateFloatAsState(if (reading) 1f else 0f, tween(400), label = "lineAlpha")
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(k.surface2)
            .semantics { contentDescription = "Receipt photo" },
        contentAlignment = Alignment.Center,
    ) {
        if (preview != null) {
            Image(preview.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
        } else {
            Icon(KeptIcons.forCategory(null), contentDescription = null, tint = k.muted, modifier = Modifier.size(40.dp))
        }
        if (lineAlpha > 0f) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val band = 70.dp.toPx()
                    val lineY = size.height * y
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, k.accent.copy(alpha = 0.32f * lineAlpha)), startY = lineY - band, endY = lineY),
                        topLeft = Offset(0f, lineY - band),
                        size = androidx.compose.ui.geometry.Size(size.width, band),
                    )
                    drawLine(k.accent.copy(alpha = lineAlpha), Offset(0f, lineY), Offset(size.width, lineY), 2.dp.toPx())
                },
            )
        }
    }
}

/**
 * A text field that sits inside a card. When the parser was unsure it turns amber, gets a warning
 * icon and the words "Check this", and pulses once. Colour is never the only signal.
 */
@Composable
private fun KeptField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    check: Boolean,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    note: String? = "Check this. Kept wasn't sure it read this right.",
    tint: Boolean = check,
) {
    val k = Kept.colors
    val bg by animateColorAsState(if (tint) k.checkContainer else Color.Transparent, tween(300), label = "bg")
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = k.muted) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = MaterialTheme.typography.bodyLarge,
        leadingIcon = if (check) ({ Icon(KeptIcons.Warning, contentDescription = null, tint = k.check, modifier = Modifier.size(20.dp)) }) else null,
        supportingText = if (check && note != null) ({ Text(note, color = k.check) }) else null,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLabelColor = if (check) k.check else k.accent,
            unfocusedLabelColor = if (check) k.check else k.muted,
            focusedTextColor = k.ink,
            unfocusedTextColor = k.ink,
            cursorColor = k.accent,
        ),
        modifier = modifier.fillMaxWidth().background(bg).attentionPulse(tint, k.check),
    )
}

@Composable
private fun DateRow(date: LocalDate?, today: LocalDate, expanded: Boolean, check: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val k = Kept.colors
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(400, easing = KeptMotion.Settle), label = "chev")
    val bg by animateColorAsState(if (check) k.checkContainer else Color.Transparent, tween(300), label = "bg")
    val ago = date?.let { ChronoUnit.DAYS.between(it, today) }
    val value = date?.let { d -> d.format(DATE) + when (ago) { 0L -> " · today"; 1L -> " · yesterday"; else -> "" } } ?: "Tap to set"
    Row(
        modifier
            .fillMaxWidth()
            .background(bg)
            .attentionPulse(check, k.check)
            .keptClickable(pressed = 0.99f, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = "Purchase date, $value"
                stateDescription = if (expanded) "Quick dates shown" else "Quick dates hidden"
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (check) {
            Icon(KeptIcons.Warning, contentDescription = null, tint = k.check, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Purchase date", style = MaterialTheme.typography.bodySmall, color = if (check) k.check else k.muted)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = k.ink, modifier = Modifier.padding(top = 2.dp))
            if (check) Text("Check this. The date was hard to read.", style = MaterialTheme.typography.bodySmall, color = k.check, modifier = Modifier.padding(top = 4.dp))
        }
        Icon(KeptIcons.ChevronDown, contentDescription = null, tint = if (expanded) k.accent else k.muted, modifier = Modifier.rotate(turn))
    }
}

private fun sourceLabel(d: Deadline): String? = when (d) {
    is Deadline.On -> when (d.source) {
        DeadlineSource.RECEIPT_TEXT, DeadlineSource.RECEIPT_DATE -> "From the receipt"
        DeadlineSource.MERCHANT_POLICY -> "Store policy"
        DeadlineSource.CATEGORY_DEFAULT -> "Typical for this kind of item"
        DeadlineSource.USER -> "Set by you"
    }
    is Deadline.NotAllowed -> if (d.source == DeadlineSource.USER) "Set by you" else "From the receipt"
    else -> null
}

@Composable
private fun DeadlineHeader(icon: ImageVector, kind: String, title: String, subtitle: String?, source: String?) {
    val k = Kept.colors
    Row(verticalAlignment = Alignment.Top) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kind, style = MaterialTheme.typography.bodySmall, color = k.muted)
                source?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing), color = k.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(CircleShape).background(k.surface2).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            AnimatedContent(title, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "title") { t ->
                Text(t, style = MaterialTheme.typography.titleLarge, color = k.ink, modifier = Modifier.padding(top = 2.dp))
            }
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = k.muted, modifier = Modifier.padding(top = 1.dp)) }
        }
    }
}

@Composable
private fun ReminderLine(text: String) {
    val k = Kept.colors
    Row(
        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(k.accentContainer).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KeptIcons.Bell, contentDescription = null, tint = k.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = k.ink)
    }
}

private fun reminderText(kind: ReminderKind, due: LocalDate): String? {
    val dates = ReminderPlanner.plan(kind, due, LocalDateTime.now()).map { it.fireAt.toLocalDate().format(SHORT) }.distinct()
    if (dates.isEmpty()) return null
    return "Reminders on ${joinDates(dates)} at 9:00"
}

@Composable
private fun ReturnCard(form: ReviewForm, today: LocalDate, vm: ReviewViewModel, onPickDate: () -> Unit, modifier: Modifier) {
    val d = form.returnDeadline
    val bought = form.purchaseDate
    val presetDays = listOf(14, 30, 60, 90)
    val selectedDays = (d as? Deadline.On)?.let { on -> presetDays.firstOrNull { bought?.plusDays(it.toLong()) == on.date } }
    val (title, subtitle) = when (d) {
        is Deadline.On -> {
            val left = ChronoUnit.DAYS.between(today, d.date)
            val span = bought?.let { ChronoUnit.DAYS.between(it, d.date) }
            (if (left >= 0) "Return by ${d.date.format(DATE)}" else "Closed on ${d.date.format(DATE)}") to listOfNotNull(
                span?.let { "$it days from purchase" },
                when { left < 0 -> null; left == 0L -> "last day today"; left == 1L -> "1 day left"; else -> "$left days left" },
            ).joinToString(" · ").ifEmpty { d.note }
        }
        is Deadline.NotAllowed -> "No returns" to "Marked as final sale"
        Deadline.Lifetime -> "No time limit" to null
        Deadline.Unknown -> "How long can you return it?" to "Pick one below"
    }
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            DeadlineHeader(KeptIcons.ReturnArrow, "Return window", title, subtitle, sourceLabel(d))
            FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presetDays.forEach { n -> KeptChip("$n days", selectedDays == n, { vm.onReturnDays(n) }) }
                KeptChip("No returns", d is Deadline.NotAllowed, vm::onNoReturns)
                KeptChip(
                    if (d is Deadline.On && selectedDays == null) d.date.format(SHORT) else "Pick date",
                    d is Deadline.On && selectedDays == null,
                    onPickDate,
                )
            }
            val reminders = (d as? Deadline.On)?.let { reminderText(ReminderKind.RETURN, it.date) }
            AnimatedVisibility(reminders != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                ReminderLine(reminders.orEmpty())
            }
        }
    }
}

@Composable
private fun WarrantyCard(form: ReviewForm, today: LocalDate, vm: ReviewViewModel, modifier: Modifier) {
    val d = form.warrantyDeadline
    val bought = form.purchaseDate
    val presets = listOf(12 to "1 year", 24 to "2 years", 36 to "3 years")
    val selected = (d as? Deadline.On)?.let { on -> presets.firstOrNull { bought?.plusMonths(it.first.toLong()) == on.date }?.first }
    val (title, subtitle) = when (d) {
        is Deadline.On -> (if (!d.date.isBefore(today)) "Covered until ${d.date.format(DATE)}" else "Ended ${d.date.format(DATE)}") to
            (selected?.let { m -> presets.first { it.first == m }.second + " from purchase" } ?: d.note)
        Deadline.Lifetime -> "Lifetime warranty" to null
        else -> "No warranty" to "Pick a length if it came with one"
    }
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            DeadlineHeader(KeptIcons.Shield, "Warranty", title, subtitle, sourceLabel(d).takeIf { d is Deadline.On })
            FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (m, label) -> KeptChip(label, selected == m, { vm.onWarrantyMonths(m) }) }
                KeptChip("None", d is Deadline.Unknown && form.warrantySetByUser, vm::onNoWarranty)
            }
            val reminders = (d as? Deadline.On)?.let { reminderText(ReminderKind.WARRANTY, it.date) }
            AnimatedVisibility(reminders != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                ReminderLine(reminders.orEmpty())
            }
        }
    }
}

@Composable
private fun ItemsList(items: List<app.kept.data.SnapshotItem>, currency: String, modifier: Modifier) {
    val k = Kept.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(400, easing = KeptMotion.Settle), label = "chev")
    Card(modifier) {
        Row(
            Modifier.fillMaxWidth().keptClickable(pressed = 0.99f, role = Role.Button) { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${items.size} item${if (items.size == 1) "" else "s"} read from the receipt", style = MaterialTheme.typography.titleSmall, color = k.ink, modifier = Modifier.weight(1f))
            Icon(KeptIcons.ChevronDown, contentDescription = if (expanded) "Hide items" else "Show items", tint = k.muted, modifier = Modifier.rotate(turn))
        }
        AnimatedVisibility(expanded, enter = expandVertically(tween(320, easing = KeptMotion.Settle)) + fadeIn(), exit = shrinkVertically(tween(260, easing = KeptMotion.Settle)) + fadeOut()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                items.forEach { item ->
                    val price = Money(item.totalMinor, currency.ifBlank { "USD" }).toDecimal().toPlainString()
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("${if (item.quantity > 1) "${item.quantity} × " else ""}${item.name}", style = MaterialTheme.typography.bodyMedium, color = k.ink, modifier = Modifier.weight(1f))
                        Text(price, style = MaterialTheme.typography.bodyMedium, color = k.muted)
                    }
                }
            }
        }
    }
}

/** Full-width Save that shrinks into a round spinner while saving, then shows a check before closing. */
@Composable
private fun SaveButton(label: String, enabled: Boolean, saving: Boolean, saved: Boolean, onClick: () -> Unit) {
    val k = Kept.colors
    BoxWithConstraints(Modifier.fillWidth(), contentAlignment = Alignment.Center) {
        val busy = saving || saved
        val width by animateDpAsState(if (busy) 56.dp else maxWidth, tween(550, easing = KeptMotion.Settle), label = "w")
        val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "a")
        Box(
            Modifier
                .width(width)
                .height(56.dp)
                .clip(CircleShape)
                .background(k.accent.copy(alpha = alpha))
                .keptClickable(pressed = 0.97f, enabled = enabled && !busy, onClick = onClick)
                .semantics { if (busy) stateDescription = if (saved) "Saved" else "Saving" },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = when { saved -> 2; saving -> 1; else -> 0 },
                transitionSpec = { (fadeIn(tween(180)) + scaleIn(initialScale = 0.5f, animationSpec = KeptMotion.pop())) togetherWith fadeOut(tween(120)) },
                label = "save",
            ) { phase ->
                when (phase) {
                    2 -> Icon(KeptIcons.Check, contentDescription = null, tint = k.onAccent, modifier = Modifier.size(26.dp))
                    1 -> CircularProgressIndicator(color = k.onAccent, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                    else -> Text(label, style = MaterialTheme.typography.titleSmall, color = k.onAccent, maxLines = 1)
                }
            }
        }
    }
}
