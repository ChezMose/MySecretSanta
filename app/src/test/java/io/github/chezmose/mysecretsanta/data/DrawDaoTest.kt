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

/** Tirages en brouillon et cohérence avec les groupes et les personnes. */
@RunWith(RobolectricTestRunner::class)
class DrawDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var persons: PersonDao
    private lateinit var groups: GroupDao
    private lateinit var draws: DrawDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        persons = db.personDao()
        groups = db.groupDao()
        draws = db.drawDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun person(name: String, phone: String? = null): Person {
        val id = persons.insert(Person(name = name, phoneE164 = phone))
        return Person(id, name, phone)
    }

    private suspend fun group(name: String, vararg members: Person): Long {
        val id = groups.insert(SantaGroup(name = name))
        groups.addMembers(members.map { GroupMember(groupId = id, personId = it.id) })
        return id
    }

    private fun participant(p: Person) = Participant(drawId = 0, personId = p.id, name = p.name, phoneE164 = p.phoneE164)

    private suspend fun draft(groupId: Long, name: String, vararg participants: Person, createdAt: Long = 1): Long =
        draws.saveDraft(
            Draw(
                groupId = groupId,
                name = name,
                dateEpochDay = null,
                budget = null,
                messageTemplate = "Tu offres à {receveur}.",
                createdAt = createdAt,
            ),
            participants.map(::participant),
        )

    private suspend fun participantNames(drawId: Long): Set<String> = draws.participants(drawId).map { it.name }.toSet()

    @Test
    fun `enregistrer un brouillon puis remplacer ses participants`(): Unit = runBlocking {
        val (a, b, c) = listOf(person("A", "+33611111111"), person("B"), person("C"))
        val g = group("Famille", a, b, c)
        val id = draft(g, "Noël 2026", a, b, c)

        assertEquals(setOf("A", "B", "C"), participantNames(id))
        assertEquals("+33611111111", draws.participants(id).first { it.name == "A" }.phoneE164)
        assertEquals(DrawStatus.DRAFT, draws.get(id)?.status)

        val draw = draws.get(id)!!
        draws.saveDraft(draw.copy(name = "Noël", budget = "30 €"), listOf(participant(a), participant(c)))

        assertEquals(setOf("A", "C"), participantNames(id))
        assertEquals("Noël", draws.get(id)?.name)
        assertEquals("30 €", draws.get(id)?.budget)
    }

    @Test
    fun `liste des tirages, du plus récent au plus ancien`(): Unit = runBlocking {
        val (a, b, c) = listOf(person("A"), person("B"), person("C"))
        val g = group("Famille", a, b, c)
        draft(g, "Ancien", a, b, createdAt = 1)
        draft(g, "Récent", a, b, c, createdAt = 2)

        val summaries = draws.observeSummaries().first()
        assertEquals(listOf("Récent", "Ancien"), summaries.map { it.name })
        assertEquals(listOf(3, 2), summaries.map { it.participantCount })
        assertEquals(listOf("Famille", "Famille"), summaries.map { it.groupName })
    }

    @Test
    fun `quitter le groupe retire la personne des brouillons, pas des tirages faits`(): Unit = runBlocking {
        val (a, b, c) = listOf(person("A"), person("B"), person("C"))
        val g = group("Famille", a, b, c)
        val brouillon = draft(g, "Brouillon", a, b, c)
        val fait = draft(g, "Fait", a, b, c)
        draws.updateDraw(draws.get(fait)!!.copy(status = DrawStatus.DRAWN))

        groups.removeMember(g, b.id)

        assertEquals(setOf("A", "C"), participantNames(brouillon))
        assertEquals(setOf("A", "B", "C"), participantNames(fait))
    }

    @Test
    fun `supprimer une personne la retire des brouillons et garde son nom dans les tirages faits`(): Unit =
        runBlocking {
            val (a, b, c) = listOf(person("A"), person("B"), person("C"))
            val g = group("Famille", a, b, c)
            val brouillon = draft(g, "Brouillon", a, b, c)
            val fait = draft(g, "Fait", a, b, c)
            draws.updateDraw(draws.get(fait)!!.copy(status = DrawStatus.DRAWN))

            persons.delete(b.id)

            assertEquals(setOf("A", "C"), participantNames(brouillon))
            assertEquals(setOf("A", "B", "C"), participantNames(fait))
            assertNull(draws.participants(fait).first { it.name == "B" }.personId)
        }

    @Test
    fun `supprimer le groupe supprime ses brouillons et garde ses tirages faits`(): Unit = runBlocking {
        val (a, b, c) = listOf(person("A"), person("B"), person("C"))
        val g = group("Famille", a, b, c)
        val brouillon = draft(g, "Brouillon", a, b, c)
        val fait = draft(g, "Fait", a, b, c)
        draws.updateDraw(draws.get(fait)!!.copy(status = DrawStatus.DRAWN))

        groups.delete(g)

        assertNull(draws.get(brouillon))
        assertNull(draws.get(fait)?.groupId)
        assertEquals(setOf("A", "B", "C"), participantNames(fait))
        assertEquals(listOf<String?>(null), draws.observeSummaries().first().map { it.groupName })
    }

    @Test
    fun `supprimer un tirage supprime ses participants`(): Unit = runBlocking {
        val (a, b, c) = listOf(person("A"), person("B"), person("C"))
        val g = group("Famille", a, b, c)
        val id = draft(g, "Noël", a, b, c)

        draws.delete(id)

        assertNull(draws.get(id))
        assertEquals(emptyList<Participant>(), draws.participants(id))
        assertEquals(3, persons.observeAll().first().size)
    }
}
