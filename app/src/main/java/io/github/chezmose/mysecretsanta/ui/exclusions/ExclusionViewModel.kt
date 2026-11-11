package io.github.chezmose.mysecretsanta.ui.exclusions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.chezmose.mysecretsanta.SantaApp
import io.github.chezmose.mysecretsanta.data.ExclusionMember
import io.github.chezmose.mysecretsanta.data.ExclusionRole
import io.github.chezmose.mysecretsanta.data.ExclusionRule
import io.github.chezmose.mysecretsanta.data.ExclusionType
import io.github.chezmose.mysecretsanta.data.GroupDao
import io.github.chezmose.mysecretsanta.data.Person
import io.github.chezmose.mysecretsanta.util.sortedByName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Création ou modification d'une exclusion d'un groupe.
 * @param ruleId `null` pour une nouvelle exclusion.
 */
class ExclusionViewModel(
    private val groupId: Long,
    private val ruleId: Long?,
    private val groupDao: GroupDao,
) : ViewModel() {

    val isNew = ruleId == null

    var type by mutableStateOf(ExclusionType.COUPLE)
        private set

    /** Personnes cochées, pour un couple ou un foyer. */
    var selected by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** Sens unique : la personne qui n'offre pas. */
    var giver by mutableStateOf<Long?>(null)
        private set

    /** Sens unique : la personne à qui [giver] n'offre pas. */
    var receiver by mutableStateOf<Long?>(null)
        private set

    var label by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    /** Les membres du groupe, seuls concernés par ses exclusions. */
    val members: StateFlow<List<Person>> =
        groupDao.observeMembers(groupId)
            .map { list -> list.sortedByName { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (ruleId != null) {
            viewModelScope.launch {
                val exclusion = groupDao.getExclusion(ruleId) ?: return@launch
                type = exclusion.rule.type
                label = exclusion.rule.label.orEmpty()
                if (type == ExclusionType.ONE_WAY) {
                    giver = exclusion.members.firstOrNull { it.role == ExclusionRole.GIVER }?.personId
                    receiver = exclusion.members.firstOrNull { it.role == ExclusionRole.RECEIVER }?.personId
                } else {
                    selected = exclusion.members.map { it.personId }.toSet()
                }
            }
        }
    }

    fun onTypeChange(value: ExclusionType) {
        type = value
        error = null
        if (value == ExclusionType.COUPLE && selected.size > 2) selected = emptySet()
    }

    fun toggle(personId: Long) {
        selected = when {
            personId in selected -> selected - personId
            type == ExclusionType.COUPLE && selected.size >= 2 -> selected
            else -> selected + personId
        }
        error = null
    }

    fun onGiverChange(personId: Long) {
        giver = personId
        if (receiver == personId) receiver = null
        error = null
    }

    fun onReceiverChange(personId: Long) {
        receiver = personId
        error = null
    }

    fun onLabelChange(value: String) {
        label = value
    }

    fun save(onSaved: () -> Unit) {
        if (saving) return
        val exclusionMembers: List<ExclusionMember> = when (type) {
            ExclusionType.COUPLE -> {
                if (selected.size != 2) {
                    error = "Choisis les 2 personnes du couple."
                    return
                }
                selected.map { ExclusionMember(ruleId = 0, personId = it, role = ExclusionRole.MEMBER) }
            }
            ExclusionType.HOUSEHOLD -> {
                if (selected.size < 2) {
                    error = "Choisis au moins 2 personnes du foyer."
                    return
                }
                selected.map { ExclusionMember(ruleId = 0, personId = it, role = ExclusionRole.MEMBER) }
            }
            ExclusionType.ONE_WAY -> {
                val from = giver
                val to = receiver
                if (from == null || to == null || from == to) {
                    error = "Choisis qui n'offre pas, et à qui."
                    return
                }
                listOf(
                    ExclusionMember(ruleId = 0, personId = from, role = ExclusionRole.GIVER),
                    ExclusionMember(ruleId = 0, personId = to, role = ExclusionRole.RECEIVER),
                )
            }
        }

        saving = true
        viewModelScope.launch {
            groupDao.saveExclusion(
                ExclusionRule(
                    id = ruleId ?: 0,
                    groupId = groupId,
                    type = type,
                    label = label.trim().ifEmpty { null },
                ),
                exclusionMembers,
            )
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = ruleId ?: return
        viewModelScope.launch {
            groupDao.deleteExclusion(id)
            onDeleted()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SantaApp
                val args = createSavedStateHandle()
                ExclusionViewModel(
                    groupId = checkNotNull(args.get<Long>("groupId")),
                    ruleId = args.get<Long>("ruleId")?.takeIf { it >= 0 },
                    groupDao = app.database.groupDao(),
                )
            }
        }
    }
}
