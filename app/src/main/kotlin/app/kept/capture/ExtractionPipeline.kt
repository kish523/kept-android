package app.kept.capture

import app.kept.core.parser.CardNumberRedactor
import app.kept.core.parser.ParserConfig
import app.kept.core.parser.ReceiptParser
import app.kept.data.ExtractionJson
import app.kept.data.ReceiptRepository
import app.kept.data.db.AttachmentKind
import app.kept.data.db.ExtractionResultEntity
import app.kept.data.db.ExtractionStatus
import app.kept.data.db.ExtractionTier
import app.kept.data.db.KeptDatabase
import app.kept.data.db.ReceiptStatus
import app.kept.data.files.AttachmentStore
import kotlin.coroutines.cancellation.CancellationException

/**
 * Draft -> OCR -> rules parser -> pre-filled draft. Tier 1 of the extraction plan.
 *
 * Next tiers plug in here: on phones with Gemini Nano, also send the text to the ML Kit Prompt API
 * and let the rules parser check its answer; if the user opted in, fall back to cloud reading.
 */
class ExtractionPipeline(
    private val db: KeptDatabase,
    private val files: AttachmentStore,
    private val ocr: OcrEngine,
    private val repository: ReceiptRepository,
    private val config: () -> ParserConfig,
) {
    suspend fun run(receiptId: String) {
        val job = ExtractionResultEntity(
            receiptId = receiptId,
            tier = ExtractionTier.RULES,
            status = ExtractionStatus.RUNNING,
            attempts = 1,
        )
        db.extractionDao().insert(job)
        try {
            val pages = db.attachmentDao().forReceipt(receiptId).filter { it.kind == AttachmentKind.PAGE_IMAGE }
            val document = ocr.recognize(pages.map { files.resolve(it.relativePath) })

            // Store the recognised text per page, redacted, for search and for re-parsing later.
            pages.forEachIndexed { i, page ->
                val text = document.lines.filter { it.page == i }.joinToString("\n") { CardNumberRedactor.redact(it.text) }
                db.attachmentDao().setOcrText(page.id, text)
            }

            val parsed = ReceiptParser(config()).parse(document)
            repository.applyExtraction(receiptId, parsed)
            db.extractionDao().update(job.copy(status = ExtractionStatus.DONE, fieldsJson = ExtractionJson.encode(parsed)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The draft and its images are safe; the user can type the fields in by hand.
            db.extractionDao().update(job.copy(status = ExtractionStatus.FAILED, lastError = e.message ?: e.toString()))
            db.receiptDao().setStatus(receiptId, ReceiptStatus.NEEDS_REVIEW)
        }
    }
}
