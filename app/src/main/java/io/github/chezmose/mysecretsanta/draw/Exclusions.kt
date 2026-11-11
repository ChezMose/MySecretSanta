package io.github.chezmose.mysecretsanta.draw

import io.github.chezmose.mysecretsanta.data.ExclusionRole
import io.github.chezmose.mysecretsanta.data.ExclusionType
import io.github.chezmose.mysecretsanta.data.ExclusionWithMembers

/**
 * Paires interdites (donneur, receveur) d'une exclusion, pour [SecretSanta.draw] :
 * - couple et foyer : toutes les paires entre les personnes concernées, dans les deux sens ;
 * - sens unique : la seule paire (donneur, receveur).
 */
fun ExclusionWithMembers.forbiddenPairs(): Set<Pair<Long, Long>> =
    when (rule.type) {
        ExclusionType.COUPLE, ExclusionType.HOUSEHOLD -> {
            val ids = members.map { it.personId }
            buildSet { for (a in ids) for (b in ids) if (a != b) add(a to b) }
        }
        ExclusionType.ONE_WAY -> {
            val giver = members.firstOrNull { it.role == ExclusionRole.GIVER }?.personId
            val receiver = members.firstOrNull { it.role == ExclusionRole.RECEIVER }?.personId
            if (giver != null && receiver != null) setOf(giver to receiver) else emptySet()
        }
    }

fun List<ExclusionWithMembers>.forbiddenPairs(): Set<Pair<Long, Long>> =
    flatMapTo(mutableSetOf()) { it.forbiddenPairs() }
