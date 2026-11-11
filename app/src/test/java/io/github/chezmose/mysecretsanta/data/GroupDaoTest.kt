package io.github.chezmose.mysecretsanta.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Cohérence des groupes et de leurs exclusions dans la base. */
@RunWith(RobolectricTestRunner::class)
class GroupDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var persons: PersonDao
    private lateinit var groups: GroupDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        persons = db.personDao()
        groups = db.groupDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun person(name: String): Long = persons.insert(Person(name = name, phoneE164 = null))

    private suspend fun group(name: String, vararg members: Long): Long {
        val id = groups.insert(SantaGroup(name = name))
        groups.addMembers(members.map { GroupMember(groupId = id, personId = it) })
        return id
    }

    private suspend fun couple(groupId: Long, a: Long, b: Long): Long =
        groups.saveExclusion(
            ExclusionRule(groupId = groupId, type = ExclusionType.COUPLE, label = "Mariés"),
            listOf(ExclusionMember(0, a, ExclusionRole.MEMBER), ExclusionMember(0, b, ExclusionRole.MEMBER)),
        )

    private suspend fun household(groupId: Long, vararg ids: Long): Long =
        groups.saveExclusion(
            ExclusionRule(groupId = groupId, type = ExclusionType.HOUSEHOLD, label = null),
            ids.map { ExclusionMember(0, it, ExclusionRole.MEMBER) },
        )

    private suspend fun memberIdsOf(ruleId: Long): Set<Long>? =
        groups.getExclusion(ruleId)?.members?.map { it.personId }?.toSet()

    @Test
    fun `créer puis modifier une exclusion remplace ses personnes`(): Unit = runBlocking {
        val (a, b, c) = listOf(person("A"), person("B"), person("C"))
        val g = group("Famille", a, b, c)
        val ruleId = couple(g, a, b)
        assertEquals(setOf(a, b), memberIdsOf(ruleId))
        assertEquals("Mariés", groups.getExclusion(ruleId)?.rule?.label)

        groups.saveExclusion(
            ExclusionRule(id = ruleId, groupId = g, type = ExclusionType.ONE_WAY, label = null),
            listOf(ExclusionMember(0, c, ExclusionRole.GIVER), ExclusionMember(0, a, ExclusionRole.RECEIVER)),
        )
        val updated = groups.getExclusion(ruleId)!!
        assertEquals(ExclusionType.ONE_WAY, updated.rule.type)
        assertEquals(
            setOf(c to ExclusionRole.GIVER, a to ExclusionRole.RECEIVER),
            updated.members.map { it.personId to it.role }.toSet(),
        )
        assertEquals(1, groups.getExclusions(g).size)
    }

    @Test
    fun `retirer une personne du groupe supprime son couple`(): Unit = runBlocking {
        val (a, b, c) = listOf(person("A"), person("B"), person("C"))
        val g = group("Famille", a, b, c)
        val ruleId = couple(g, a, b)

        groups.removeMember(g, b)

        assertNull(groups.getExclusion(ruleId))
    }

    @Test
    fun `un foyer rétrécit, puis disparaît sous 2 personnes`(): Unit = runBlocking {
        val (a, b, c, d) = listOf(person("A"), person("B"), person("C"), person("D"))
        val g = group("Famille", a, b, c, d)
        val ruleId = household(g, a, b, c)

        groups.removeMember(g, c)
        assertEquals(setOf(a, b), memberIdsOf(ruleId))

        groups.removeMember(g, b)
        assertNull(groups.getExclusion(ruleId))
    }

    @Test
    fun `décocher un groupe sur la fiche personne retire ses exclusions dans ce groupe seulement`(): Unit =
        runBlocking {
            val (a, b) = listOf(person("A"), person("B"))
            val famille = group("Famille", a, b)
            val amis = group("Amis", a, b)
            val coupleFamille = couple(famille, a, b)
            val coupleAmis = couple(amis, a, b)

            groups.setMemberships(a, listOf(amis))

            assertNull(groups.getExclusion(coupleFamille))
            assertEquals(setOf(a, b), memberIdsOf(coupleAmis))
        }

    @Test
    fun `supprimer une personne supprime ses exclusions devenues incomplètes`(): Unit = runBlocking {
        val (a, b, c, d) = listOf(person("A"), person("B"), person("C"), person("D"))
        val g = group("Famille", a, b, c, d)
        val coupleId = couple(g, a, b)
        val householdId = household(g, a, c, d)

        persons.delete(a)

        assertNull(groups.getExclusion(coupleId))
        assertEquals(setOf(c, d), memberIdsOf(householdId))
    }

    @Test
    fun `supprimer le groupe supprime ses exclusions`(): Unit = runBlocking {
        val (a, b, c) = listOf(person("A"), person("B"), person("C"))
        val g = group("Famille", a, b, c)
        val ruleId = couple(g, a, b)

        groups.delete(g)

        assertNull(groups.getExclusion(ruleId))
        assertEquals(3, persons.observeAll().first().size)
    }

    @Test
    fun `dupliquer un groupe copie ses membres et ses exclusions`(): Unit = runBlocking {
        val (a, b, c) = listOf(person("A"), person("B"), person("C"))
        val g = group("Famille", a, b, c)
        couple(g, a, b)

        val copy = groups.duplicate(g, "Famille (copie)")

        assertEquals(setOf(a, b, c), groups.observeMembers(copy).first().map { it.id }.toSet())
        val copied = groups.getExclusions(copy).single()
        assertEquals(ExclusionType.COUPLE, copied.rule.type)
        assertEquals("Mariés", copied.rule.label)
        assertEquals(setOf(a, b), copied.members.map { it.personId }.toSet())
        // L'original est intact et indépendant.
        assertEquals(1, groups.getExclusions(g).size)
    }
}
