package app.kept.core.parser.internal

import app.kept.core.parser.model.OcrDocument
import app.kept.core.parser.model.OcrLine
import kotlin.math.abs
import kotlin.math.min

/** One visual row of the receipt: label and price joined even when OCR split them apart. */
internal data class Row(
    val index: Int,
    val page: Int,
    val text: String,
    val confidence: Float?,
) {
    /** Lines with weak OCR confidence make every field read from them less certain. */
    val ocrFactor: Float get() = if (confidence != null && confidence < 0.6f) 0.8f else 1f
}

/**
 * Receipts are two-column layouts, and OCR engines often return "TOTAL" and "54.20" as separate
 * lines or even separate paragraphs. When positions are known, lines whose vertical centres line
 * up are merged into one row, left to right, joined by two spaces so columns stay visible.
 */
internal object RowBuilder {
    fun build(doc: OcrDocument): List<Row> {
        val out = mutableListOf<Row>()
        doc.lines.groupBy { it.page }.toSortedMap().forEach { (page, lines) ->
            val positioned = lines.all { it.box != null }
            val groups = if (positioned) groupByBaseline(lines) else lines.map { listOf(it) }
            for (group in groups) {
                val text = group.sortedBy { it.box?.left ?: 0 }
                    .joinToString("  ") { it.text.trim() }
                    .trim()
                if (text.isEmpty()) continue
                out += Row(out.size, page, text, group.mapNotNull { it.confidence }.minOrNull())
            }
        }
        return out
    }

    private fun groupByBaseline(lines: List<OcrLine>): List<List<OcrLine>> {
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in lines.sortedBy { it.box!!.centerY }) {
            val box = line.box!!
            val current = rows.lastOrNull()
            if (current != null) {
                val rowCenter = current.map { it.box!!.centerY }.average().toFloat()
                val rowHeight = current.map { it.box!!.height }.average().toFloat()
                if (abs(box.centerY - rowCenter) <= 0.5f * min(rowHeight, box.height.toFloat())) {
                    current += line
                    continue
                }
            }
            rows += mutableListOf(line)
        }
        return rows
    }
}
