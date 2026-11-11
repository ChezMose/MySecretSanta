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
    entities = [Person::class, SantaGroup::class, GroupMember::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun groupDao(): GroupDao

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

        val MIGRATIONS = arrayOf(MIGRATION_1_2)

        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
