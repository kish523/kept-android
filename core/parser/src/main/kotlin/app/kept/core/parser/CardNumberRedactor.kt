package app.kept.core.parser

/**
 * Replaces anything that looks like a full payment card number with "•••• 1234" before the text is
 * parsed or stored. A run of 13-19 digits is treated as a card number when it starts with a card
 * network prefix (2-6) and passes the Luhn check, so long order numbers starting 1, 7, 8 or 9 survive.
 */
object CardNumberRedactor {
    private val CANDIDATE = Regex("""(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)""")

    fun redact(text: String): String = CANDIDATE.replace(text) { m ->
        val digits = m.value.filter(Char::isDigit)
        if (digits.length in 13..19 && digits.first() in '2'..'6' && luhn(digits)) "•••• ${digits.takeLast(4)}"
        else m.value
    }

    fun luhn(digits: String): Boolean {
        var sum = 0
        var double = false
        for (i in digits.indices.reversed()) {
            var n = digits[i] - '0'
            if (double) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            double = !double
        }
        return sum % 10 == 0
    }
}
