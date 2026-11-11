package io.github.chezmose.mysecretsanta.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.chezmose.mysecretsanta.SantaApp
import io.github.chezmose.mysecretsanta.data.ExclusionWithMembers
import io.github.chezmose.mysecretsanta.data.GroupDao
import io.github.chezmose.mysecretsanta.data.GroupMember
import io.github.chezmose.mysecretsanta.data.Person
import io.github.chezmose.mysecretsanta.data.PersonDao
import io.github.chezmose.mysecretsanta.data.SantaGroup
import io.github.chezmose.mysecretsanta.draw.Feasibility
import io.github.chezmose.mysecretsanta.draw.assessFeasibility
import io.github.chezmose.mysecretsanta.util.sortedByName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupViewModel(
    val groupId: Long,
    private val groupDao: GroupDao,
    personDao: PersonDao,
) : ViewModel() {

    val group: StateFlow<SantaGroup?> =
        groupDao.observe(groupId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val members: StateFlow<List<Person>> =
        groupDao.observeMembers(groupId)
            .map { list -> list.sortedByName { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exclusions: StateFlow<List<ExclusionWithMembers>> =
        groupDao.observeExclusions(groupId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** `null` pendant le calcul. */
    val feasibility: StateFlow<Feasibility?> =
        combine(members, exclusions) { m, e -> assessFeasibility(m, e) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Personnes du carnet qui ne sont pas encore dans le groupe. */
    val candidates: StateFlow<List<Person>> =
        combine(personDao.observeAll(), members) { all, current ->
            val memberIds = current.map { it.id }.toSet()
            all.filter { it.id !in memberIds }.sortedByName { it.name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(name: String) {
        viewModelScope.launch { groupDao.rename(groupId, name.trim()) }
    }

    fun addMembers(personIds: Collection<Long>) {
        viewModelScope.launch {
            groupDao.addMembers(personIds.map { GroupMember(groupId = groupId, personId = it) })
        }
    }

    fun removeMember(personId: Long) {
        viewModelScope.launch { groupDao.removeMember(groupId, personId) }
    }

    fun duplicate(onCreated: (Long) -> Unit) {
        val current = group.value ?: return
        viewModelScope.launch {
            onCreated(groupDao.duplicate(groupId, "${current.name} (copie)"))
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            groupDao.delete(groupId)
            onDeleted()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SantaApp
                val groupId = checkNotNull(createSavedStateHandle().get<Long>("groupId"))
                GroupViewModel(groupId, app.database.groupDao(), app.database.personDao())
            }
        }
    }
}
