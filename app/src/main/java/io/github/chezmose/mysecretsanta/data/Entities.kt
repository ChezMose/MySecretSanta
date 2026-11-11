package io.github.chezmose.mysecretsanta.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

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

/** Type d'exclusion (SPEC.md §5.3). Stocké en texte : ne jamais renommer une valeur. */
enum class ExclusionType {
    /** A et B ne se tirent pas l'un l'autre. */
    COUPLE,

    /** A n'offre pas à B ; B peut offrir à A. */
    ONE_WAY,

    /** Aucun membre du foyer n'offre à un autre membre du foyer. */
    HOUSEHOLD,
}

/** Rôle d'une personne dans une exclusion. Stocké en texte : ne jamais renommer une valeur. */
enum class ExclusionRole {
    /** Sens unique : la personne qui n'offre pas. */
    GIVER,

    /** Sens unique : la personne qui ne reçoit pas de [GIVER]. */
    RECEIVER,

    /** Couple ou foyer. */
    MEMBER,
}

/** Une exclusion d'un groupe. Supprimer le groupe supprime ses exclusions. */
@Entity(
    tableName = "exclusion_rule",
    foreignKeys = [
        ForeignKey(
            entity = SantaGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["groupId"])],
)
data class ExclusionRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val type: ExclusionType,
    val label: String?,
)

/** Une personne concernée par une exclusion. */
@Entity(
    tableName = "exclusion_member",
    primaryKeys = ["ruleId", "personId"],
    foreignKeys = [
        ForeignKey(
            entity = ExclusionRule::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
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
data class ExclusionMember(
    val ruleId: Long,
    val personId: Long,
    val role: ExclusionRole,
)

/** Une exclusion avec les personnes qu'elle concerne. */
data class ExclusionWithMembers(
    @Embedded val rule: ExclusionRule,
    @Relation(parentColumn = "id", entityColumn = "ruleId")
    val members: List<ExclusionMember>,
)

/** Un groupe avec son nombre de membres, pour la liste des groupes. */
data class GroupSummary(
    val id: Long,
    val name: String,
    val memberCount: Int,
)
