package app.kept.update

/** A piece of a changelog section, for the "What's new" sheet. */
sealed interface NoteBlock {
    data class Heading(val text: String) : NoteBlock
    data class Bullet(val text: String) : NoteBlock
    data class Paragraph(val text: String) : NoteBlock
}

/**
 * The blocks of the `## <version>` section of CHANGELOG.md (bundled into the app at build time).
 * Understands what the changelog uses: `**Heading**` lines, `- ` bullets with indented continuation
 * lines, and plain paragraphs. `**bold**` markers stay in the text for the UI to render.
 */
fun changelogSection(markdown: String, version: String): List<NoteBlock> {
    val lines = markdown.lines()
    val start = lines.indexOfFirst { it.startsWith("## ") && it.removePrefix("## ").trim().let { h -> h == version || h.startsWith("$version:") || h.startsWith("$version ") } }
    if (start < 0) return emptyList()
    val body = lines.drop(start + 1).takeWhile { !it.startsWith("## ") }
    val blocks = mutableListOf<NoteBlock>()
    var bullet: StringBuilder? = null
    var para: StringBuilder? = null
    fun flush() {
        bullet?.let { blocks += NoteBlock.Bullet(it.toString().trim()) }
        para?.let { blocks += NoteBlock.Paragraph(it.toString().trim()) }
        bullet = null
        para = null
    }
    for (raw in body) {
        val line = raw.trimEnd()
        when {
            line.isBlank() -> flush()
            line.startsWith("- ") -> { flush(); bullet = StringBuilder(line.removePrefix("- ")) }
            line.startsWith("  ") && bullet != null -> bullet!!.append(' ').append(line.trim())
            Regex("""^\*\*[^*]+\*\*$""").matches(line.trim()) -> { flush(); blocks += NoteBlock.Heading(line.trim().removeSurrounding("**")) }
            else -> (para ?: StringBuilder().also { para = it }).let { if (it.isNotEmpty()) it.append(' '); it.append(line.trim()) }
        }
    }
    flush()
    return blocks
}

/** The section's title after the version, e.g. "the redesign" from "## 0.2.0: the redesign". */
fun changelogTitle(markdown: String, version: String): String? =
    markdown.lines().firstOrNull { it.startsWith("## $version") }?.substringAfter(":", "")?.trim()?.ifEmpty { null }
