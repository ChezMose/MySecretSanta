package io.github.chezmose.mysecretsanta.message

/**
 * Alphabet SMS standard (GSM 03.38, 7 bits) : 160 caractères par SMS.
 * Un seul caractère hors de cet alphabet ferait passer le SMS en Unicode (70 caractères),
 * d'où [sanitize], qui garantit un message 100 % GSM (SPEC.md §5.7).
 */
object Gsm {

    const val BASIC =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"

    /** Caractères de l'extension : ils comptent pour 2 dans la longueur du SMS. */
    const val EXTENSION = "^{}\\[~]|€\u000C"

    fun isGsm(char: Char): Boolean = char in BASIC || char in EXTENSION

    /** Longueur du texte en caractères SMS (les caractères de l'extension comptent double). */
    fun length(text: String): Int {
        var length = 0
        for (char in text) length += if (char in EXTENSION) 2 else 1
        return length
    }

    private val OPENING_GUILLEMET = Regex("«[\\s  ]*")
    private val CLOSING_GUILLEMET = Regex("[\\s  ]*»")

    private val REPLACEMENTS: Map<Char, String> = buildMap {
        fun map(chars: String, replacement: String) {
            for (char in chars) put(char, replacement)
        }
        map("áâãāą", "a")
        map("ÀÁÂÃĀĄ", "A")
        map("çćč", "c")
        map("ĆČ", "C")
        map("êëēėę", "e")
        map("ÈÊËĒĖĘ", "E")
        map("íîïī", "i")
        map("ÌÍÎÏĪ", "I")
        map("óôõō", "o")
        map("ÒÓÔÕŌ", "O")
        map("úûū", "u")
        map("ÙÚÛŪ", "U")
        map("ýÿ", "y")
        map("ÝŸ", "Y")
        map("ńň", "n")
        map("ŃŇ", "N")
        map("śš", "s")
        map("ŚŠ", "S")
        map("źżž", "z")
        map("ŹŻŽ", "Z")
        map("ł", "l")
        map("Ł", "L")
        map("œ", "oe")
        map("Œ", "OE")
        map("«»“”„″", "\"")
        map("‘’‚′", "'")
        map("–—‐‑−•", "-")
        map("…", "...")
    }

    /**
     * Rend le texte 100 % GSM : remplace les accents et la ponctuation typographique par leur
     * équivalent le plus proche (ê → e, « » → "), les espaces spéciaux par un espace,
     * et retire tout le reste (émojis, caractères invisibles…).
     */
    fun sanitize(text: String): String = buildString {
        // Guillemets français : « Noël » devient "Noel", sans les espaces intérieures.
        val source = text.replace(OPENING_GUILLEMET, "\"").replace(CLOSING_GUILLEMET, "\"")
        var offset = 0
        while (offset < source.length) {
            val codePoint = source.codePointAt(offset)
            if (Character.charCount(codePoint) == 1) {
                val char = source[offset]
                when {
                    isGsm(char) -> append(char)
                    char in REPLACEMENTS -> append(REPLACEMENTS.getValue(char))
                    char.isWhitespace() -> append(' ')
                    else -> Unit // émoji du plan de base, caractère invisible… : retiré
                }
            }
            // Hors du plan de base (la plupart des émojis) : retiré.
            offset += Character.charCount(codePoint)
        }
    }
}
