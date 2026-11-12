package io.github.chezmose.mysecretsanta.message

import org.junit.Assert.assertEquals
import org.junit.Test

class GsmTest {

    @Test
    fun `texte déjà GSM inchangé`() {
        val text = "Bonjour Élodie, à demain ! 30 € (ça va ?)".replace("ç", "c")
        assertEquals(text, Gsm.sanitize(text))
    }

    @Test
    fun `accents spéciaux remplacés`() {
        assertEquals("Fete de Noel chez Francoise et Jerome", Gsm.sanitize("Fête de Noël chez Françoise et Jérôme").replace("é", "e"))
        assertEquals("ecole", Gsm.sanitize("école").replace("é", "e"))
        assertEquals("oeuf, OEuvre", Gsm.sanitize("œuf, Œuvre"))
        assertEquals("Ile", Gsm.sanitize("Île"))
    }

    @Test
    fun `ponctuation typographique remplacée`() {
        assertEquals("\"Noel\" - c'est l'heure...", Gsm.sanitize("« Noël » – c’est l’heure…"))
    }

    @Test
    fun `émojis et caractères invisibles retirés, espaces spéciaux remplacés`() {
        assertEquals("Joyeux Noel !", Gsm.sanitize("🎅Joyeux Noël !🎄"))
        assertEquals("abc", Gsm.sanitize("a​b‪c"))
    }

    @Test
    fun `longueur SMS, extension comptée double`() {
        assertEquals(5, Gsm.length("Hello"))
        assertEquals(5, Gsm.length("30 €")) // « 3 », « 0 », espace, puis € qui compte double
        assertEquals(2, Gsm.length("{"))
    }
}
