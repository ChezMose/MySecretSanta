package io.github.chezmose.mysecretsanta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Les listes ne sont pas triées en SQL : le tri alphabétique français se fait en Kotlin (sortedByName).

/** Supprime les exclusions qui ne concernent plus au moins 2 personnes. */
private const val DELETE_INCOMPLETE_EXCLUSIONS =
    "DELETE FROM exclusion_rule WHERE " +
        "(SELECT COUNT(*) FROM exclusion_member em WHERE em.ruleId = exclusion_rule.id) < 2"

/** Retire des exclusions les personnes qui ne sont plus membres du groupe de l'exclusion. */
private const val DELETE_EXCLUSION_MEMBERS_OUTSIDE_GROUP =
    "DELETE FROM exclusion_member WHERE NOT EXISTS (" +
        "SELECT 1 FROM exclusion_rule r INNER JOIN group_member m ON m.groupId = r.groupId " +
        "WHERE r.id = exclusion_member.ruleId AND m.personId = exclusion_member.personId)"

/**
 * Retire des tirages en brouillon les participants qui ne sont plus membres du groupe du tirage
 * (ou dont la personne a été supprimée). Les tirages faits gardent leurs participants figés.
 */
private const val DELETE_DRAFT_PARTICIPANTS_OUTSIDE_GROUP =
    "DELETE FROM participant WHERE drawId IN (SELECT id FROM draw WHERE status = 'DRAFT') " +
        "AND NOT EXISTS (SELECT 1 FROM draw d INNER JOIN group_member m ON m.groupId = d.groupId " +
        "WHERE d.id = participant.drawId AND m.personId = participant.personId)"

@Dao
abstract class PersonDao {
    @Query("SELECT * FROM person")
    abstract fun observeAll(): Flow<List<Person>>

    @Query("SELECT * FROM person WHERE id = :id")
    abstract suspend fun get(id: Long): Person?

    @Query("SELECT * FROM person WHERE phoneE164 = :phoneE164 LIMIT 1")
    abstract suspend fun findByPhone(phoneE164: String): Person?

    @Insert
    abstract suspend fun insert(person: Person): Long

    @Update
    abstract suspend fun update(person: Person)

    @Query("DELETE FROM person WHERE id = :id")
    abstract suspend fun deletePersonRow(id: Long)

    @Query(DELETE_INCOMPLETE_EXCLUSIONS)
    abstract suspend fun deleteIncompleteExclusions()

    @Query(DELETE_DRAFT_PARTICIPANTS_OUTSIDE_GROUP)
    abstract suspend fun deleteDraftParticipantsOutsideGroup()

    /**
     * Supprime la personne, ses appartenances aux groupes, les exclusions devenues incomplètes
     * et sa place dans les brouillons. Les tirages faits gardent son nom.
     */
    @Transaction
    open suspend fun delete(id: Long) {
        deletePersonRow(id)
        deleteIncompleteExclusions()
        deleteDraftParticipantsOutsideGroup()
    }
}

@Dao
abstract class GroupDao {

    // --- Groupes ---

    @Query(
        "SELECT g.id AS id, g.name AS name, COUNT(m.personId) AS memberCount " +
            "FROM santa_group g LEFT JOIN group_member m ON m.groupId = g.id " +
            "GROUP BY g.id",
    )
    abstract fun observeSummaries(): Flow<List<GroupSummary>>

    @Query("SELECT * FROM santa_group")
    abstract fun observeAll(): Flow<List<SantaGroup>>

    @Query("SELECT * FROM santa_group WHERE id = :id")
    abstract fun observe(id: Long): Flow<SantaGroup?>

    @Insert
    abstract suspend fun insert(group: SantaGroup): Long

    @Query("UPDATE santa_group SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM santa_group WHERE id = :id")
    abstract suspend fun deleteGroupRow(id: Long)

    @Query("DELETE FROM draw WHERE groupId = :groupId AND status = 'DRAFT'")
    abstract suspend fun deleteDraftDraws(groupId: Long)

    /**
     * Supprime le groupe, ses appartenances, ses exclusions et ses brouillons.
     * Ses tirages faits restent dans l'historique, sans groupe.
     */
    @Transaction
    open suspend fun delete(id: Long) {
        deleteDraftDraws(id)
        deleteGroupRow(id)
    }

    /** Crée une copie du groupe avec les mêmes membres et exclusions, et renvoie son identifiant. */
    @Transaction
    open suspend fun duplicate(sourceId: Long, newName: String): Long {
        val newId = insert(SantaGroup(name = newName))
        copyMembers(sourceId, newId)
        for (exclusion in getExclusions(sourceId)) {
            val ruleId = insertExclusionRule(exclusion.rule.copy(id = 0, groupId = newId))
            insertExclusionMembers(exclusion.members.map { it.copy(ruleId = ruleId) })
        }
        return newId
    }

    // --- Membres ---

    @Query(
        "SELECT p.* FROM person p INNER JOIN group_member m ON m.personId = p.id " +
            "WHERE m.groupId = :groupId",
    )
    abstract fun observeMembers(groupId: Long): Flow<List<Person>>

    @Query(
        "SELECT p.* FROM person p INNER JOIN group_member m ON m.personId = p.id " +
            "WHERE m.groupId = :groupId",
    )
    abstract suspend fun getMembers(groupId: Long): List<Person>

    @Query("SELECT groupId FROM group_member WHERE personId = :personId")
    abstract suspend fun groupIdsOf(personId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addMembers(members: List<GroupMember>)

    @Query("DELETE FROM group_member WHERE groupId = :groupId AND personId = :personId")
    abstract suspend fun deleteMembership(groupId: Long, personId: Long)

    @Query("DELETE FROM group_member WHERE personId = :personId")
    abstract suspend fun clearMemberships(personId: Long)

    @Query(
        "INSERT INTO group_member (groupId, personId) " +
            "SELECT :targetId, personId FROM group_member WHERE groupId = :sourceId",
    )
    abstract suspend fun copyMembers(sourceId: Long, targetId: Long)

    /** Retire la personne du groupe, et de ses exclusions dans ce groupe. */
    @Transaction
    open suspend fun removeMember(groupId: Long, personId: Long) {
        deleteMembership(groupId, personId)
        cleanUpAfterMembershipChange()
    }

    /** Remplace la liste des groupes dont la personne est membre. */
    @Transaction
    open suspend fun setMemberships(personId: Long, groupIds: Collection<Long>) {
        clearMemberships(personId)
        addMembers(groupIds.map { GroupMember(groupId = it, personId = personId) })
        cleanUpAfterMembershipChange()
    }

    // --- Exclusions ---

    @Transaction
    @Query("SELECT * FROM exclusion_rule WHERE groupId = :groupId")
    abstract fun observeExclusions(groupId: Long): Flow<List<ExclusionWithMembers>>

    @Transaction
    @Query("SELECT * FROM exclusion_rule WHERE groupId = :groupId")
    abstract suspend fun getExclusions(groupId: Long): List<ExclusionWithMembers>

    @Transaction
    @Query("SELECT * FROM exclusion_rule WHERE id = :ruleId")
    abstract suspend fun getExclusion(ruleId: Long): ExclusionWithMembers?

    @Insert
    abstract suspend fun insertExclusionRule(rule: ExclusionRule): Long

    @Update
    abstract suspend fun updateExclusionRule(rule: ExclusionRule)

    @Insert
    abstract suspend fun insertExclusionMembers(members: List<ExclusionMember>)

    @Query("DELETE FROM exclusion_member WHERE ruleId = :ruleId")
    abstract suspend fun clearExclusionMembers(ruleId: Long)

    @Query("DELETE FROM exclusion_rule WHERE id = :ruleId")
    abstract suspend fun deleteExclusion(ruleId: Long)

    /** Crée ou modifie une exclusion et remplace ses personnes. Renvoie son identifiant. */
    @Transaction
    open suspend fun saveExclusion(rule: ExclusionRule, members: List<ExclusionMember>): Long {
        val ruleId = if (rule.id == 0L) {
            insertExclusionRule(rule)
        } else {
            updateExclusionRule(rule)
            rule.id
        }
        clearExclusionMembers(ruleId)
        insertExclusionMembers(members.map { it.copy(ruleId = ruleId) })
        return ruleId
    }

    @Query(DELETE_EXCLUSION_MEMBERS_OUTSIDE_GROUP)
    abstract suspend fun deleteExclusionMembersOutsideGroup()

    @Query(DELETE_INCOMPLETE_EXCLUSIONS)
    abstract suspend fun deleteIncompleteExclusions()

    @Query(DELETE_DRAFT_PARTICIPANTS_OUTSIDE_GROUP)
    abstract suspend fun deleteDraftParticipantsOutsideGroup()

    /**
     * Garde les exclusions et les brouillons cohérents avec les groupes : une personne qui n'est plus
     * membre du groupe est retirée de ses exclusions et de ses brouillons, et une exclusion qui ne
     * concerne plus au moins 2 personnes est supprimée.
     */
    @Transaction
    open suspend fun cleanUpAfterMembershipChange() {
        deleteExclusionMembersOutsideGroup()
        deleteIncompleteExclusions()
        deleteDraftParticipantsOutsideGroup()
    }
}

private const val DRAW_SUMMARY_SELECT =
    "SELECT d.id AS id, d.name AS name, d.dateEpochDay AS dateEpochDay, d.status AS status, " +
        "d.revealed AS revealed, g.name AS groupName, " +
        "(SELECT COUNT(*) FROM participant p WHERE p.drawId = d.id) AS participantCount " +
        "FROM draw d LEFT JOIN santa_group g ON g.id = d.groupId "

@Dao
abstract class DrawDao {

    // --- Lecture ---

    @Query(DRAW_SUMMARY_SELECT + "ORDER BY d.createdAt DESC, d.id DESC")
    abstract fun observeSummaries(): Flow<List<DrawSummary>>

    /** Historique d'un groupe, du plus récent au plus ancien. */
    @Query(DRAW_SUMMARY_SELECT + "WHERE d.groupId = :groupId ORDER BY d.createdAt DESC, d.id DESC")
    abstract fun observeSummariesForGroup(groupId: Long): Flow<List<DrawSummary>>

    @Query("SELECT * FROM draw WHERE id = :id")
    abstract suspend fun get(id: Long): Draw?

    @Query("SELECT * FROM draw WHERE id = :id")
    abstract fun observe(id: Long): Flow<Draw?>

    @Query("SELECT * FROM participant WHERE drawId = :drawId")
    abstract suspend fun participants(drawId: Long): List<Participant>

    @Query("SELECT * FROM participant WHERE drawId = :drawId")
    abstract fun observeParticipants(drawId: Long): Flow<List<Participant>>

    @Query("SELECT * FROM participant WHERE id = :id")
    abstract suspend fun getParticipant(id: Long): Participant?

    // --- Écriture ---

    @Insert
    abstract suspend fun insertDraw(draw: Draw): Long

    @Update
    abstract suspend fun updateDraw(draw: Draw)

    @Query("DELETE FROM draw WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Insert
    abstract suspend fun insertParticipants(participants: List<Participant>): List<Long>

    @Query("DELETE FROM participant WHERE drawId = :drawId")
    abstract suspend fun clearParticipants(drawId: Long)

    @Query("UPDATE participant SET receiverParticipantId = :receiverId WHERE id = :participantId")
    abstract suspend fun setReceiver(participantId: Long, receiverId: Long)

    @Query("UPDATE participant SET status = :status, statusError = :error, statusAt = :at WHERE id = :id")
    abstract suspend fun setStatusRow(id: Long, status: DeliveryStatus, error: String?, at: Long)

    @Query("UPDATE draw SET revealed = 1 WHERE id = :drawId")
    abstract suspend fun setRevealed(drawId: Long)

    @Query("UPDATE draw SET allowReciprocal = :allow WHERE id = :drawId")
    abstract suspend fun setAllowReciprocal(drawId: Long, allow: Boolean)

    /** Le tirage passe à « envoyé » quand chaque participant a reçu ou vu son résultat. */
    @Query(
        "UPDATE draw SET status = 'SENT' WHERE id = :drawId AND status = 'DRAWN' AND NOT EXISTS (" +
            "SELECT 1 FROM participant p WHERE p.drawId = :drawId " +
            "AND (p.status IS NULL OR p.status NOT IN ('SENT', 'DELIVERED', 'SHOWN')))",
    )
    abstract suspend fun markSentIfComplete(drawId: Long)

    @Query(
        "UPDATE participant SET receiverParticipantId = NULL, status = NULL, statusError = NULL, statusAt = NULL " +
            "WHERE drawId = :drawId",
    )
    abstract suspend fun clearResults(drawId: Long)

    @Query("UPDATE draw SET status = 'DRAFT', revealed = 0 WHERE id = :drawId")
    abstract suspend fun setDraft(drawId: Long)

    @Query(DELETE_DRAFT_PARTICIPANTS_OUTSIDE_GROUP)
    abstract suspend fun deleteDraftParticipantsOutsideGroup()

    /**
     * Enregistre le résultat d'un tirage : participants figés (nom et numéro du moment),
     * receveur de chacun, état de transmission initial. Le tirage passe à « tiré ».
     * @param assignments donneur → receveur, en identifiants de personnes.
     */
    @Transaction
    open suspend fun saveResult(draw: Draw, participants: List<Person>, assignments: Map<Long, Long>) {
        updateDraw(draw.copy(status = DrawStatus.DRAWN, revealed = false))
        clearParticipants(draw.id)
        val ids = insertParticipants(
            participants.map {
                Participant(
                    drawId = draw.id,
                    personId = it.id,
                    name = it.name,
                    phoneE164 = it.phoneE164,
                    status = if (it.phoneE164 == null) DeliveryStatus.TO_SHOW else DeliveryStatus.PENDING,
                )
            },
        )
        val participantIdOf = participants.map { it.id }.zip(ids).toMap()
        for ((giver, receiver) in assignments) {
            setReceiver(participantIdOf.getValue(giver), participantIdOf.getValue(receiver))
        }
    }

    /**
     * Met à jour la transmission du résultat d'un participant, puis l'état du tirage.
     * Un compte rendu d'envoi tardif ne fait pas reculer un SMS déjà distribué.
     */
    @Transaction
    open suspend fun updateStatus(participantId: Long, status: DeliveryStatus, error: String?, at: Long) {
        val participant = getParticipant(participantId) ?: return
        if (participant.status == null) return // Brouillon (tirage refait entre-temps) : compte rendu périmé.
        if (participant.status == DeliveryStatus.DELIVERED && status == DeliveryStatus.SENT) return
        setStatusRow(participantId, status, error, at)
        markSentIfComplete(participant.drawId)
    }

    /** Remet un tirage fait en brouillon : résultats effacés, sélection des participants conservée. */
    @Transaction
    open suspend fun resetToDraft(drawId: Long) {
        clearResults(drawId)
        setDraft(drawId)
        deleteDraftParticipantsOutsideGroup()
    }

    /** Crée ou modifie un brouillon et remplace ses participants. Renvoie son identifiant. */
    @Transaction
    open suspend fun saveDraft(draw: Draw, participants: List<Participant>): Long {
        val drawId = if (draw.id == 0L) {
            insertDraw(draw)
        } else {
            updateDraw(draw)
            draw.id
        }
        clearParticipants(drawId)
        insertParticipants(participants.map { it.copy(id = 0, drawId = drawId) })
        return drawId
    }
}
