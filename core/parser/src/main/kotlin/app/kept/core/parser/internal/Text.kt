package app.kept.core.parser.internal

internal fun String.letterCount(): Int = count { it.isLetter() }

internal fun String.normalizeSpaces(): String = trim().replace(WHITESPACE, " ")

/** Lower-case letters and digits only: "Lanka Home-Centre" -> "lankahomecentre". */
internal fun normalizeKey(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

private val WHITESPACE = Regex("\\s+")

private val LEGAL_SUFFIX = Regex(
    """[\s,]+(?:\(?pvt\.?\)?\s*ltd\.?|\(?private\)?\s+limited|pty\.?\s*ltd\.?|ltd\.?|limited|llc\.?|inc\.?|plc\.?|gmbh|co\.)$""",
    RegexOption.IGNORE_CASE,
)

/** "LANKA HOME CENTRE (PVT) LTD" -> "LANKA HOME CENTRE". */
internal fun stripLegalSuffix(s: String): String {
    var r = s.trim()
    repeat(2) { r = r.replace(LEGAL_SUFFIX, "").trim() }
    return r.trimEnd(',', '.', '-', '!', ':').trim()
}

/** Title-cases text that is entirely upper case; leaves mixed-case names alone. */
internal fun smartTitleCase(s: String): String {
    val letters = s.filter { it.isLetter() }
    if (letters.isEmpty() || letters.any { it.isLowerCase() }) return s
    return s.split(" ").joinToString(" ") { word ->
        if (word.length <= 1 || word.any { it.isDigit() }) word
        else word.lowercase().replaceFirstChar { it.titlecase() }
    }
}

internal fun wordRegex(words: List<String>): Regex =
    Regex("""\b(?:${words.joinToString("|") { Regex.escape(it) }})\b""", RegexOption.IGNORE_CASE)
