package io.github.chezmose.mysecretsanta.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base locale de l'app.
 *
 * Toute modification du schéma (entité, colonne, index) impose d'augmenter `version`
 * et d'ajouter une `Migration` : les données de l'utilisateur ne doivent jamais être perdues
 * lors d'une mise à jour de l'app.
 */
@Database(
    entities = [Person::class, SantaGroup::class, GroupMember::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun groupDao(): GroupDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mysecretsanta.db").build()
    }
}
