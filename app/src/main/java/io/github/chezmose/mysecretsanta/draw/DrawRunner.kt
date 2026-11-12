package io.github.chezmose.mysecretsanta.draw

import io.github.chezmose.mysecretsanta.data.AppDatabase
import io.github.chezmose.mysecretsanta.data.DrawStatus
import io.github.chezmose.mysecretsanta.message.MessageTemplate
import java.security.SecureRandom
import java.util.Random

/**
 * Exécute le tirage d'un brouillon (SPEC.md §5.4) : fige les participants avec leur nom et leur
 * numéro du moment, tire au sort avec les exclusions du groupe, et enregistre le résultat.
 */
class DrawRunner(private val db: AppDatabase, private val random: Random = SecureRandom()) {

    sealed interface Outcome {
        data object Done : Outcome
        data object NotADraft : Outcome
        data object GroupDeleted : Outcome
        data object TooFewParticipants : Outcome
        data class InvalidMessage(val message: String) : Outcome
        data class Impossible(val reasons: List<String>) : Outcome

        /** Possible seulement avec un échange réciproque : à faire accepter, puis relancer. */
        data object OnlyWithReciprocal : Outcome
    }

    suspend fun run(drawId: Long): Outcome {
        val draw = db.drawDao().get(drawId) ?: return Outcome.NotADraft
        if (draw.status != DrawStatus.DRAFT) return Outcome.NotADraft
        val groupId = draw.groupId ?: return Outcome.GroupDeleted

        // Participants : la sélection du brouillon, parmi les membres actuels du groupe, à jour.
        val selection = db.drawDao().participants(drawId).mapNotNull { it.personId }.toSet()
        val participants = db.groupDao().getMembers(groupId).filter { it.id in selection }
        if (participants.size < SecretSanta.MIN_PARTICIPANTS) return Outcome.TooFewParticipants

        val longestName = participants.maxBy { it.name.length }.name
        val messageError = MessageTemplate.validate(draw.messageTemplate)
            ?: MessageTemplate.checkLength(draw.messageTemplate, longestName, draw.name, draw.budget)
        if (messageError != null) return Outcome.InvalidMessage(messageError)

        val exclusions = db.groupDao().getExclusions(groupId)
        val ids = participants.map { it.id }
        return when (
            val result = SecretSanta.draw(ids, exclusions.forbiddenPairs(), draw.allowReciprocal, random)
        ) {
            is SecretSanta.Result.Success -> {
                db.drawDao().saveResult(draw, participants, result.assignments)
                Outcome.Done
            }
            SecretSanta.Result.OnlyWithReciprocal -> Outcome.OnlyWithReciprocal
            SecretSanta.Result.TooFewParticipants -> Outcome.TooFewParticipants
            SecretSanta.Result.Impossible -> {
                val reasons = (assessFeasibility(participants, exclusions) as? Feasibility.Impossible)?.reasons
                Outcome.Impossible(reasons.orEmpty())
            }
        }
    }
}
