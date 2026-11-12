package io.github.chezmose.mysecretsanta.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSegmentsTest {

    private fun segments(text: String) = SmsSegments.estimate(text).segments

    @Test
    fun `alphabet GSM, 160 caractères par SMS seul, 153 au-delà`() {
        assertEquals(0, segments(""))
        assertEquals(1, segments("a".repeat(160)))
        assertEquals(2, segments("a".repeat(161)))
        assertEquals(2, segments("a".repeat(306)))
        assertEquals(3, segments("a".repeat(307)))
        assertFalse(SmsSegments.estimate("Bonjour Élodie, à demain !").unicode)
    }

    @Test
    fun `caractères de l'extension GSM comptent double`() {
        assertEquals(1, segments("€".repeat(80)))
        assertEquals(2, segments("€".repeat(81)))
        assertFalse(SmsSegments.estimate("€").unicode)
    }

    @Test
    fun `accents hors alphabet GSM, Unicode à 70 puis 67 caractères`() {
        val estimate = SmsSegments.estimate("Fête")
        assertTrue(estimate.unicode)
        assertEquals(listOf("ê"), estimate.unicodeCharacters)
        assertEquals(1, segments("ê".repeat(70)))
        assertEquals(2, segments("ê".repeat(71)))
        assertEquals(2, segments("ê".repeat(134)))
        assertEquals(3, segments("ê".repeat(135)))
    }

    @Test
    fun `un émoji compte pour 2`() {
        assertEquals(1, segments("🎅".repeat(35)))
        assertEquals(2, segments("🎅".repeat(36)))
    }

    @Test
    fun `caractères Unicode listés une seule fois, dans l'ordre`() {
        assertEquals(
            listOf("🎅", "«", "»"),
            SmsSegments.estimate("🎅 Pour « Noel », 🎅 « » !").unicodeCharacters,
        )
    }
}
