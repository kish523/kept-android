package app.kept.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class StoredFile(val relativePath: String, val sha256: String, val pageIndex: Int, val isPdf: Boolean)

/**
 * Receipt files live in app-private storage under filesDir/receipts/<receiptId>/.
 * Every write goes to a temp file, is fsync'd, then renamed, so a crash or a dead battery never
 * leaves a half-written image behind.
 */
class AttachmentStore(private val context: Context) {
    private val root = File(context.filesDir, ROOT)

    suspend fun importImage(receiptId: String, pageIndex: Int, source: Uri): StoredFile =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(source)
                ?: error("Can't open $source")
            val name = "page-$pageIndex.jpg"
            val sha = input.use { writeAtomically(receiptId, name, it) }
            StoredFile("$ROOT/$receiptId/$name", sha, pageIndex, isPdf = false)
        }

    /**
     * Keeps the original PDF and renders each page to an image, so OCR and the receipt viewer only
     * ever deal with images. Rendered at 2x for sharper text recognition, capped at 10 pages.
     */
    suspend fun importPdf(receiptId: String, source: Uri): List<StoredFile> = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(source) ?: error("Can't open $source")
        val originalSha = input.use { writeAtomically(receiptId, "original.pdf", it) }
        val stored = mutableListOf(StoredFile("$ROOT/$receiptId/original.pdf", originalSha, -1, isPdf = true))

        val pdfFile = File(dir(receiptId), "original.pdf")
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                for (i in 0 until minOf(renderer.pageCount, MAX_PDF_PAGES)) {
                    renderer.openPage(i).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val name = "page-$i.jpg"
                        val bytes = java.io.ByteArrayOutputStream().also {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                        }.toByteArray()
                        bitmap.recycle()
                        val sha = writeAtomically(receiptId, name, bytes.inputStream())
                        stored += StoredFile("$ROOT/$receiptId/$name", sha, i, isPdf = false)
                    }
                }
            }
        }
        stored
    }

    fun resolve(relativePath: String): File = File(context.filesDir, relativePath)

    suspend fun deleteReceipt(receiptId: String) = withContext(Dispatchers.IO) {
        dir(receiptId).deleteRecursively()
    }

    private fun dir(receiptId: String) = File(root, receiptId).apply { mkdirs() }

    private fun writeAtomically(receiptId: String, name: String, input: InputStream): String {
        val target = File(dir(receiptId), name)
        val tmp = File(target.parentFile, "$name.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        FileOutputStream(tmp).use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
                out.write(buffer, 0, n)
            }
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
            error("Couldn't save $name")
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val ROOT = "receipts"
        private const val MAX_PDF_PAGES = 10
    }
}
