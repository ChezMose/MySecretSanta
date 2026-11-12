package io.github.chezmose.mysecretsanta.message

/**
 * Estimation du nombre de SMS nécessaires pour un message (SPEC.md §5.7).
 *
 * - Alphabet GSM 7 bits : 160 caractères pour un SMS seul, 153 par SMS au-delà.
 *   Les caractères de l'extension (€, [, ], {, }…) comptent double.
 * - Dès qu'un caractère sort de cet alphabet (émoji, « », ê, ç…) : Unicode,
 *   70 caractères pour un SMS seul, 67 par SMS au-delà. Un émoji compte souvent pour 2.
 */
object SmsSegments {

    data class Estimate(
        val segments: Int,
        val unicode: Boolean,
        /** Les caractères qui imposent l'Unicode, sans doublon, dans l'ordre d'apparition. */
        val unicodeCharacters: List<String>,
    )

    private const val GSM_BASIC =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    private const val GSM_EXTENSION = "^{}\\[~]|€\u000C"

    fun estimate(text: String): Estimate {
        if (text.isEmpty()) return Estimate(segments = 0, unicode = false, unicodeCharacters = emptyList())

        val nonGsm = linkedSetOf<String>()
        var septets = 0
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val char = String(Character.toChars(codePoint))
            when {
                char.length == 1 && char[0] in GSM_BASIC -> septets += 1
                char.length == 1 && char[0] in GSM_EXTENSION -> septets += 2
                else -> nonGsm += char
            }
            offset += Character.charCount(codePoint)
        }

        return if (nonGsm.isEmpty()) {
            Estimate(segmentsFor(septets, single = 160, multi = 153), unicode = false, unicodeCharacters = emptyList())
        } else {
            // En Unicode, chaque unité UTF-16 compte : un émoji hors du plan de base en vaut 2.
            Estimate(segmentsFor(text.length, single = 70, multi = 67), unicode = true, unicodeCharacters = nonGsm.toList())
        }
    }

    private fun segmentsFor(units: Int, single: Int, multi: Int): Int =
        if (units <= single) 1 else (units + multi - 1) / multi
}
