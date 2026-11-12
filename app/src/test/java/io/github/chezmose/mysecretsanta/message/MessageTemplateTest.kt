package io.github.chezmose.mysecretsanta.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MessageTemplateTest {

    private val christmasEve = LocalDate.of(2026, 12, 24) // un jeudi

    private fun render(budget: String?, date: LocalDate?, template: String = MessageTemplate.DEFAULT) =
        MessageTemplate.render(template, "Alice", "Bob", "Noël 2026", budget, date)

    @Test
    fun `message par défaut complet`() {
        assertEquals(
            "🎅 Bonjour Alice ! Pour « Noël 2026 », tu offres un cadeau à Bob. " +
                "Budget : 30 €. Rendez-vous le jeudi 24 décembre. Chut, c'est secret !",
            render("30 €", christmasEve),
        )
    }

    @Test
    fun `sans budget, la phrase du budget disparaît`() {
        assertEquals(
            "🎅 Bonjour Alice ! Pour « Noël 2026 », tu offres un cadeau à Bob. " +
                "Rendez-vous le jeudi 24 décembre. Chut, c'est secret !",
            render(null, christmasEve),
        )
        assertEquals(render(null, christmasEve), render("   ", christmasEve))
    }

    @Test
    fun `sans date, la phrase de la date disparaît`() {
        assertEquals(
            "🎅 Bonjour Alice ! Pour « Noël 2026 », tu offres un cadeau à Bob. " +
                "Budget : 30 €. Chut, c'est secret !",
            render("30 €", null),
        )
    }

    @Test
    fun `sans budget ni date`() {
        assertEquals(
            "🎅 Bonjour Alice ! Pour « Noël 2026 », tu offres un cadeau à Bob. Chut, c'est secret !",
            render(null, null),
        )
    }

    @Test
    fun `premier du mois`() {
        assertEquals(
            "Rendez-vous le mardi 1er décembre.",
            render(null, LocalDate.of(2026, 12, 1), template = "Rendez-vous le {date}."),
        )
    }

    @Test
    fun `retours à la ligne conservés`() {
        val template = "Bonjour {donneur} !\nTu offres à {receveur}.\nBudget : {budget}.\nÀ bientôt !"
        assertEquals(
            "Bonjour Alice !\nTu offres à Bob.\n\nÀ bientôt !",
            render(null, null, template),
        )
    }

    @Test
    fun `validation du modèle`() {
        assertNull(MessageTemplate.validate(MessageTemplate.DEFAULT))
        assertNull(MessageTemplate.validate("Tu offres à {receveur}"))
        assertNotNull(MessageTemplate.validate(""))
        assertNotNull(MessageTemplate.validate("   "))
        assertNotNull(MessageTemplate.validate("Bonjour {donneur} !"))
    }
}
