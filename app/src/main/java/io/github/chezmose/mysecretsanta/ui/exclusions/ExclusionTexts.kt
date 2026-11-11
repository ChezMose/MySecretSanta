package io.github.chezmose.mysecretsanta.ui.exclusions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.chezmose.mysecretsanta.data.ExclusionRole
import io.github.chezmose.mysecretsanta.data.ExclusionType
import io.github.chezmose.mysecretsanta.data.ExclusionWithMembers

val ExclusionType.label: String
    get() = when (this) {
        ExclusionType.COUPLE -> "Couple"
        ExclusionType.ONE_WAY -> "Sens unique"
        ExclusionType.HOUSEHOLD -> "Foyer"
    }

val ExclusionType.explanation: String
    get() = when (this) {
        ExclusionType.COUPLE -> "Ces deux personnes ne se tirent pas l'une l'autre."
        ExclusionType.ONE_WAY -> "Une personne n'offre pas à une autre. L'inverse reste possible."
        ExclusionType.HOUSEHOLD -> "Aucun membre du foyer n'offre à un autre membre du foyer."
    }

val ExclusionType.icon: ImageVector
    get() = when (this) {
        ExclusionType.COUPLE -> Icons.Filled.Favorite
        ExclusionType.ONE_WAY -> Icons.AutoMirrored.Filled.ArrowForward
        ExclusionType.HOUSEHOLD -> Icons.Filled.Home
    }

/** « Alice et Bob », « Alice n'offre pas à Bob », « Alice, Bob et Léo ». */
fun ExclusionWithMembers.describe(nameOf: (Long) -> String): String =
    when (rule.type) {
        ExclusionType.ONE_WAY -> {
            val giver = members.firstOrNull { it.role == ExclusionRole.GIVER }?.personId
            val receiver = members.firstOrNull { it.role == ExclusionRole.RECEIVER }?.personId
            "${giver?.let(nameOf) ?: "?"} n'offre pas à ${receiver?.let(nameOf) ?: "?"}"
        }
        ExclusionType.COUPLE, ExclusionType.HOUSEHOLD -> frenchList(members.map { nameOf(it.personId) }.sorted())
    }

/** « A », « A et B », « A, B et C ». */
fun frenchList(items: List<String>): String =
    when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " et " + items.last()
    }
