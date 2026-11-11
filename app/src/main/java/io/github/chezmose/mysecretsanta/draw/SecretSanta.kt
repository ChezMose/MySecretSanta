package io.github.chezmose.mysecretsanta.draw

import java.security.SecureRandom
import java.util.Random

/**
 * Tirage au sort du Secret Santa (SPEC.md §5.4).
 *
 * Règles :
 * - chaque participant offre à exactement un participant et reçoit d'exactement un participant ;
 * - personne ne se tire lui-même ;
 * - aucune paire interdite (exclusions) n'est attribuée ;
 * - pas d'échange réciproque (A → B et B → A), sauf si [draw] reçoit `allowReciprocal = true`.
 *
 * Méthode :
 * 1. Un couplage parfait (algorithme de Kuhn) vérifie qu'au moins une répartition respecte les
 *    exclusions. Sinon : [Result.Impossible].
 * 2. Échantillonnage par rejet : on mélange au hasard jusqu'à obtenir une répartition valide.
 *    Chaque répartition valide a exactement la même probabilité de sortir.
 * 3. Si le rejet n'aboutit pas (exclusions très serrées), une recherche exhaustive au hasard
 *    trouve une répartition valide, ou prouve qu'il n'en existe qu'avec un échange réciproque.
 *    Cette solution de secours est aléatoire, mais pas parfaitement équiprobable.
 */
object SecretSanta {

    const val MIN_PARTICIPANTS = 3

    sealed interface Result {
        /** Chaque clé (donneur) offre un cadeau à sa valeur (receveur). */
        data class Success(val assignments: Map<Long, Long>) : Result

        /** Moins de [MIN_PARTICIPANTS] participants. */
        data object TooFewParticipants : Result

        /** Aucune répartition ne respecte les exclusions. */
        data object Impossible : Result

        /** Une répartition existe, mais seulement avec au moins un échange réciproque. */
        data object OnlyWithReciprocal : Result
    }

    internal const val DEFAULT_REJECTION_TRIES = 200_000
    private const val MAX_SEARCH_NODES = 5_000_000

    /**
     * @param participants identifiants des participants, sans doublon.
     * @param forbidden paires interdites (donneur, receveur), dans un seul sens.
     *   Un couple s'exprime par deux paires : (A, B) et (B, A).
     * @param allowReciprocal autorise A → B et B → A dans le même tirage.
     */
    fun draw(
        participants: List<Long>,
        forbidden: Set<Pair<Long, Long>> = emptySet(),
        allowReciprocal: Boolean = false,
        random: Random = SecureRandom(),
    ): Result = draw(participants, forbidden, allowReciprocal, random, DEFAULT_REJECTION_TRIES)

    internal fun draw(
        participants: List<Long>,
        forbidden: Set<Pair<Long, Long>>,
        allowReciprocal: Boolean,
        random: Random,
        rejectionTries: Int,
    ): Result {
        require(participants.toSet().size == participants.size) { "Participant en double." }
        val ids = participants
        val n = ids.size
        if (n < MIN_PARTICIPANTS) return Result.TooFewParticipants

        // allowed[i][j] : le participant i peut offrir au participant j (sans tenir compte de la réciprocité).
        val allowed = Array(n) { i -> BooleanArray(n) { j -> i != j && (ids[i] to ids[j]) !in forbidden } }

        val matching = perfectMatching(allowed, random) ?: return Result.Impossible

        val permutation = IntArray(n) { it }
        repeat(rejectionTries) {
            permutation.shuffle(random)
            if (isValid(permutation, allowed, allowReciprocal)) return Result.Success(toMap(ids, permutation))
        }

        if (allowReciprocal) return Result.Success(toMap(ids, matching))
        val found = searchWithoutReciprocal(allowed, random)
        return if (found != null) Result.Success(toMap(ids, found)) else Result.OnlyWithReciprocal
    }

    private fun isValid(receiverOf: IntArray, allowed: Array<BooleanArray>, allowReciprocal: Boolean): Boolean {
        for (giver in receiverOf.indices) {
            val receiver = receiverOf[giver]
            if (!allowed[giver][receiver]) return false
            if (!allowReciprocal && receiverOf[receiver] == giver) return false
        }
        return true
    }

    /** Couplage parfait donneurs → receveurs (algorithme de Kuhn), ou `null` s'il n'en existe pas. */
    private fun perfectMatching(allowed: Array<BooleanArray>, random: Random): IntArray? {
        val n = allowed.size
        val giverOf = IntArray(n) { -1 }
        val receiverOrder = IntArray(n) { it }.apply { shuffle(random) }

        fun augment(giver: Int, seen: BooleanArray): Boolean {
            for (receiver in receiverOrder) {
                if (allowed[giver][receiver] && !seen[receiver]) {
                    seen[receiver] = true
                    if (giverOf[receiver] == -1 || augment(giverOf[receiver], seen)) {
                        giverOf[receiver] = giver
                        return true
                    }
                }
            }
            return false
        }

        for (giver in IntArray(n) { it }.apply { shuffle(random) }) {
            if (!augment(giver, BooleanArray(n))) return null
        }
        return IntArray(n).also { receiverOf -> giverOf.forEachIndexed { receiver, giver -> receiverOf[giver] = receiver } }
    }

    /**
     * Recherche exhaustive d'une répartition sans échange réciproque, en traitant d'abord le donneur
     * qui a le moins de choix. Renvoie `null` si aucune n'existe (ou au-delà de [MAX_SEARCH_NODES]).
     */
    private fun searchWithoutReciprocal(allowed: Array<BooleanArray>, random: Random): IntArray? {
        val n = allowed.size
        val receiverOf = IntArray(n) { -1 }
        val taken = BooleanArray(n)
        var nodes = 0

        fun options(giver: Int): List<Int> =
            (0 until n).filter { r -> allowed[giver][r] && !taken[r] && receiverOf[r] != giver }

        fun backtrack(assigned: Int): Boolean {
            if (assigned == n) return true
            if (++nodes > MAX_SEARCH_NODES) return false
            var bestGiver = -1
            var bestOptions: List<Int> = emptyList()
            for (giver in 0 until n) {
                if (receiverOf[giver] != -1) continue
                val giverOptions = options(giver)
                if (giverOptions.isEmpty()) return false
                if (bestGiver == -1 || giverOptions.size < bestOptions.size) {
                    bestGiver = giver
                    bestOptions = giverOptions
                }
            }
            for (receiver in bestOptions.shuffled(random)) {
                receiverOf[bestGiver] = receiver
                taken[receiver] = true
                if (backtrack(assigned + 1)) return true
                receiverOf[bestGiver] = -1
                taken[receiver] = false
            }
            return false
        }

        return if (backtrack(0)) receiverOf else null
    }

    private fun IntArray.shuffle(random: Random) {
        for (i in lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = this[i]
            this[i] = this[j]
            this[j] = tmp
        }
    }

    private fun toMap(ids: List<Long>, receiverOf: IntArray): Map<Long, Long> =
        ids.indices.associate { ids[it] to ids[receiverOf[it]] }
}
