package io.github.chezmose.mysecretsanta.util

/** Validation et normalisation des numéros de mobile (SPEC.md §5.1). */
object PhoneNumbers {

    sealed interface Result {
        /** Numéro valide, au format international : `+33612345678`. */
        data class Valid(val e164: String) : Result

        /** Numéro refusé, avec un message à afficher sous le champ. */
        data class Invalid(val message: String) : Result
    }

    private const val FORMAT_HINT =
        "Saisis un numéro à 10 chiffres (06 12 34 56 78) ou international (+32 …)."

    fun normalize(input: String): Result {
        val compact = buildString {
            for (c in input) {
                when {
                    c in '0'..'9' || c == '+' -> append(c)
                    c.isIgnorableSeparator() -> Unit
                    else -> return Result.Invalid("Le numéro contient des caractères non autorisés.")
                }
            }
        }
        if (compact.isEmpty()) return Result.Invalid("Le numéro est obligatoire.")
        if (compact.lastIndexOf('+') > 0) return Result.Invalid("Le signe + doit être au début du numéro.")

        var number = when {
            compact.startsWith("+") -> compact
            compact.startsWith("00") -> "+" + compact.drop(2)
            compact.length == 10 && compact.startsWith("0") -> "+33" + compact.drop(1)
            else -> return Result.Invalid(FORMAT_HINT)
        }
        // « +33 (0)6 12 34 56 78 » : le 0 national est en trop après l'indicatif.
        if (number.startsWith("+330") && number.length == 13) number = "+33" + number.drop(4)

        val digits = number.drop(1)
        if (digits.length !in 8..15 || digits.startsWith("0")) {
            return Result.Invalid("Numéro international invalide.")
        }
        if (number.startsWith("+33")) {
            val national = number.drop(3)
            if (national.length != 9) return Result.Invalid("Un numéro français compte 10 chiffres.")
            if (national[0] != '6' && national[0] != '7') {
                return Result.Invalid("Ce numéro n'est pas un mobile : il doit commencer par 06 ou 07.")
            }
        }
        return Result.Valid(number)
    }

    /** Affichage : `06 12 34 56 78` pour un mobile français, le format international sinon. */
    fun format(e164: String): String =
        if (e164.startsWith("+33") && e164.length == 12) {
            ("0" + e164.drop(3)).chunked(2).joinToString(" ")
        } else {
            e164
        }

    // Espaces, ponctuation de saisie et caractères invisibles ajoutés par certaines apps Contacts.
    private fun Char.isIgnorableSeparator(): Boolean =
        isWhitespace() ||
            this in ".-/()" ||
            Character.getType(this) == Character.SPACE_SEPARATOR.toInt() ||
            Character.getType(this) == Character.FORMAT.toInt()
}
