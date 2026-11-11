package io.github.chezmose.mysecretsanta.draw

import io.github.chezmose.mysecretsanta.data.ExclusionWithMembers
import io.github.chezmose.mysecretsanta.data.Person
import java.util.Random

/** Le tirage est-il possible avec les membres et les exclusions actuels d'un groupe ? */
sealed interface Feasibility {
    data object TooFewMembers : Feasibility
    data object Possible : Feasibility
    data object OnlyWithReciprocal : Feasibility

    /** @param reasons explications lisibles, par exemple « Alice ne peut offrir à personne. ». */
    data class Impossible(val reasons: List<String>) : Feasibility
}

/** Évalue la faisabilité d'un tirage, et explique les blocages évidents. */
fun assessFeasibility(members: List<Person>, exclusions: List<ExclusionWithMembers>): Feasibility {
    if (members.size < SecretSanta.MIN_PARTICIPANTS) return Feasibility.TooFewMembers
    val ids = members.map { it.id }
    val forbidden = exclusions.forbiddenPairs()
    return when (SecretSanta.draw(ids, forbidden, allowReciprocal = false, random = Random())) {
        is SecretSanta.Result.Success -> Feasibility.Possible
        SecretSanta.Result.OnlyWithReciprocal -> Feasibility.OnlyWithReciprocal
        SecretSanta.Result.TooFewParticipants -> Feasibility.TooFewMembers
        SecretSanta.Result.Impossible -> Feasibility.Impossible(
            members.flatMap { person ->
                val others = ids.filter { it != person.id }
                buildList {
                    if (others.all { (person.id to it) in forbidden }) {
                        add("${person.name} ne peut offrir à personne.")
                    }
                    if (others.all { (it to person.id) in forbidden }) {
                        add("Personne ne peut offrir à ${person.name}.")
                    }
                }
            },
        )
    }
}
