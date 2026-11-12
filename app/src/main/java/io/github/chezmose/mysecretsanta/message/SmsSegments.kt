package io.github.chezmose.mysecretsanta.message

/**
 * Estimation du nombre de SMS nécessaires pour un texte quelconque.
 *
 * - Alphabet GSM 7 bits ([Gsm]) : 160 caractères pour un SMS seul, 153 par SMS au-delà.
 *   Les caractères de l'extension (€, [, ], {, }…) comptent double.
 * - Dès qu'un caractère sort de cet alphabet : Unicode, 70 caractères pour un SMS seul,
 *   67 par SMS au-delà. Un émoji compte souvent pour 2.
 *
 * Les messages du Secret Santa passent par [MessageTemplate.render], toujours en GSM.
 */
object SmsSegments {

    data class Estimate(
        val segments: Int,
        val unicode: Boolean,
        /** Les caractères qui imposent l'Unicode, sans doublon, dans l'ordre d'apparition. */
        val unicodeCharacters: List<String>,
    )

    fun estimate(text: String): Estimate {
        if (text.isEmpty()) return Estimate(segments = 0, unicode = false, unicodeCharacters = emptyList())

        val nonGsm = linkedSetOf<String>()
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val char = String(Character.toChars(codePoint))
            if (char.length != 1 || !Gsm.isGsm(char[0])) nonGsm += char
            offset += Character.charCount(codePoint)
        }

        return if (nonGsm.isEmpty()) {
            Estimate(segmentsFor(Gsm.length(text), single = 160, multi = 153), unicode = false, unicodeCharacters = emptyList())
        } else {
            // En Unicode, chaque unité UTF-16 compte : un émoji hors du plan de base en vaut 2.
            Estimate(segmentsFor(text.length, single = 70, multi = 67), unicode = true, unicodeCharacters = nonGsm.toList())
        }
    }

    private fun segmentsFor(units: Int, single: Int, multi: Int): Int =
        if (units <= single) 1 else (units + multi - 1) / multi
}
