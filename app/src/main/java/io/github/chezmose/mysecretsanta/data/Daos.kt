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

    /** Supprime la personne, ses appartenances aux groupes et les exclusions devenues incomplètes. */
    @Transaction
    open suspend fun delete(id: Long) {
        deletePersonRow(id)
        deleteIncompleteExclusions()
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

    /** Supprime le groupe, ses appartenances et ses exclusions (clés étrangères en cascade). */
    @Query("DELETE FROM santa_group WHERE id = :id")
    abstract suspend fun delete(id: Long)

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
        cleanUpExclusions()
    }

    /** Remplace la liste des groupes dont la personne est membre. */
    @Transaction
    open suspend fun setMemberships(personId: Long, groupIds: Collection<Long>) {
        clearMemberships(personId)
        addMembers(groupIds.map { GroupMember(groupId = it, personId = personId) })
        cleanUpExclusions()
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

    /**
     * Garde les exclusions cohérentes avec les groupes : une personne qui n'est plus membre du groupe
     * en est retirée, et une exclusion qui ne concerne plus au moins 2 personnes est supprimée.
     */
    @Transaction
    open suspend fun cleanUpExclusions() {
        deleteExclusionMembersOutsideGroup()
        deleteIncompleteExclusions()
    }
}
