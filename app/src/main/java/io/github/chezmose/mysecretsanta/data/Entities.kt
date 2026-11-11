package io.github.chezmose.mysecretsanta.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Une entrée du carnet. Le numéro, au format international, est unique.
 * `phoneE164` est `null` pour une personne sans téléphone (SPEC.md §5.9).
 */
@Entity(
    tableName = "person",
    indices = [Index(value = ["phoneE164"], unique = true)],
)
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneE164: String?,
)

/** Un groupe de personnes réutilisable d'un tirage à l'autre. */
@Entity(tableName = "santa_group")
data class SantaGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** Appartenance d'une personne à un groupe. Supprimer l'un ou l'autre supprime le lien. */
@Entity(
    tableName = "group_member",
    primaryKeys = ["groupId", "personId"],
    foreignKeys = [
        ForeignKey(
            entity = SantaGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["personId"])],
)
data class GroupMember(
    val groupId: Long,
    val personId: Long,
)

/** Un groupe avec son nombre de membres, pour la liste des groupes. */
data class GroupSummary(
    val id: Long,
    val name: String,
    val memberCount: Int,
)
