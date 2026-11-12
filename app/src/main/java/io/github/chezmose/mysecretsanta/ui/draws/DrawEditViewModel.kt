package io.github.chezmose.mysecretsanta.ui.draws

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.chezmose.mysecretsanta.SantaApp
import io.github.chezmose.mysecretsanta.data.AppDatabase
import io.github.chezmose.mysecretsanta.data.Draw
import io.github.chezmose.mysecretsanta.data.ExclusionWithMembers
import io.github.chezmose.mysecretsanta.data.Participant
import io.github.chezmose.mysecretsanta.data.Person
import io.github.chezmose.mysecretsanta.data.SantaGroup
import io.github.chezmose.mysecretsanta.draw.Feasibility
import io.github.chezmose.mysecretsanta.draw.assessFeasibility
import io.github.chezmose.mysecretsanta.message.MessageTemplate
import io.github.chezmose.mysecretsanta.util.AppSettings
import io.github.chezmose.mysecretsanta.util.sortedByName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Création ou modification d'un tirage en brouillon (SPEC.md §5.4, écran 7).
 * @param drawId `null` pour un nouveau tirage.
 * @param presetGroupId groupe imposé, quand le tirage est créé depuis la fiche d'un groupe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DrawEditViewModel(
    private val drawId: Long?,
    presetGroupId: Long?,
    private val db: AppDatabase,
    settings: AppSettings,
) : ViewModel() {

    val isNew = drawId == null

    /** Le groupe ne se choisit qu'à la création d'un tirage depuis l'onglet Tirages. */
    val canChooseGroup = isNew && presetGroupId == null

    val defaultTemplate: String = settings.defaultMessageTemplate

    var groupId by mutableStateOf(presetGroupId)
        private set
    var name by mutableStateOf("Noël ${LocalDate.now().year}")
        private set
    var date by mutableStateOf<LocalDate?>(null)
        private set
    var budget by mutableStateOf("")
        private set
    var template by mutableStateOf(defaultTemplate)
        private set

    /** Personnes cochées comme participants. */
    var selected by mutableStateOf<Set<Long>>(emptySet())
        private set

    var nameError by mutableStateOf<String?>(null)
        private set
    var groupError by mutableStateOf<String?>(null)
        private set
    var templateError by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    private var loadedDraw: Draw? = null

    /** Participants enregistrés d'un brouillon, appliqués quand les membres du groupe sont connus. */
    private var savedSelection: Set<Long>? = null

    val groups: StateFlow<List<SantaGroup>> =
        db.groupDao().observeAll()
            .map { list -> list.sortedByName { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val groupIdFlow: Flow<Long?> = snapshotFlow { groupId }

    /** Membres du groupe choisi ; `null` tant qu'ils ne sont pas chargés. */
    val members: StateFlow<List<Person>?> =
        groupIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else db.groupDao().observeMembers(id).map { list -> list.sortedByName { it.name } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val exclusions: Flow<List<ExclusionWithMembers>> =
        groupIdFlow.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else db.groupDao().observeExclusions(id) }

    /** Faisabilité du tirage avec les participants cochés ; `null` pendant le calcul. */
    val feasibility: StateFlow<Feasibility?> =
        combine(members.filterNotNull(), exclusions, snapshotFlow { selected }) { m, e, s ->
            assessFeasibility(m.filter { it.id in s }, e)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            if (drawId != null) {
                val draw = db.drawDao().get(drawId) ?: return@launch
                loadedDraw = draw
                name = draw.name
                date = draw.dateEpochDay?.let(LocalDate::ofEpochDay)
                budget = draw.budget.orEmpty()
                template = draw.messageTemplate
                savedSelection = db.drawDao().participants(drawId).mapNotNull { it.personId }.toSet()
                groupId = draw.groupId
            }
            // À chaque choix de groupe : tous ses membres cochés (ou la sélection enregistrée du brouillon).
            groupIdFlow.flatMapLatest { id ->
                if (id == null) emptyFlow() else db.groupDao().observeMembers(id).take(1)
            }.collect { list ->
                val ids = list.map { it.id }.toSet()
                selected = savedSelection?.let { it intersect ids } ?: ids
                savedSelection = null
            }
        }
    }

    fun selectGroup(id: Long) {
        groupId = id
        groupError = null
    }

    fun onNameChange(value: String) {
        name = value
        nameError = null
    }

    fun onDateChange(value: LocalDate?) {
        date = value
    }

    fun onBudgetChange(value: String) {
        budget = value
    }

    fun onTemplateChange(value: String) {
        template = value
        templateError = null
    }

    fun toggle(personId: Long) {
        selected = if (personId in selected) selected - personId else selected + personId
    }

    fun save(onSaved: (Long) -> Unit) {
        if (saving) return
        nameError = if (name.isBlank()) "Le nom du tirage est obligatoire." else null
        groupError = if (groupId == null) "Choisis un groupe." else null
        templateError = MessageTemplate.validate(template)
        val currentGroup = groupId
        val currentMembers = members.value
        if (nameError != null || groupError != null || templateError != null) return
        if (currentGroup == null || currentMembers == null) return

        val participants = currentMembers.filter { it.id in selected }.map {
            Participant(drawId = 0, personId = it.id, name = it.name, phoneE164 = it.phoneE164)
        }
        val draw = (loadedDraw ?: Draw(groupId = currentGroup, name = "", dateEpochDay = null, budget = null, messageTemplate = "", createdAt = System.currentTimeMillis()))
            .copy(
                groupId = currentGroup,
                name = name.trim(),
                dateEpochDay = date?.toEpochDay(),
                budget = budget.trim().ifEmpty { null },
                messageTemplate = template,
            )

        saving = true
        viewModelScope.launch {
            onSaved(db.drawDao().saveDraft(draw, participants))
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = drawId ?: return
        viewModelScope.launch {
            db.drawDao().delete(id)
            onDeleted()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SantaApp
                val args = createSavedStateHandle()
                DrawEditViewModel(
                    drawId = args.get<Long>("drawId")?.takeIf { it >= 0 },
                    presetGroupId = args.get<Long>("groupId")?.takeIf { it >= 0 },
                    db = app.database,
                    settings = app.settings,
                )
            }
        }
    }
}
