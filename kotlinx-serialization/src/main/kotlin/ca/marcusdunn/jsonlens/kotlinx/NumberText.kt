package ca.marcusdunn.jsonlens.kotlinx

/** The kind of a JSON number text (RFC 8259, Section 6), and the cheapest exact representation of its value. */
internal enum class NumberText {
    /** The text is not a JSON number. */
    NONE,

    /** An integer with at most 18 digits: a `long` holds it. */
    LONG,

    /**
     * At most 15 significant digits, and a magnitude from 1e-280 to 1e280 (or zero). The `double`
     * of the text has exactly the value of the text: two decimals with at most 15 significant digits
     * never round to the same `double`, so the shortest decimal of that `double` is the text.
     */
    DOUBLE,

    /** Any other JSON number. */
    DECIMAL,
}

/** The most significant digits for [NumberText.DOUBLE]. */
private const val DOUBLE_DIGITS = 15

/** The largest decimal exponent (in magnitude) for [NumberText.DOUBLE]: far inside the normal range. */
private const val DOUBLE_ORDER = 280

/** The most digits for [NumberText.LONG]. */
private const val LONG_DIGITS = 18

/** An exponent beyond this is not a [NumberText.DOUBLE] in any case, so the scan stops counting. */
private const val EXPONENT_CAP = 100_000

/**
 * Reads a text with the JSON number grammar, in one pass and without a regular expression:
 * `-? (0 / [1-9][0-9]*) ("." [0-9]+)? ([eE] [-+]? [0-9]+)?`.
 */
internal fun numberText(text: String): NumberText {
    val scan = NumberScan(text)
    return if (scan.valid()) scan.kind() else NumberText.NONE
}

/** One scan of a number text. */
private class NumberScan(private val text: String) {
    private var position = 0
    private var integerDigits = 0
    private var fraction = false
    private var exponentPart = false
    private var exponent = 0

    /** The number of digits before the first digit that is not zero, over the integer and the fraction. */
    private var leadingZeros = 0

    /** The number of digits up to and with the last digit that is not zero. */
    private var lastNonZero = 0

    /** All digits so far, over the integer and the fraction. */
    private var digits = 0

    fun valid(): Boolean {
        if (peek() == '-') {
            position++
        }
        if (peek() == '0') {
            // A zero integer part changes neither the significant digits nor the order.
            position++
        } else if (isDigit(peek())) {
            while (isDigit(peek())) {
                digit(text[position])
                position++
            }
        } else {
            return false
        }
        integerDigits = digits
        if (peek() == '.') {
            position++
            fraction = true
            if (!isDigit(peek())) {
                return false
            }
            while (isDigit(peek())) {
                digit(text[position])
                position++
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            position++
            exponentPart = true
            return exponent() && position == text.length
        }
        return position == text.length
    }

    private fun exponent(): Boolean {
        var negative = false
        if (peek() == '+' || peek() == '-') {
            negative = peek() == '-'
            position++
        }
        if (!isDigit(peek())) {
            return false
        }
        while (isDigit(peek())) {
            exponent = minOf(EXPONENT_CAP, exponent * 10 + (text[position] - '0'))
            position++
        }
        if (negative) {
            exponent = -exponent
        }
        return true
    }

    private fun digit(c: Char) {
        digits++
        if (c != '0') {
            if (lastNonZero == 0) {
                leadingZeros = digits - 1
            }
            lastNonZero = digits
        }
    }

    private fun isDigit(c: Char): Boolean = c in '0'..'9'

    /** The character at the position, or a space after the end. */
    private fun peek(): Char = if (position < text.length) text[position] else ' '

    fun kind(): NumberText {
        if (!fraction && !exponentPart && integerDigits <= LONG_DIGITS) {
            return NumberText.LONG
        }
        if (lastNonZero == 0) {
            // Zero, with any fraction and exponent.
            return NumberText.DOUBLE
        }
        val significant = lastNonZero - leadingZeros
        // The decimal exponent of the first digit that is not zero.
        val order = integerDigits - 1 - leadingZeros + exponent
        return if (significant <= DOUBLE_DIGITS && order in -DOUBLE_ORDER..DOUBLE_ORDER) NumberText.DOUBLE else NumberText.DECIMAL
    }
}
