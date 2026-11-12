package io.github.chezmose.mysecretsanta.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base locale de l'app.
 *
 * Toute modification du schéma (entité, colonne, index) impose d'augmenter `version`
 * et d'ajouter une `Migration` à [AppDatabase.MIGRATIONS], couverte par `MigrationTest` :
 * les données de l'utilisateur ne doivent jamais être perdues lors d'une mise à jour de l'app.
 */
@Database(
    entities = [
        Person::class,
        SantaGroup::class,
        GroupMember::class,
        ExclusionRule::class,
        ExclusionMember::class,
        Draw::class,
        Participant::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun groupDao(): GroupDao
    abstract fun drawDao(): DrawDao

    companion object {
        const val NAME = "mysecretsanta.db"

        /** v1 → v2 : le numéro de téléphone devient facultatif (personnes sans téléphone). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite ne sait pas rendre une colonne facultative : on recrée la table.
                // Les clés étrangères ne sont pas actives pendant une migration Room,
                // donc supprimer l'ancienne table ne vide pas group_member.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `person_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`phoneE164` TEXT)",
                )
                db.execSQL(
                    "INSERT INTO `person_new` (`id`, `name`, `phoneE164`) " +
                        "SELECT `id`, `name`, `phoneE164` FROM `person`",
                )
                db.execSQL("DROP TABLE `person`")
                db.execSQL("ALTER TABLE `person_new` RENAME TO `person`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_person_phoneE164` ON `person` (`phoneE164`)",
                )
            }
        }

        /** v2 → v3 : exclusions des groupes. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exclusion_rule` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`groupId` INTEGER NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`label` TEXT, " +
                        "FOREIGN KEY(`groupId`) REFERENCES `santa_group`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_exclusion_rule_groupId` ON `exclusion_rule` (`groupId`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exclusion_member` (" +
                        "`ruleId` INTEGER NOT NULL, " +
                        "`personId` INTEGER NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "PRIMARY KEY(`ruleId`, `personId`), " +
                        "FOREIGN KEY(`ruleId`) REFERENCES `exclusion_rule`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`personId`) REFERENCES `person`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_exclusion_member_personId` ON `exclusion_member` (`personId`)",
                )
            }
        }

        /** v3 → v4 : tirages et participants. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `draw` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`groupId` INTEGER, " +
                        "`name` TEXT NOT NULL, " +
                        "`dateEpochDay` INTEGER, " +
                        "`budget` TEXT, " +
                        "`messageTemplate` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`allowReciprocal` INTEGER NOT NULL, " +
                        "`revealed` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`groupId`) REFERENCES `santa_group`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE SET NULL )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_draw_groupId` ON `draw` (`groupId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `participant` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`drawId` INTEGER NOT NULL, " +
                        "`personId` INTEGER, " +
                        "`name` TEXT NOT NULL, " +
                        "`phoneE164` TEXT, " +
                        "FOREIGN KEY(`drawId`) REFERENCES `draw`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`personId`) REFERENCES `person`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE SET NULL )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_participant_drawId` ON `participant` (`drawId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_participant_personId` ON `participant` (`personId`)")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
