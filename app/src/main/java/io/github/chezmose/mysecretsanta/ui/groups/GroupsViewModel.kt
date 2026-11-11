package io.github.chezmose.mysecretsanta.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.chezmose.mysecretsanta.SantaApp
import io.github.chezmose.mysecretsanta.data.GroupDao
import io.github.chezmose.mysecretsanta.data.GroupSummary
import io.github.chezmose.mysecretsanta.data.SantaGroup
import io.github.chezmose.mysecretsanta.util.sortedByName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupsViewModel(private val groupDao: GroupDao) : ViewModel() {

    /** `null` tant que la base n'a pas répondu. */
    val groups: StateFlow<List<GroupSummary>?> =
        groupDao.observeSummaries()
            .map { list -> list.sortedByName { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            onCreated(groupDao.insert(SantaGroup(name = name.trim())))
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SantaApp
                GroupsViewModel(app.database.groupDao())
            }
        }
    }
}
