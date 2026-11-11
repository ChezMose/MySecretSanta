package io.github.chezmose.mysecretsanta.draw

import io.github.chezmose.mysecretsanta.draw.SecretSanta.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random
import kotlin.math.abs

class SecretSantaTest {

    private fun ids(n: Int): List<Long> = (1L..n.toLong()).toList()

    /** Un couple : ni A → B, ni B → A. */
    private fun couple(a: Long, b: Long) = setOf(a to b, b to a)

    /** Vérifie toutes les règles du tirage sur un résultat réussi. */
    private fun assertValid(
        participants: List<Long>,
        forbidden: Set<Pair<Long, Long>>,
        allowReciprocal: Boolean,
        result: Result,
    ): Map<Long, Long> {
        if (result !is Result.Success) fail("Tirage attendu, obtenu : $result")
        val assignments = (result as Result.Success).assignments
        assertEquals("Chaque participant offre un cadeau", participants.toSet(), assignments.keys)
        assertEquals("Chaque participant reçoit un cadeau", participants.toSet(), assignments.values.toSet())
        for ((giver, receiver) in assignments) {
            assertTrue("$giver se tire lui-même", giver != receiver)
            assertTrue("$giver → $receiver est exclu", (giver to receiver) !in forbidden)
            if (!allowReciprocal) {
                assertTrue("Échange réciproque $giver ↔ $receiver", assignments[receiver] != giver)
            }
        }
        return assignments
    }

    // --- Vérité de référence : énumération de toutes les répartitions possibles ---

    private enum class Feasibility { POSSIBLE, ONLY_WITH_RECIPROCAL, IMPOSSIBLE }

    private fun bruteForce(n: Int, forbidden: Set<Pair<Long, Long>>): Feasibility {
        var withReciprocal = false
        var withoutReciprocal = false
        val receiverOf = IntArray(n) { -1 }
        val taken = BooleanArray(n)

        fun visit(giver: Int) {
            if (withoutReciprocal) return
            if (giver == n) {
                withReciprocal = true
                if ((0 until n).none { receiverOf[receiverOf[it]] == it }) withoutReciprocal = true
                return
            }
            for (receiver in 0 until n) {
                if (receiver == giver || taken[receiver]) continue
                if ((giver + 1L to receiver + 1L) in forbidden) continue
                receiverOf[giver] = receiver
                taken[receiver] = true
                visit(giver + 1)
                taken[receiver] = false
            }
        }
        visit(0)
        return when {
            withoutReciprocal -> Feasibility.POSSIBLE
            withReciprocal -> Feasibility.ONLY_WITH_RECIPROCAL
            else -> Feasibility.IMPOSSIBLE
        }
    }

    private fun randomForbidden(n: Int, random: Random, density: Double): Set<Pair<Long, Long>> =
        buildSet {
            for (a in 1L..n) for (b in 1L..n) if (a != b && random.nextDouble() < density) add(a to b)
        }

    // --- Tests ---

    @Test
    fun `moins de 3 participants`() {
        assertEquals(Result.TooFewParticipants, SecretSanta.draw(emptyList()))
        assertEquals(Result.TooFewParticipants, SecretSanta.draw(ids(1)))
        assertEquals(Result.TooFewParticipants, SecretSanta.draw(ids(2)))
    }

    @Test
    fun `participant en double refusé`() {
        try {
            SecretSanta.draw(listOf(1L, 2L, 2L))
            fail("Un participant en double aurait dû être refusé.")
        } catch (_: IllegalArgumentException) {
            // Attendu.
        }
    }

    @Test
    fun `3 participants donnent un cycle A → B → C → A`() {
        val random = Random(1)
        val seen = mutableSetOf<Map<Long, Long>>()
        repeat(200) {
            seen += assertValid(ids(3), emptySet(), false, SecretSanta.draw(ids(3), random = random))
        }
        // Il n'existe que deux cycles possibles, et les deux doivent sortir.
        assertEquals(2, seen.size)
    }

    @Test
    fun `de 3 à 40 participants sans exclusion, toujours valide`() {
        val random = Random(2)
        for (n in 3..40) {
            repeat(200) {
                assertValid(ids(n), emptySet(), false, SecretSanta.draw(ids(n), random = random))
            }
        }
    }

    @Test
    fun `couples et foyers respectés sur des groupes réalistes`() {
        val random = Random(3)
        for (n in 6..30) {
            // Des couples sur les premiers participants, un participant sur trois en couple.
            val forbidden = (1L..n / 3L).flatMap { couple(2 * it - 1, 2 * it) }.toSet()
            repeat(100) {
                assertValid(ids(n), forbidden, false, SecretSanta.draw(ids(n), forbidden, random = random))
            }
        }
    }

    @Test
    fun `3 participants dont un couple, impossible`() {
        // A et B ne peuvent offrir qu'à C : impossible.
        assertEquals(Result.Impossible, SecretSanta.draw(ids(3), couple(1, 2)))
    }

    @Test
    fun `participant exclu de tous les autres, impossible`() {
        val forbidden = (2L..5L).map { 1L to it }.toSet()
        assertEquals(Result.Impossible, SecretSanta.draw(ids(5), forbidden))
    }

    @Test
    fun `possible seulement avec un échange réciproque`() {
        // A et B ne peuvent offrir ni à C ni à D : seule solution A ↔ B et C ↔ D.
        val forbidden = setOf(1L to 3L, 1L to 4L, 2L to 3L, 2L to 4L)
        assertEquals(Result.OnlyWithReciprocal, SecretSanta.draw(ids(4), forbidden))

        val assignments = assertValid(
            ids(4),
            forbidden,
            true,
            SecretSanta.draw(ids(4), forbidden, allowReciprocal = true),
        )
        assertEquals(mapOf(1L to 2L, 2L to 1L, 3L to 4L, 4L to 3L), assignments)
    }

    @Test
    fun `verdict identique à l'énumération complète, de 3 à 8 participants`() {
        val random = Random(4)
        for (n in 3..8) {
            repeat(60) { round ->
                val forbidden = randomForbidden(n, random, density = 0.15 + 0.1 * (round % 6))
                val expected = bruteForce(n, forbidden)
                // Avec et sans la recherche de secours (0 essai de rejet).
                for (tries in listOf(SecretSanta.DEFAULT_REJECTION_TRIES, 0)) {
                    val result = SecretSanta.draw(ids(n), forbidden, false, random, tries)
                    when (expected) {
                        Feasibility.POSSIBLE -> assertValid(ids(n), forbidden, false, result)
                        Feasibility.ONLY_WITH_RECIPROCAL -> assertEquals(Result.OnlyWithReciprocal, result)
                        Feasibility.IMPOSSIBLE -> assertEquals(Result.Impossible, result)
                    }
                    if (expected != Feasibility.IMPOSSIBLE) {
                        val withReciprocal = SecretSanta.draw(ids(n), forbidden, true, random, tries)
                        assertValid(ids(n), forbidden, true, withReciprocal)
                    }
                }
            }
        }
    }

    @Test
    fun `recherche de secours valide sur un grand groupe`() {
        val random = Random(5)
        val n = 30
        val forbidden = (1L..10L).flatMap { couple(2 * it - 1, 2 * it) }.toSet()
        repeat(20) {
            val result = SecretSanta.draw(ids(n), forbidden, false, random, rejectionTries = 0)
            assertValid(ids(n), forbidden, false, result)
        }
    }

    @Test
    fun `tirage équitable sans échange réciproque`() {
        // 4 participants : 6 cycles possibles, chacun doit sortir environ 1 fois sur 6.
        assertUniform(n = 4, allowReciprocal = false, expectedOutcomes = 6)
    }

    @Test
    fun `tirage équitable avec échanges réciproques autorisés`() {
        // 4 participants : 9 répartitions où personne ne se tire lui-même.
        assertUniform(n = 4, allowReciprocal = true, expectedOutcomes = 9)
    }

    private fun assertUniform(n: Int, allowReciprocal: Boolean, expectedOutcomes: Int) {
        val random = Random(6)
        val perOutcome = 10_000
        val counts = mutableMapOf<Map<Long, Long>, Int>()
        repeat(perOutcome * expectedOutcomes) {
            val result = SecretSanta.draw(ids(n), emptySet(), allowReciprocal, random)
            val assignments = assertValid(ids(n), emptySet(), allowReciprocal, result)
            counts[assignments] = (counts[assignments] ?: 0) + 1
        }
        assertEquals(expectedOutcomes, counts.size)
        // Écart toléré : 5 % (plus de 5 écarts-types pour 10 000 tirages par répartition).
        for ((assignments, count) in counts) {
            assertTrue(
                "Répartition $assignments sortie $count fois au lieu d'environ $perOutcome",
                abs(count - perOutcome) < perOutcome * 0.05,
            )
        }
    }

    @Test
    fun `50 participants avec exclusions, en moins d'une seconde`() {
        val random = Random(7)
        val forbidden = (1L..20L).flatMap { couple(2 * it - 1, 2 * it) }.toSet()
        val start = System.nanoTime()
        repeat(10) {
            assertValid(ids(50), forbidden, false, SecretSanta.draw(ids(50), forbidden, random = random))
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("10 tirages de 50 participants en $elapsedMs ms", elapsedMs < 10_000)
    }
}
