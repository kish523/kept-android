package app.kept.data

import app.kept.core.parser.model.Category
import app.kept.core.parser.model.Field
import app.kept.core.parser.model.ParsedReceipt
import app.kept.core.parser.model.ReturnHint
import app.kept.core.parser.model.WarrantyHint
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * What the review card needs from a parse, stored with the receipt so a crash between "scan" and
 * "save" loses nothing: field confidences, return / warranty hints, the suggested category, items.
 */
data class ExtractionSnapshot(
    val confidences: Map<String, Float>,
    val needsReview: Set<String>,
    val returnHint: ReturnHint?,
    val warranty: WarrantyHint?,
    val category: Category?,
    val serials: List<String>,
    val items: List<SnapshotItem>,
    val warnings: List<String>,
)

data class SnapshotItem(val name: String, val quantity: Int, val totalMinor: Long, val unitMinor: Long?)

object ExtractionJson {

    fun encode(p: ParsedReceipt): String {
        val conf = JSONObject()
        fun put(name: String, f: Field<*>?) { if (f != null) conf.put(name, f.confidence.toDouble()) }
        put("merchant", p.merchant)
        put("purchaseDate", p.purchaseDate)
        put("currency", p.currency)
        put("total", p.total)
        put("tax", p.tax)
        put("payment", p.payment)
        put("orderNumber", p.orderNumber)
        put("returnHint", p.returnHint)
        put("warranty", p.warranty)
        put("category", p.suggestedCategory)

        return JSONObject()
            .put("confidences", conf)
            .put("needsReview", JSONArray(p.fieldsNeedingReview))
            .put("returnHint", when (val h = p.returnHint?.value) {
                is ReturnHint.ByDate -> "date:${h.date}"
                is ReturnHint.WithinDays -> "days:${h.days}"
                ReturnHint.NoReturns -> "none"
                null -> JSONObject.NULL
            })
            .put("warranty", when (val w = p.warranty?.value) {
                is WarrantyHint.Months -> "months:${w.months}"
                WarrantyHint.Lifetime -> "lifetime"
                null -> JSONObject.NULL
            })
            .put("category", p.suggestedCategory?.value?.name ?: JSONObject.NULL)
            .put("serials", JSONArray(p.serialNumbers.map { it.value }))
            .put("items", JSONArray(p.items.map {
                JSONObject()
                    .put("name", it.name)
                    .put("quantity", it.quantity)
                    .put("totalMinor", it.totalPrice.minorUnits)
                    .put("unitMinor", it.unitPrice?.minorUnits ?: JSONObject.NULL)
            }))
            .put("warnings", JSONArray(p.warnings))
            .toString()
    }

    fun decode(json: String): ExtractionSnapshot {
        val o = JSONObject(json)
        val conf = o.getJSONObject("confidences")
        val items = o.getJSONArray("items")
        return ExtractionSnapshot(
            confidences = conf.keys().asSequence().associateWith { conf.getDouble(it).toFloat() },
            needsReview = o.getJSONArray("needsReview").strings().toSet(),
            returnHint = o.optStringOrNull("returnHint")?.let { s ->
                when {
                    s == "none" -> ReturnHint.NoReturns
                    s.startsWith("date:") -> ReturnHint.ByDate(LocalDate.parse(s.removePrefix("date:")))
                    s.startsWith("days:") -> ReturnHint.WithinDays(s.removePrefix("days:").toInt())
                    else -> null
                }
            },
            warranty = o.optStringOrNull("warranty")?.let { s ->
                when {
                    s == "lifetime" -> WarrantyHint.Lifetime
                    s.startsWith("months:") -> WarrantyHint.Months(s.removePrefix("months:").toInt())
                    else -> null
                }
            },
            category = o.optStringOrNull("category")?.let { runCatching { Category.valueOf(it) }.getOrNull() },
            serials = o.optJSONArray("serials")?.strings().orEmpty(),
            items = (0 until items.length()).map { i ->
                val it = items.getJSONObject(i)
                SnapshotItem(
                    name = it.getString("name"),
                    quantity = it.getInt("quantity"),
                    totalMinor = it.getLong("totalMinor"),
                    unitMinor = if (it.isNull("unitMinor")) null else it.getLong("unitMinor"),
                )
            },
            warnings = o.optJSONArray("warnings")?.strings().orEmpty(),
        )
    }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
}
