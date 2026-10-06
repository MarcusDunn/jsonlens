package ca.marcusdunn.jsonlens.kotlinx

import ca.marcusdunn.jsonlens.testsupport.Requirement
import java.math.BigDecimal
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Requirement("lib/kotlinx-contract")
class NumberTextTest {

    /** The JSON number grammar of RFC 8259, as the model checked it before the scanner. */
    private val grammar = Regex("""-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][-+]?[0-9]+)?""")

    @Test
    fun rejectsTextsThatAreNotJsonNumbers() {
        for (text in listOf("", "-", "+1", "01", "-01", "1.", ".1", "1e", "1e+", "1E-", "1.0.0", " 1", "1 ",
                "NaN", "Infinity", "-Infinity", "1_0", "0x10", "1e1.5", "--1", "1e--1", "true", "1a",
                // The characters next to the digits: "/" before "0", ":" after "9".
                ":", "/", "1:", "1/", "-:", "1.:", "1./", "1.5:", "1e:", "1e/", "1e5:", "1e+:")) {
            assertEquals(NumberText.NONE, numberText(text), text)
        }
    }

    @Test
    fun acceptsExactlyTheTextsOfTheGrammar() {
        val random = Random(7396)
        val alphabet = "0123456789-+.eE"
        repeat(20_000) {
            val text = buildString { repeat(random.nextInt(1, 8)) { append(alphabet[random.nextInt(alphabet.length)]) } }
            assertEquals(grammar.matches(text), numberText(text) != NumberText.NONE, text)
        }
    }

    @Test
    fun anIntegerWithAtMost18DigitsIsALong() {
        assertEquals(NumberText.LONG, numberText("0"))
        assertEquals(NumberText.LONG, numberText("-0"))
        assertEquals(NumberText.LONG, numberText("-7"))
        assertEquals(NumberText.LONG, numberText("123456789012345678"))
        assertEquals(NumberText.LONG, numberText("-999999999999999999"))
        // 19 digits do not always fit in a long, and they are more than 15 significant digits.
        assertEquals(NumberText.DECIMAL, numberText("1234567890123456789"))
        // With a fraction or an exponent, an integer value is not a LONG.
        assertEquals(NumberText.DOUBLE, numberText("1.0"))
        assertEquals(NumberText.DOUBLE, numberText("1e0"))
        assertEquals(NumberText.DOUBLE, numberText("10E+1"))
    }

    @Test
    fun atMost15SignificantDigitsInTheRangeIsADouble() {
        assertEquals(NumberText.DOUBLE, numberText("1.23456789012345"))
        assertEquals(NumberText.DECIMAL, numberText("1.234567890123456"))
        assertEquals(NumberText.DOUBLE, numberText("0.000123456789012345"))
        assertEquals(NumberText.DECIMAL, numberText("0.0001234567890123456"))
        // Zeros at the end are not significant.
        assertEquals(NumberText.DOUBLE, numberText("1.500000000000000000000"))
        assertEquals(NumberText.DOUBLE, numberText("123456789012345000000"))
        assertEquals(NumberText.DOUBLE, numberText("-12.5e-3"))
    }

    @Test
    fun theMagnitudeMustBeFarInsideTheRangeOfADouble() {
        assertEquals(NumberText.DOUBLE, numberText("1e280"))
        assertEquals(NumberText.DECIMAL, numberText("1e281"))
        assertEquals(NumberText.DOUBLE, numberText("9.99e280"))
        assertEquals(NumberText.DOUBLE, numberText("1e-280"))
        assertEquals(NumberText.DECIMAL, numberText("1e-281"))
        assertEquals(NumberText.DOUBLE, numberText("0.01e-278"))
        assertEquals(NumberText.DECIMAL, numberText("0.01e-279"))
        assertEquals(NumberText.DOUBLE, numberText("100e278"))
        assertEquals(NumberText.DECIMAL, numberText("100e279"))
        assertEquals(NumberText.DECIMAL, numberText("1e400"))
        assertEquals(NumberText.DECIMAL, numberText("-1E-400"))
        assertEquals(NumberText.DECIMAL, numberText("1e99999999999999999999"))
        assertEquals(NumberText.DECIMAL, numberText("1e-99999999999999999999"))
    }

    @Test
    fun zeroIsADoubleWithAnyExponent() {
        assertEquals(NumberText.DOUBLE, numberText("0.0"))
        assertEquals(NumberText.DOUBLE, numberText("-0.000"))
        assertEquals(NumberText.DOUBLE, numberText("0e99999999999"))
        assertEquals(NumberText.DOUBLE, numberText("0.00e-99999999999"))
    }

    @Test
    fun theDoubleOfADoubleTextHasExactlyTheValueOfTheText() {
        val random = Random(9535)
        var doubles = 0
        repeat(50_000) {
            val text = randomNumber(random)
            val exact = BigDecimal(text)
            when (numberText(text)) {
                NumberText.LONG -> assertEquals(0, exact.compareTo(BigDecimal(text.toLong())), text)
                NumberText.DOUBLE -> {
                    doubles++
                    assertEquals(0, exact.compareTo(BigDecimal.valueOf(text.toDouble())), text)
                }
                NumberText.DECIMAL -> {}
                NumberText.NONE -> throw AssertionError("not a number: $text")
            }
        }
        assertTrue(doubles > 10_000, "only $doubles DOUBLE texts")
    }

    /** A random JSON number text: up to 20 digits, a fraction, and an exponent near the limits. */
    private fun randomNumber(random: Random): String = buildString {
        if (random.nextBoolean()) append('-')
        val integer = random.nextInt(0, 12)
        if (integer == 0) append('0') else {
            append('1' + random.nextInt(9))
            repeat(integer - 1) { append('0' + random.nextInt(10)) }
        }
        if (random.nextBoolean()) {
            append('.')
            repeat(random.nextInt(1, 12)) { append('0' + random.nextInt(10)) }
        }
        if (random.nextBoolean()) {
            append(if (random.nextBoolean()) 'e' else 'E')
            append(listOf("", "+", "-")[random.nextInt(3)])
            append(random.nextInt(0, 300))
        }
    }
}
