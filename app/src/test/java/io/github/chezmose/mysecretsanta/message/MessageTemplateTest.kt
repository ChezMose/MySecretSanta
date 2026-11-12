package io.github.chezmose.mysecretsanta.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTemplateTest {

    private fun render(budget: String?, template: String = MessageTemplate.DEFAULT, drawName: String = "Noël 2026") =
        MessageTemplate.render(template, "Alice", "Bob", drawName, budget)

    @Test
    fun `message par défaut`() {
        assertEquals(
            "Bonjour Alice, pour Noel 2026, tu offres un cadeau à Bob.\n" +
                "Ne réponds pas à ce message ! C'est un secret pour moi aussi.",
            render(null),
        )
    }

    @Test
    fun `message par défaut, un seul SMS même avec des noms longs`() {
        assertNull(MessageTemplate.validate(MessageTemplate.DEFAULT))
        assertNull(MessageTemplate.checkLength(MessageTemplate.DEFAULT, "Jean-Baptiste", "Noël 2026", null))
        val text = MessageTemplate.render(MessageTemplate.DEFAULT, "Marie-Antoinette", "Marie-Antoinette", "Noël 2026", null)
        val estimate = SmsSegments.estimate(text)
        assertEquals(false, estimate.unicode)
        assertEquals(1, estimate.segments)
    }

    @Test
    fun `budget renseigné ou phrase retirée`() {
        val template = "Tu offres à {receveur}. Budget : {budget}. Chut !"
        assertEquals("Tu offres à Bob. Budget : 30 €. Chut !", render("30 €", template))
        assertEquals("Tu offres à Bob. Chut !", render(null, template))
        assertEquals("Tu offres à Bob. Chut !", render("   ", template))
    }

    @Test
    fun `ancienne variable date, sa phrase est retirée`() {
        assertEquals(
            "Tu offres à Bob. Chut !",
            render(null, "Tu offres à {receveur}. Rendez-vous le {date}. Chut !"),
        )
    }

    @Test
    fun `retours à la ligne conservés`() {
        val template = "Bonjour {donneur} !\nTu offres à {receveur}.\nBudget : {budget}.\nA demain !"
        assertEquals("Bonjour Alice !\nTu offres à Bob.\n\nA demain !", render(null, template))
    }

    @Test
    fun `noms et tirage convertis en alphabet SMS`() {
        val text = MessageTemplate.render("{donneur} offre à {receveur} pour {tirage}.", "Françoise", "Zoë", "Fête 🎄", null)
        assertEquals("Francoise offre à Zoe pour Fete.", text)
        assertTrue(text.all { Gsm.isGsm(it) })
    }

    @Test
    fun `validation du modèle`() {
        assertNull(MessageTemplate.validate("Tu offres à {receveur}"))
        assertNotNull(MessageTemplate.validate(""))
        assertNotNull(MessageTemplate.validate("   "))
        assertNotNull(MessageTemplate.validate("Bonjour {donneur} !"))
        assertNotNull(MessageTemplate.validate("🎅 Tu offres à {receveur}"))
        assertNotNull(MessageTemplate.validate("Tu offres à {receveur} le {date}."))
    }

    @Test
    fun `message trop long refusé`() {
        val template = "Tu offres à {receveur}. " + "a".repeat(140)
        assertNull(MessageTemplate.checkLength(template, "Bob", "Noël", null))
        assertNotNull(MessageTemplate.checkLength(template, "Jean-Baptiste Dupont", "Noël", null))
    }
}
