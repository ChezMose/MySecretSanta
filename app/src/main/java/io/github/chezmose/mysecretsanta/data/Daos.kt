package io.github.chezmose.mysecretsanta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Les listes ne sont pas triées en SQL : le tri alphabétique français se fait en Kotlin (sortedByName).

@Dao
interface PersonDao {
    @Query("SELECT * FROM person")
    fun observeAll(): Flow<List<Person>>

    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun get(id: Long): Person?

    @Query("SELECT * FROM person WHERE phoneE164 = :phoneE164 LIMIT 1")
    suspend fun findByPhone(phoneE164: String): Person?

    @Insert
    suspend fun insert(person: Person): Long

    @Update
    suspend fun update(person: Person)

    @Query("DELETE FROM person WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
abstract class GroupDao {
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

    @Query(
        "SELECT p.* FROM person p INNER JOIN group_member m ON m.personId = p.id " +
            "WHERE m.groupId = :groupId",
    )
    abstract fun observeMembers(groupId: Long): Flow<List<Person>>

    @Query("SELECT groupId FROM group_member WHERE personId = :personId")
    abstract suspend fun groupIdsOf(personId: Long): List<Long>

    @Insert
    abstract suspend fun insert(group: SantaGroup): Long

    @Query("UPDATE santa_group SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM santa_group WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addMembers(members: List<GroupMember>)

    @Query("DELETE FROM group_member WHERE groupId = :groupId AND personId = :personId")
    abstract suspend fun removeMember(groupId: Long, personId: Long)

    @Query("DELETE FROM group_member WHERE personId = :personId")
    abstract suspend fun clearMemberships(personId: Long)

    @Query(
        "INSERT INTO group_member (groupId, personId) " +
            "SELECT :targetId, personId FROM group_member WHERE groupId = :sourceId",
    )
    abstract suspend fun copyMembers(sourceId: Long, targetId: Long)

    /** Crée une copie du groupe avec les mêmes membres, et renvoie son identifiant. */
    @Transaction
    open suspend fun duplicate(sourceId: Long, newName: String): Long {
        val newId = insert(SantaGroup(name = newName))
        copyMembers(sourceId, newId)
        return newId
    }

    /** Remplace la liste des groupes dont la personne est membre. */
    @Transaction
    open suspend fun setMemberships(personId: Long, groupIds: Collection<Long>) {
        clearMemberships(personId)
        addMembers(groupIds.map { GroupMember(groupId = it, personId = personId) })
    }
}
