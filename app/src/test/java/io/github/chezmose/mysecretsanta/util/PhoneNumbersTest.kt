package io.github.chezmose.mysecretsanta.util

import io.github.chezmose.mysecretsanta.util.PhoneNumbers.Result.Invalid
import io.github.chezmose.mysecretsanta.util.PhoneNumbers.Result.Valid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {

    private fun assertValid(input: String, expected: String) =
        assertEquals("« $input »", Valid(expected), PhoneNumbers.normalize(input))

    private fun assertInvalid(input: String) =
        assertTrue("« $input » devrait être refusé", PhoneNumbers.normalize(input) is Invalid)

    @Test
    fun `numéros français`() {
        assertValid("0612345678", "+33612345678")
        assertValid("06 12 34 56 78", "+33612345678")
        assertValid("07.12.34.56.78", "+33712345678")
        assertValid("06-12-34-56-78", "+33612345678")
        assertValid("  06 12 34 56 78  ", "+33612345678")
    }

    @Test
    fun `numéros français au format international`() {
        assertValid("+33612345678", "+33612345678")
        assertValid("+33 6 12 34 56 78", "+33612345678")
        assertValid("0033 6 12 34 56 78", "+33612345678")
        assertValid("+33 (0)6 12 34 56 78", "+33612345678")
    }

    @Test
    fun `caractères invisibles copiés depuis les contacts`() {
        assertValid("‪+33 6 12 34 56 78‬", "+33612345678")
        assertValid("06 12 34 56 78", "+33612345678")
    }

    @Test
    fun `numéros étrangers`() {
        assertValid("+32 470 12 34 56", "+32470123456")
        assertValid("0041 79 123 45 67", "+41791234567")
    }

    @Test
    fun `numéros refusés`() {
        assertInvalid("")
        assertInvalid("   ")
        assertInvalid("abc")
        assertInvalid("06 12 34")
        assertInvalid("6 12 34 56 78")
        assertInvalid("01 23 45 67 89") // fixe
        assertInvalid("+33 1 23 45 67 89") // fixe
        assertInvalid("+33 6 12 34 56") // trop court
        assertInvalid("06 12 34 56 78 9") // trop long
        assertInvalid("+1")
        assertInvalid("06+12345678")
        assertInvalid("06 12 34 56 7a")
    }

    @Test
    fun `affichage`() {
        assertEquals("06 12 34 56 78", PhoneNumbers.format("+33612345678"))
        assertEquals("+32470123456", PhoneNumbers.format("+32470123456"))
        assertEquals("06 12 34 56 78", PhoneNumbers.label("+33612345678"))
        assertEquals("Pas de téléphone", PhoneNumbers.label(null))
    }
}
