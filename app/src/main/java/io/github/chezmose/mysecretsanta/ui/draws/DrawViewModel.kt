package io.github.chezmose.mysecretsanta.ui.draws

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
import io.github.chezmose.mysecretsanta.data.AppDatabase
import io.github.chezmose.mysecretsanta.data.DeliveryStatus
import io.github.chezmose.mysecretsanta.data.Draw
import io.github.chezmose.mysecretsanta.data.DrawStatus
import io.github.chezmose.mysecretsanta.data.Participant
import io.github.chezmose.mysecretsanta.data.Person
import io.github.chezmose.mysecretsanta.draw.DrawRunner
import io.github.chezmose.mysecretsanta.draw.Feasibility
import io.github.chezmose.mysecretsanta.draw.assessFeasibility
import io.github.chezmose.mysecretsanta.message.MessageTemplate
import io.github.chezmose.mysecretsanta.sms.Sim
import io.github.chezmose.mysecretsanta.sms.SmsSender
import io.github.chezmose.mysecretsanta.util.AppSettings
import io.github.chezmose.mysecretsanta.util.sortedByName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Écran d'un tirage (SPEC.md écran 8) : tirage au sort, envoi des SMS, suivi, révélation. */
@OptIn(ExperimentalCoroutinesApi::class)
class DrawViewModel(
    val drawId: Long,
    private val db: AppDatabase,
    private val settings: AppSettings,
    private val sender: SmsSender,
) : ViewModel() {

    /** Résultat d'un tirage au sort qui n'a pas abouti, à afficher. */
    sealed interface DrawProblem {
        data class Message(val title: String, val text: String) : DrawProblem
        data object OnlyWithReciprocal : DrawProblem
    }

    val draw: StateFlow<Draw?> =
        db.drawDao().observe(drawId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Participants triés par nom. */
    val participants: StateFlow<List<Participant>> =
        db.drawDao().observeParticipants(drawId)
            .map { list -> list.sortedByName { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groupName: StateFlow<String?> =
        draw.filterNotNull()
            .flatMapLatest { d -> d.groupId?.let { db.groupDao().observe(it).map { g -> g?.name } } ?: flowOf(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Brouillon : faisabilité avec les participants cochés et les exclusions du groupe. */
    val feasibility: StateFlow<Feasibility?> =
        draw.filterNotNull()
            .flatMapLatest { d ->
                val exclusions = d.groupId?.let { db.groupDao().observeExclusions(it) } ?: flowOf(emptyList())
                combine(participants, exclusions) { list, e ->
                    if (d.status != DrawStatus.DRAFT) {
                        null
                    } else {
                        assessFeasibility(list.mapNotNull { p -> p.personId?.let { Person(it, p.name, p.phoneE164) } }, e)
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var drawing by mutableStateOf(false)
        private set
    var problem by mutableStateOf<DrawProblem?>(null)
        private set
    var sending by mutableStateOf(false)
        private set

    // --- Tirage ---

    fun performDraw() {
        if (drawing) return
        drawing = true
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.Default) { DrawRunner(db).run(drawId) }
            problem = when (outcome) {
                DrawRunner.Outcome.Done, DrawRunner.Outcome.NotADraft -> null
                DrawRunner.Outcome.GroupDeleted -> DrawProblem.Message(
                    "Groupe supprimé",
                    "Le groupe de ce tirage n'existe plus : impossible de tirer au sort.",
                )
                DrawRunner.Outcome.TooFewParticipants -> DrawProblem.Message(
                    "Pas assez de participants",
                    "Il faut au moins 3 participants. Modifie le tirage pour en ajouter.",
                )
                is DrawRunner.Outcome.InvalidMessage -> DrawProblem.Message("Message à corriger", outcome.message)
                is DrawRunner.Outcome.Impossible -> DrawProblem.Message(
                    "Tirage impossible",
                    (outcome.reasons + "Les exclusions du groupe ne laissent aucune répartition possible.").joinToString("\n"),
                )
                DrawRunner.Outcome.OnlyWithReciprocal -> DrawProblem.OnlyWithReciprocal
            }
            drawing = false
        }
    }

    /** L'organisateur accepte un échange réciproque pour ce tirage : on relance le tirage. */
    fun acceptReciprocalAndDraw() {
        problem = null
        viewModelScope.launch {
            db.drawDao().setAllowReciprocal(drawId, true)
            performDraw()
        }
    }

    fun dismissProblem() {
        problem = null
    }

    // --- Envoi des SMS ---

    fun hasSendPermission(): Boolean = sender.hasSendPermission()

    fun activeSims(): List<Sim> = sender.activeSims()

    val savedSubscriptionId: Int? get() = settings.smsSubscriptionId

    /** Participants avec téléphone dont le SMS reste à envoyer (jamais envoyé, ou en échec). */
    fun pendingSms(list: List<Participant>): List<Participant> =
        list.filter { it.phoneE164 != null && (it.status == DeliveryStatus.PENDING || it.status == DeliveryStatus.FAILED) }

    /** Envoie les SMS restants, l'un après l'autre. */
    fun sendPending(subscriptionId: Int?) {
        if (sending) return
        settings.smsSubscriptionId = subscriptionId
        sending = true
        viewModelScope.launch {
            val currentDraw = db.drawDao().get(drawId)
            if (currentDraw != null) {
                val all = db.drawDao().participants(drawId)
                for (participant in pendingSms(all)) {
                    sendOne(currentDraw, participant, all, subscriptionId)
                    delay(SEND_INTERVAL_MS)
                }
            }
            sending = false
        }
    }

    /** Renvoie le SMS d'un seul participant (échec, message perdu…). */
    fun resend(participantId: Long, subscriptionId: Int?) {
        settings.smsSubscriptionId = subscriptionId
        viewModelScope.launch {
            val currentDraw = db.drawDao().get(drawId) ?: return@launch
            val all = db.drawDao().participants(drawId)
            val participant = all.firstOrNull { it.id == participantId } ?: return@launch
            sendOne(currentDraw, participant, all, subscriptionId)
        }
    }

    private suspend fun sendOne(draw: Draw, giver: Participant, all: List<Participant>, subscriptionId: Int?) {
        val phone = giver.phoneE164 ?: return
        val receiver = all.firstOrNull { it.id == giver.receiverParticipantId } ?: return
        val text = MessageTemplate.render(draw.messageTemplate, giver.name, receiver.name, draw.name, draw.budget)
        val dao = db.drawDao()
        dao.updateStatus(giver.id, DeliveryStatus.SENDING, null, System.currentTimeMillis())
        try {
            withContext(Dispatchers.IO) { sender.send(giver.id, phone, text, subscriptionId) }
        } catch (e: Exception) {
            dao.updateStatus(giver.id, DeliveryStatus.FAILED, "envoi refusé par le téléphone (${e.message ?: e.javaClass.simpleName})", System.currentTimeMillis())
        }
    }

    // --- Participants sans téléphone ---

    /** Nom du receveur d'un participant. Secret : à n'appeler qu'au moment de le montrer. */
    fun receiverNameOf(participant: Participant): String? =
        participants.value.firstOrNull { it.id == participant.receiverParticipantId }?.name

    fun markShown(participantId: Long) {
        viewModelScope.launch {
            db.drawDao().updateStatus(participantId, DeliveryStatus.SHOWN, null, System.currentTimeMillis())
        }
    }

    // --- Menu ---

    fun reveal() {
        viewModelScope.launch { db.drawDao().setRevealed(drawId) }
    }

    fun resetToDraft() {
        viewModelScope.launch { db.drawDao().resetToDraft(drawId) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            db.drawDao().delete(drawId)
            onDeleted()
        }
    }

    companion object {
        /** Pause entre deux SMS : envoi l'un après l'autre (SPEC.md §5.6). */
        private const val SEND_INTERVAL_MS = 1_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SantaApp
                DrawViewModel(
                    drawId = checkNotNull(createSavedStateHandle().get<Long>("drawId")),
                    db = app.database,
                    settings = app.settings,
                    sender = SmsSender(app),
                )
            }
        }
    }
}
