package io.github.chezmose.mysecretsanta.draw

import io.github.chezmose.mysecretsanta.data.ExclusionMember
import io.github.chezmose.mysecretsanta.data.ExclusionRole
import io.github.chezmose.mysecretsanta.data.ExclusionRule
import io.github.chezmose.mysecretsanta.data.ExclusionType
import io.github.chezmose.mysecretsanta.data.ExclusionWithMembers
import io.github.chezmose.mysecretsanta.data.Person
import io.github.chezmose.mysecretsanta.ui.exclusions.describe
import io.github.chezmose.mysecretsanta.ui.exclusions.frenchList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExclusionsTest {

    private var nextRuleId = 1L

    private fun exclusion(type: ExclusionType, vararg members: Pair<Long, ExclusionRole>): ExclusionWithMembers {
        val ruleId = nextRuleId++
        return ExclusionWithMembers(
            ExclusionRule(id = ruleId, groupId = 1, type = type, label = null),
            members.map { (personId, role) -> ExclusionMember(ruleId, personId, role) },
        )
    }

    private fun couple(a: Long, b: Long) =
        exclusion(ExclusionType.COUPLE, a to ExclusionRole.MEMBER, b to ExclusionRole.MEMBER)

    private fun oneWay(giver: Long, receiver: Long) =
        exclusion(ExclusionType.ONE_WAY, giver to ExclusionRole.GIVER, receiver to ExclusionRole.RECEIVER)

    private fun household(vararg ids: Long) =
        exclusion(ExclusionType.HOUSEHOLD, *ids.map { it to ExclusionRole.MEMBER }.toTypedArray())

    private fun people(vararg names: String) =
        names.mapIndexed { index, name -> Person(id = index + 1L, name = name, phoneE164 = null) }

    @Test
    fun `couple interdit les deux sens`() {
        assertEquals(setOf(1L to 2L, 2L to 1L), couple(1, 2).forbiddenPairs())
    }

    @Test
    fun `sens unique interdit un seul sens`() {
        assertEquals(setOf(1L to 2L), oneWay(1, 2).forbiddenPairs())
    }

    @Test
    fun `foyer interdit toutes les paires entre ses membres`() {
        assertEquals(
            setOf(1L to 2L, 2L to 1L, 1L to 3L, 3L to 1L, 2L to 3L, 3L to 2L),
            household(1, 2, 3).forbiddenPairs(),
        )
    }

    @Test
    fun `plusieurs exclusions se cumulent`() {
        assertEquals(
            setOf(1L to 2L, 2L to 1L, 3L to 4L),
            listOf(couple(1, 2), oneWay(3, 4)).forbiddenPairs(),
        )
    }

    @Test
    fun `descriptions lisibles`() {
        val names = mapOf(1L to "Alice", 2L to "Bob", 3L to "Léo")
        assertEquals("Alice et Bob", couple(2, 1).describe { names.getValue(it) })
        assertEquals("Alice n'offre pas à Bob", oneWay(1, 2).describe { names.getValue(it) })
        assertEquals("Alice, Bob et Léo", household(3, 1, 2).describe { names.getValue(it) })
        assertEquals("", frenchList(emptyList()))
        assertEquals("Alice", frenchList(listOf("Alice")))
    }

    @Test
    fun `faisabilité, trop peu de membres`() {
        assertEquals(Feasibility.TooFewMembers, assessFeasibility(people("Alice", "Bob"), emptyList()))
    }

    @Test
    fun `faisabilité, possible`() {
        val members = people("Alice", "Bob", "Chloé", "David")
        assertEquals(Feasibility.Possible, assessFeasibility(members, listOf(couple(1, 2))))
    }

    @Test
    fun `faisabilité, impossible, avec la personne bloquée`() {
        val members = people("Alice", "Bob", "Chloé", "David")
        // Alice ne peut offrir ni à Bob (couple) ni à Chloé et David (sens unique).
        val result = assessFeasibility(members, listOf(couple(1, 2), oneWay(1, 3), oneWay(1, 4)))
        assertTrue(result is Feasibility.Impossible)
        assertEquals(listOf("Alice ne peut offrir à personne."), (result as Feasibility.Impossible).reasons)
    }

    @Test
    fun `faisabilité, impossible pour un foyer trop grand`() {
        // 3 membres d'un même foyer sur 4 : ils ne peuvent offrir qu'à David, qui ne peut recevoir qu'une fois.
        val members = people("Alice", "Bob", "Chloé", "David")
        val result = assessFeasibility(members, listOf(household(1, 2, 3)))
        assertTrue(result is Feasibility.Impossible)
    }

    @Test
    fun `faisabilité, possible seulement avec un échange réciproque`() {
        // Alice et Bob ne peuvent offrir ni à Chloé ni à David : seule solution Alice ↔ Bob et Chloé ↔ David.
        val members = people("Alice", "Bob", "Chloé", "David")
        val exclusions = listOf(oneWay(1, 3), oneWay(1, 4), oneWay(2, 3), oneWay(2, 4))
        assertEquals(Feasibility.OnlyWithReciprocal, assessFeasibility(members, exclusions))
    }
}
