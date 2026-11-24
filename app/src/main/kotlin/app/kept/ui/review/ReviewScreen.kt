@file:OptIn(ExperimentalMaterial3Api::class)

package app.kept.ui.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kept.core.deadlines.Deadline
import app.kept.core.parser.model.Category
import app.kept.core.parser.model.Money
import app.kept.ui.theme.KeptColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class DateTarget { PURCHASE, RETURN }

private val DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
fun ReviewScreen(viewModel: ReviewViewModel, onSaved: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var datePicker by remember { mutableStateOf<DateTarget?>(null) }
    val form = state.form

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check receipt") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.preview?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Receipt photo",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                )
            }

            if (state.reading) {
                Text("Reading your receipt…", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                return@Column
            }
            if (state.readFailed) {
                Text(
                    "Couldn't read this receipt. It's saved; fill in the details below.",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            CheckedField("Store", form.merchant, viewModel::onMerchant, check = "merchant" in state.needsCheck)

            CheckedField(
                label = "Purchase date",
                value = form.purchaseDate?.format(DATE).orEmpty(),
                onValueChange = {},
                check = "purchaseDate" in state.needsCheck,
                readOnly = true,
                trailing = {
                    IconButton(onClick = { datePicker = DateTarget.PURCHASE }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Pick purchase date")
                    }
                },
                modifier = Modifier.clickable { datePicker = DateTarget.PURCHASE },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CheckedField(
                    "Total", form.total, viewModel::onTotal,
                    check = "total" in state.needsCheck,
                    keyboard = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                CheckedField(
                    "Currency", form.currency, viewModel::onCurrency,
                    check = "currency" in state.needsCheck,
                    modifier = Modifier.width(110.dp),
                )
            }

            SectionTitle("Category")
            ChipRow {
                Category.entries.filter { it != Category.OTHER }.forEach { c ->
                    FilterChip(
                        selected = form.category == c,
                        onClick = { viewModel.onCategory(c) },
                        label = { Text(c.name.lowercase().replaceFirstChar { it.titlecase() }) },
                    )
                }
            }

            SectionTitle("Return window")
            DeadlineSummary(form.returnDeadline, none = "No returns", unknown = "How long can you return it?")
            ChipRow {
                listOf(14, 30, 60, 90).forEach { d ->
                    FilterChip(selected = false, onClick = { viewModel.onReturnDays(d) }, label = { Text("$d days") })
                }
                FilterChip(
                    selected = form.returnDeadline is Deadline.NotAllowed,
                    onClick = viewModel::onNoReturns,
                    label = { Text("No returns") },
                )
                FilterChip(selected = false, onClick = { datePicker = DateTarget.RETURN }, label = { Text("Pick date") })
            }

            SectionTitle("Warranty")
            DeadlineSummary(form.warrantyDeadline, none = "No warranty", unknown = "No warranty set")
            ChipRow {
                listOf(12 to "1 year", 24 to "2 years", 36 to "3 years").forEach { (m, label) ->
                    FilterChip(selected = false, onClick = { viewModel.onWarrantyMonths(m) }, label = { Text(label) })
                }
                FilterChip(selected = false, onClick = viewModel::onNoWarranty, label = { Text("None") })
            }

            CheckedField("Serial number (optional)", form.serialNumber, viewModel::onSerial, check = false)

            if (form.items.isNotEmpty()) ItemsList(form.items, form.currency)

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (state.saving) "Saving…" else "Save") }
        }
    }

    datePicker?.let { target ->
        val initial = when (target) {
            DateTarget.PURCHASE -> form.purchaseDate
            DateTarget.RETURN -> (form.returnDeadline as? Deadline.On)?.date
        } ?: LocalDate.now()
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
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { datePicker = null }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

/**
 * A text field that turns amber with a warning icon and the word "Check" when the parser was unsure.
 * Colour is never the only signal, so it works for colour-blind users and with TalkBack.
 */
@Composable
private fun CheckedField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    check: Boolean,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val amber = KeptColors.checkAmber()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        readOnly = readOnly,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        trailingIcon = trailing,
        leadingIcon = if (check) ({ Icon(Icons.Filled.Warning, contentDescription = null, tint = amber) }) else null,
        supportingText = if (check) ({ Text("Check this", color = amber) }) else null,
        colors = if (check) {
            OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = amber,
                focusedBorderColor = amber,
                unfocusedLabelColor = amber,
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun DeadlineSummary(deadline: Deadline, none: String, unknown: String) {
    val (headline, detail) = when (deadline) {
        is Deadline.On -> deadline.date.format(DATE) to deadline.note
        is Deadline.NotAllowed -> none to null
        Deadline.Lifetime -> "Lifetime" to null
        Deadline.Unknown -> unknown to null
    }
    Column {
        Text(headline, style = MaterialTheme.typography.bodyLarge)
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ItemsList(items: List<app.kept.data.SnapshotItem>, currency: String) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide items" else "${items.size} item${if (items.size == 1) "" else "s"} read from the receipt")
        }
        if (expanded) {
            items.forEach { item ->
                val price = Money(item.totalMinor, currency.ifBlank { "USD" }).toDecimal().toPlainString()
                Text(
                    "${if (item.quantity > 1) "${item.quantity} × " else ""}${item.name}  ·  $price",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
