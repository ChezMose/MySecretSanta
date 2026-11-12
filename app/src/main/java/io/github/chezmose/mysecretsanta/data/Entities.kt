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

/** État d'un tirage. Stocké en texte : ne jamais renommer une valeur. */
enum class DrawStatus {
    /** En préparation : participants, message… modifiables. */
    DRAFT,

    /** Tiré au sort, résultats pas encore tous transmis. */
    DRAWN,

    /** Tous les résultats ont été transmis. */
    SENT,
}

/**
 * Un tirage (SPEC.md §5.4). Supprimer son groupe ne le supprime pas (historique) :
 * `groupId` devient `null`. Les brouillons, eux, sont supprimés avec le groupe.
 */
@Entity(
    tableName = "draw",
    foreignKeys = [
        ForeignKey(
            entity = SantaGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["groupId"])],
)
data class Draw(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long?,
    val name: String,
    /**
     * Inutilisée : la date de l'échange a été retirée (SPEC.md §5.4). Toujours `null` pour les
     * nouveaux tirages ; la colonne reste pour éviter une migration.
     */
    val dateEpochDay: Long?,
    val budget: String?,
    val messageTemplate: String,
    val status: DrawStatus = DrawStatus.DRAFT,
    val allowReciprocal: Boolean = false,
    val revealed: Boolean = false,
    /** Création, en millisecondes depuis 1970. */
    val createdAt: Long,
)

/**
 * Un participant d'un tirage, avec une copie de son nom et de son numéro.
 * Dans un brouillon, c'est la sélection en cours ; au tirage, la copie est figée.
 * Supprimer la personne ne supprime pas le participant d'un tirage fait : `personId` devient `null`.
 */
@Entity(
    tableName = "participant",
    foreignKeys = [
        ForeignKey(
            entity = Draw::class,
            parentColumns = ["id"],
            childColumns = ["drawId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["drawId"]), Index(value = ["personId"])],
)
data class Participant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drawId: Long,
    val personId: Long?,
    val name: String,
    val phoneE164: String?,
    /** Après le tirage : le participant à qui celui-ci offre un cadeau. Secret : jamais affiché sans demande. */
    val receiverParticipantId: Long? = null,
    /** Après le tirage : où en est la transmission du résultat. `null` dans un brouillon. */
    val status: DeliveryStatus? = null,
    /** Motif de l'échec, si [status] vaut [DeliveryStatus.FAILED]. */
    val statusError: String? = null,
    /** Dernier changement de [status], en millisecondes depuis 1970. */
    val statusAt: Long? = null,
)

/** Transmission du résultat à un participant (SPEC.md §5.6, §5.9). Stocké en texte : ne jamais renommer. */
enum class DeliveryStatus {
    /** SMS à envoyer. */
    PENDING,

    /** SMS confié au téléphone, en attente du compte rendu d'envoi. */
    SENDING,

    /** SMS parti. */
    SENT,

    /** SMS reçu par le destinataire (accusé de réception de l'opérateur). */
    DELIVERED,

    /** Envoi échoué : voir [Participant.statusError]. */
    FAILED,

    /** Pas de téléphone : résultat à montrer sur le téléphone de l'organisateur. */
    TO_SHOW,

    /** Pas de téléphone : résultat montré. */
    SHOWN,
    ;

    /** Le résultat est transmis : rien à refaire pour ce participant. */
    val isDone: Boolean get() = this == SENT || this == DELIVERED || this == SHOWN
}

/** Un tirage pour la liste des tirages. */
data class DrawSummary(
    val id: Long,
    val name: String,
    val dateEpochDay: Long?,
    val status: DrawStatus,
    val revealed: Boolean,
    val groupName: String?,
    val participantCount: Int,
)

/** Un groupe avec son nombre de membres, pour la liste des groupes. */
data class GroupSummary(
    val id: Long,
    val name: String,
    val memberCount: Int,
)
