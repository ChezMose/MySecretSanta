package io.github.chezmose.mysecretsanta.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_OF_WEEK = DateTimeFormatter.ofPattern("EEEE", Locale.FRENCH)
private val MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.FRENCH)
private val SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

/** « mercredi 24 décembre », « dimanche 1er décembre 2024 » avec [withYear]. */
fun formatLongDate(date: LocalDate, withYear: Boolean = false): String {
    val day = if (date.dayOfMonth == 1) "1er" else date.dayOfMonth.toString()
    val text = "${date.format(DAY_OF_WEEK)} $day ${date.format(MONTH)}"
    return if (withYear) "$text ${date.year}" else text
}

/** « 24 déc. 2026 ». */
fun formatShortDate(date: LocalDate): String = date.format(SHORT)

/** Le prochain 24 décembre (aujourd'hui compris). */
fun nextChristmasEve(today: LocalDate = LocalDate.now()): LocalDate {
    val thisYear = LocalDate.of(today.year, 12, 24)
    return if (today.isAfter(thisYear)) thisYear.plusYears(1) else thisYear
}
