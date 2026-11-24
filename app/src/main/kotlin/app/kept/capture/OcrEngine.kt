package app.kept.capture

import android.content.Context
import android.net.Uri
import app.kept.core.parser.model.Box
import app.kept.core.parser.model.OcrDocument
import app.kept.core.parser.model.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device text recognition with ML Kit (Latin script). Lines keep their positions so the parser
 * can rebuild receipt rows even when OCR splits a label from its price.
 *
 * For Chinese, Japanese, Korean or Devanagari receipts, add the matching ML Kit recogniser and
 * pick it from the phone's locale.
 */
class OcrEngine(private val context: Context) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(pages: List<File>): OcrDocument = withContext(Dispatchers.Default) {
        val lines = mutableListOf<OcrLine>()
        pages.forEachIndexed { pageIndex, file ->
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val text = recognizer.process(image).await()
            for (block in text.textBlocks) {
                for (line in block.lines) {
                    val r = line.boundingBox
                    lines += OcrLine(
                        text = line.text,
                        box = r?.let { Box(it.left, it.top, it.right, it.bottom) },
                        page = pageIndex,
                        // 0 means "not available" (older Play services), not "certainly wrong".
                        confidence = line.confidence.takeIf { it > 0f },
                    )
                }
            }
        }
        OcrDocument(lines)
    }
}
