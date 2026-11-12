package io.github.chezmose.mysecretsanta.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DatesTest {

    @Test
    fun `dates longues en français`() {
        assertEquals("jeudi 24 décembre", formatLongDate(LocalDate.of(2026, 12, 24)))
        assertEquals("jeudi 24 décembre 2026", formatLongDate(LocalDate.of(2026, 12, 24), withYear = true))
        assertEquals("mardi 1er décembre", formatLongDate(LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun `prochain réveillon`() {
        assertEquals(LocalDate.of(2026, 12, 24), nextChristmasEve(LocalDate.of(2026, 11, 12)))
        assertEquals(LocalDate.of(2026, 12, 24), nextChristmasEve(LocalDate.of(2026, 12, 24)))
        assertEquals(LocalDate.of(2027, 12, 24), nextChristmasEve(LocalDate.of(2026, 12, 25)))
    }
}
