package app.kept.core.parser.model

/** Pixel rectangle of a recognised line, in page coordinates. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = (bottom - top).coerceAtLeast(1)
    val centerY: Float get() = (top + bottom) / 2f
}

/** One line of recognised text, as produced by ML Kit or read from a PDF / email text layer. */
data class OcrLine(
    val text: String,
    val box: Box? = null,
    val page: Int = 0,
    /** Recogniser confidence 0..1, when the engine reports one. */
    val confidence: Float? = null,
)

data class OcrDocument(val lines: List<OcrLine>) {
    companion object {
        /** Digital receipts (PDF text layer, email body): one line per text line, no positions. */
        fun fromPlainText(text: String): OcrDocument = OcrDocument(
            text.lineSequence()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
                .map { OcrLine(it) }
                .toList(),
        )
    }
}
