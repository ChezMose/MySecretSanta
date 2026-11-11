package io.github.chezmose.mysecretsanta.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Vérifie que la mise à jour de l'app conserve les données de l'utilisateur.
 * Chaque test crée une base dans l'ancien format, avec des données, puis l'ouvre avec le code actuel.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"
    private var database: AppDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(dbName)
    }

    /** Schéma v1, tel que créé par Room pour la première version de l'app. */
    private fun createVersion1(fill: SQLiteDatabase.() -> Unit) = createDatabase(1, fill)

    /**
     * Schéma v1 ou v2 (app/schemas/…/2.json). Seule différence : le numéro devient facultatif en v2.
     */
    private fun createDatabase(version: Int, fill: SQLiteDatabase.() -> Unit) {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val phoneColumn = if (version == 1) "`phoneE164` TEXT NOT NULL" else "`phoneE164` TEXT"
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `person` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, $phoneColumn)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_person_phoneE164` ON `person` (`phoneE164`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `santa_group` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `group_member` (`groupId` INTEGER NOT NULL, `personId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`groupId`, `personId`), " +
                "FOREIGN KEY(`groupId`) REFERENCES `santa_group`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`personId`) REFERENCES `person`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_member_personId` ON `group_member` (`personId`)")
        db.fill()
        db.version = version
        db.close()
    }

    @Test
    fun `migration 2 vers 3 conserve les données et ajoute les exclusions`(): Unit = runBlocking {
        createDatabase(2) {
            execSQL("INSERT INTO person (id, name, phoneE164) VALUES (1, 'Alice', '+33611111111')")
            execSQL("INSERT INTO person (id, name, phoneE164) VALUES (2, 'Bob', NULL)")
            execSQL("INSERT INTO santa_group (id, name) VALUES (10, 'Famille')")
            execSQL("INSERT INTO group_member (groupId, personId) VALUES (10, 1)")
            execSQL("INSERT INTO group_member (groupId, personId) VALUES (10, 2)")
        }

        val db = openCurrent()

        assertEquals(
            listOf(Person(1, "Alice", "+33611111111"), Person(2, "Bob", null)),
            db.personDao().observeAll().first().sortedBy { it.id },
        )
        assertEquals(2, db.groupDao().observeSummaries().first().single().memberCount)
        assertEquals(emptyList<ExclusionWithMembers>(), db.groupDao().getExclusions(10))

        val ruleId = db.groupDao().saveExclusion(
            ExclusionRule(groupId = 10, type = ExclusionType.COUPLE, label = "Mariés"),
            listOf(ExclusionMember(0, 1, ExclusionRole.MEMBER), ExclusionMember(0, 2, ExclusionRole.MEMBER)),
        )
        assertEquals(setOf(1L, 2L), db.groupDao().getExclusion(ruleId)?.members?.map { it.personId }?.toSet())
    }

    private fun openCurrent(): AppDatabase = AppDatabase.build(context, dbName).also { database = it }

    @Test
    fun `depuis la v1, personnes, groupes et membres conservés`(): Unit = runBlocking {
        createVersion1 {
            execSQL("INSERT INTO person (id, name, phoneE164) VALUES (1, 'Alice', '+33611111111')")
            execSQL("INSERT INTO person (id, name, phoneE164) VALUES (2, 'Bob', '+33622222222')")
            execSQL("INSERT INTO santa_group (id, name) VALUES (10, 'Famille')")
            execSQL("INSERT INTO group_member (groupId, personId) VALUES (10, 1)")
            execSQL("INSERT INTO group_member (groupId, personId) VALUES (10, 2)")
        }

        val db = openCurrent()

        val people = db.personDao().observeAll().first().sortedBy { it.id }
        assertEquals(
            listOf(Person(1, "Alice", "+33611111111"), Person(2, "Bob", "+33622222222")),
            people,
        )
        assertEquals(listOf(10L), db.groupDao().groupIdsOf(1))
        assertEquals(listOf(10L), db.groupDao().groupIdsOf(2))
        assertEquals(2, db.groupDao().observeSummaries().first().single().memberCount)
    }

    @Test
    fun `depuis la v1, plusieurs personnes peuvent n'avoir aucun téléphone`(): Unit = runBlocking {
        createVersion1 {
            execSQL("INSERT INTO person (id, name, phoneE164) VALUES (1, 'Alice', '+33611111111')")
        }

        val db = openCurrent()
        val leoId = db.personDao().insert(Person(name = "Léo", phoneE164 = null))
        db.personDao().insert(Person(name = "Zoé", phoneE164 = null))

        assertNull(db.personDao().get(leoId)?.phoneE164)
        assertEquals(3, db.personDao().observeAll().first().size)
        // Les nouveaux identifiants ne réutilisent pas ceux des personnes existantes.
        assertEquals(2L, leoId)
    }

    @Test
    fun `depuis la v1, un numéro reste unique`(): Unit = runBlocking {
        createVersion1 {
            execSQL("INSERT INTO person (id, name, phoneE164) VALUES (1, 'Alice', '+33611111111')")
        }

        val db = openCurrent()
        try {
            db.personDao().insert(Person(name = "Copie", phoneE164 = "+33611111111"))
            fail("Un deuxième numéro identique aurait dû être refusé.")
        } catch (_: SQLiteConstraintException) {
            // Attendu.
        }
    }

    @Test
    fun `depuis la v1, supprimer une personne la retire de ses groupes`(): Unit = runBlocking {
        createVersion1 {
            execSQL("INSERT INTO person (id, name, phoneE164) VALUES (1, 'Alice', '+33611111111')")
            execSQL("INSERT INTO santa_group (id, name) VALUES (10, 'Famille')")
            execSQL("INSERT INTO group_member (groupId, personId) VALUES (10, 1)")
        }

        val db = openCurrent()
        db.personDao().delete(1)

        assertEquals(0, db.groupDao().observeSummaries().first().single().memberCount)
    }
}
