package io.github.chezmose.mysecretsanta.message

/**
 * Modèle du message envoyé à chaque participant (SPEC.md §5.7).
 * Le message tient toujours en un seul SMS : alphabet GSM uniquement, 160 caractères au plus.
 */
object MessageTemplate {

    const val GIVER = "{donneur}"
    const val RECEIVER = "{receveur}"
    const val DRAW = "{tirage}"
    const val BUDGET = "{budget}"

    /** Ancienne variable, supprimée : une phrase qui la contient encore est retirée du message. */
    private const val LEGACY_DATE = "{date}"

    val VARIABLES = listOf(GIVER, RECEIVER, DRAW, BUDGET)

    /** Longueur maximale d'un SMS seul, en alphabet GSM. */
    const val MAX_LENGTH = 160

    const val DEFAULT =
        "Bonjour {donneur}, pour {tirage}, tu offres un cadeau à {receveur}.\n" +
            "Ne réponds pas à ce message ! C'est un secret pour moi aussi."

    /** Fin de phrase : ponctuation finale suivie d'un espace. */
    private val SENTENCE_BREAK = Regex("(?<=[.!?…])\\s+")

    /** Message d'erreur si le modèle est inutilisable, sinon `null`. */
    fun validate(template: String): String? =
        when {
            template.isBlank() -> "Le message est obligatoire."
            RECEIVER !in template -> "Le message doit contenir $RECEIVER : sinon, personne ne saura à qui offrir."
            LEGACY_DATE in template -> "La variable $LEGACY_DATE n'existe plus : retire-la du message."
            template.any { !Gsm.isGsm(it) } ->
                "Le message contient des caractères spéciaux (émojis, ê, ç, « »…) : retire-les pour tenir en un seul SMS."
            else -> null
        }

    /**
     * Vérifie que le message tient en un SMS avec le nom le plus long ([longestName], comme donneur
     * et comme receveur). Renvoie un message d'erreur, ou `null` si tout tient.
     */
    fun checkLength(template: String, longestName: String, drawName: String, budget: String?): String? {
        val length = Gsm.length(render(template, longestName, longestName, drawName, budget))
        return if (length <= MAX_LENGTH) {
            null
        } else {
            "Avec le nom le plus long ($longestName), le message fait $length caractères : " +
                "il dépasse un SMS ($MAX_LENGTH). Raccourcis-le."
        }
    }

    /**
     * Remplace les variables. Une phrase qui contient une variable vide (budget absent) est retirée
     * entièrement. Les retours à la ligne du modèle sont conservés. Le résultat est 100 % GSM :
     * les accents spéciaux des noms sont convertis (Noël → Noel), les émojis retirés.
     */
    fun render(
        template: String,
        giver: String,
        receiver: String,
        drawName: String,
        budget: String?,
    ): String {
        // Valeurs converties en GSM, puis nettoyées : « Fête 🎄 » donne « Fete », sans espace final.
        fun clean(value: String?): String = Gsm.sanitize(value.orEmpty()).trim()
        val values = mapOf(
            GIVER to clean(giver),
            RECEIVER to clean(receiver),
            DRAW to clean(drawName),
            BUDGET to clean(budget),
            LEGACY_DATE to "",
        )
        val empty = values.filterValues { it.isEmpty() }.keys
        val text = template.lines().joinToString("\n") { line ->
            line.split(SENTENCE_BREAK)
                .filter { sentence -> empty.none { it in sentence } }
                .joinToString(" ") { sentence ->
                    values.entries.fold(sentence) { acc, (variable, value) -> acc.replace(variable, value) }
                }
        }.trim()
        return Gsm.sanitize(text)
    }
}
