package io.github.chezmose.mysecretsanta.draw

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.chezmose.mysecretsanta.data.AppDatabase
import io.github.chezmose.mysecretsanta.data.DeliveryStatus
import io.github.chezmose.mysecretsanta.data.Draw
import io.github.chezmose.mysecretsanta.data.DrawStatus
import io.github.chezmose.mysecretsanta.data.ExclusionMember
import io.github.chezmose.mysecretsanta.data.ExclusionRole
import io.github.chezmose.mysecretsanta.data.ExclusionRule
import io.github.chezmose.mysecretsanta.data.ExclusionType
import io.github.chezmose.mysecretsanta.data.GroupMember
import io.github.chezmose.mysecretsanta.data.Participant
import io.github.chezmose.mysecretsanta.data.Person
import io.github.chezmose.mysecretsanta.data.SantaGroup
import io.github.chezmose.mysecretsanta.message.MessageTemplate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Random

/** Tirage d'un brouillon, envoi suivi, remise en brouillon. */
@RunWith(RobolectricTestRunner::class)
class DrawRunnerTest {

    private lateinit var db: AppDatabase
    private lateinit var runner: DrawRunner

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        runner = DrawRunner(db, Random(42))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private var phoneCounter = 10

    /** Une personne, avec un numéro unique, ou sans téléphone si [withPhone] est faux. */
    private suspend fun person(name: String, withPhone: Boolean = true): Person {
        val phone = if (withPhone) "+336000000${phoneCounter++}" else null
        val id = db.personDao().insert(Person(name = name, phoneE164 = phone))
        return Person(id, name, phone)
    }

    private suspend fun group(vararg members: Person): Long {
        val id = db.groupDao().insert(SantaGroup(name = "Famille"))
        db.groupDao().addMembers(members.map { GroupMember(groupId = id, personId = it.id) })
        return id
    }

    private suspend fun couple(groupId: Long, a: Person, b: Person) {
        db.groupDao().saveExclusion(
            ExclusionRule(groupId = groupId, type = ExclusionType.COUPLE, label = null),
            listOf(ExclusionMember(0, a.id, ExclusionRole.MEMBER), ExclusionMember(0, b.id, ExclusionRole.MEMBER)),
        )
    }

    private suspend fun oneWay(groupId: Long, giver: Person, receiver: Person) {
        db.groupDao().saveExclusion(
            ExclusionRule(groupId = groupId, type = ExclusionType.ONE_WAY, label = null),
            listOf(
                ExclusionMember(0, giver.id, ExclusionRole.GIVER),
                ExclusionMember(0, receiver.id, ExclusionRole.RECEIVER),
            ),
        )
    }

    private suspend fun draft(
        groupId: Long,
        vararg participants: Person,
        template: String = MessageTemplate.DEFAULT,
    ): Long =
        db.drawDao().saveDraft(
            Draw(groupId = groupId, name = "Noël", dateEpochDay = null, budget = null, messageTemplate = template, createdAt = 1),
            participants.map { Participant(drawId = 0, personId = it.id, name = it.name, phoneE164 = it.phoneE164) },
        )

    /** Vérifie le résultat enregistré et renvoie donneur → receveur, en noms. */
    private suspend fun assertValidResult(drawId: Long): Map<String, String> {
        assertEquals(DrawStatus.DRAWN, db.drawDao().get(drawId)?.status)
        val participants = db.drawDao().participants(drawId)
        val byId = participants.associateBy { it.id }
        val pairs = participants.associate { it.name to byId.getValue(checkNotNull(it.receiverParticipantId)).name }
        assertEquals(participants.map { it.name }.toSet(), pairs.values.toSet())
        pairs.forEach { (giver, receiver) -> assertNotEquals(giver, receiver) }
        return pairs
    }

    @Test
    fun `tirage enregistré, avec l'état de transmission de chacun`(): Unit = runBlocking {
        val people = listOf(person("Alice"), person("Bob"), person("Chloé"), person("Léo", withPhone = false))
        val g = group(*people.toTypedArray())
        val drawId = draft(g, *people.toTypedArray())

        assertEquals(DrawRunner.Outcome.Done, runner.run(drawId))

        val pairs = assertValidResult(drawId)
        assertEquals(4, pairs.size)
        val statuses = db.drawDao().participants(drawId).associate { it.name to it.status }
        assertEquals(DeliveryStatus.TO_SHOW, statuses["Léo"])
        assertEquals(DeliveryStatus.PENDING, statuses["Alice"])
    }

    @Test
    fun `nom et numéro figés au moment du tirage`(): Unit = runBlocking {
        val people = listOf(person("Alice"), person("Bob"), person("Chloé"))
        val g = group(*people.toTypedArray())
        val drawId = draft(g, *people.toTypedArray())
        db.personDao().update(people[0].copy(name = "Alice Martin", phoneE164 = "+33699999999"))

        runner.run(drawId)

        val alice = db.drawDao().participants(drawId).single { it.personId == people[0].id }
        assertEquals("Alice Martin", alice.name)
        assertEquals("+33699999999", alice.phoneE164)
    }

    @Test
    fun `exclusions respectées à chaque tirage`(): Unit = runBlocking {
        val (a, b, c, d) = listOf(person("Alice"), person("Bob"), person("Chloé"), person("David"))
        val g = group(a, b, c, d)
        couple(g, a, b)
        val drawId = draft(g, a, b, c, d)
        repeat(30) {
            assertEquals(DrawRunner.Outcome.Done, runner.run(drawId))
            val pairs = assertValidResult(drawId)
            assertNotEquals("Bob", pairs["Alice"])
            assertNotEquals("Alice", pairs["Bob"])
            pairs.forEach { (giver, receiver) -> assertNotEquals(giver, pairs[receiver]) } // pas de réciproque
            db.drawDao().resetToDraft(drawId)
        }
    }

    @Test
    fun `cas où le tirage n'a pas lieu`(): Unit = runBlocking {
        val (a, b, c) = listOf(person("Alice"), person("Bob"), person("Chloé"))
        val g = group(a, b, c)

        assertEquals(DrawRunner.Outcome.TooFewParticipants, runner.run(draft(g, a, b)))
        assertEquals(
            DrawRunner.Outcome.InvalidMessage::class,
            runner.run(draft(g, a, b, c, template = "Bonjour {donneur} !"))::class,
        )

        couple(g, a, b)
        val impossible = draft(g, a, b, c)
        assertTrue(runner.run(impossible) is DrawRunner.Outcome.Impossible)
        assertEquals(DrawStatus.DRAFT, db.drawDao().get(impossible)?.status)
    }

    @Test
    fun `possible seulement avec un échange réciproque, puis accepté`(): Unit = runBlocking {
        val (a, b, c, d) = listOf(person("Alice"), person("Bob"), person("Chloé"), person("David"))
        val g = group(a, b, c, d)
        // Alice et Bob ne peuvent offrir ni à Chloé ni à David : seule solution Alice ↔ Bob et Chloé ↔ David.
        oneWay(g, a, c)
        oneWay(g, a, d)
        oneWay(g, b, c)
        oneWay(g, b, d)
        val drawId = draft(g, a, b, c, d)

        assertEquals(DrawRunner.Outcome.OnlyWithReciprocal, runner.run(drawId))
        db.drawDao().setAllowReciprocal(drawId, true)
        assertEquals(DrawRunner.Outcome.Done, runner.run(drawId))
        assertEquals(mapOf("Alice" to "Bob", "Bob" to "Alice", "Chloé" to "David", "David" to "Chloé"), assertValidResult(drawId))
    }

    @Test
    fun `un tirage fait ne se refait pas sans repasser en brouillon`(): Unit = runBlocking {
        val people = listOf(person("Alice"), person("Bob"), person("Chloé"))
        val g = group(*people.toTypedArray())
        val drawId = draft(g, *people.toTypedArray())
        runner.run(drawId)

        assertEquals(DrawRunner.Outcome.NotADraft, runner.run(drawId))
    }

    @Test
    fun `transmission suivie, tirage envoyé quand tout est transmis`(): Unit = runBlocking {
        val people = listOf(person("Alice"), person("Bob"), person("Léo", withPhone = false))
        val g = group(*people.toTypedArray())
        val drawId = draft(g, *people.toTypedArray())
        runner.run(drawId)
        val byName = db.drawDao().participants(drawId).associateBy { it.name }
        val dao = db.drawDao()

        dao.updateStatus(byName.getValue("Alice").id, DeliveryStatus.SENDING, null, 1)
        dao.updateStatus(byName.getValue("Alice").id, DeliveryStatus.DELIVERED, null, 2)
        dao.updateStatus(byName.getValue("Alice").id, DeliveryStatus.SENT, null, 3) // compte rendu tardif
        assertEquals(DeliveryStatus.DELIVERED, dao.getParticipant(byName.getValue("Alice").id)?.status)

        dao.updateStatus(byName.getValue("Bob").id, DeliveryStatus.FAILED, "pas de réseau", 4)
        assertEquals("pas de réseau", dao.getParticipant(byName.getValue("Bob").id)?.statusError)
        assertEquals(DrawStatus.DRAWN, dao.get(drawId)?.status)

        dao.updateStatus(byName.getValue("Bob").id, DeliveryStatus.SENT, null, 5)
        assertEquals(DrawStatus.DRAWN, dao.get(drawId)?.status) // Léo n'a pas encore vu son résultat

        dao.updateStatus(byName.getValue("Léo").id, DeliveryStatus.SHOWN, null, 6)
        assertEquals(DrawStatus.SENT, dao.get(drawId)?.status)
    }

    @Test
    fun `refaire le tirage le remet en brouillon et ignore les comptes rendus périmés`(): Unit = runBlocking {
        val people = listOf(person("Alice"), person("Bob"), person("Chloé"))
        val g = group(*people.toTypedArray())
        val drawId = draft(g, *people.toTypedArray())
        runner.run(drawId)
        db.drawDao().setRevealed(drawId)
        val alice = db.drawDao().participants(drawId).single { it.name == "Alice" }

        db.drawDao().resetToDraft(drawId)

        val draw = db.drawDao().get(drawId)!!
        assertEquals(DrawStatus.DRAFT, draw.status)
        assertEquals(false, draw.revealed)
        val participants = db.drawDao().participants(drawId)
        assertEquals(3, participants.size)
        participants.forEach {
            assertNull(it.receiverParticipantId)
            assertNull(it.status)
        }
        db.drawDao().updateStatus(alice.id, DeliveryStatus.SENT, null, 1)
        assertNull(db.drawDao().getParticipant(alice.id)?.status)
    }
}
