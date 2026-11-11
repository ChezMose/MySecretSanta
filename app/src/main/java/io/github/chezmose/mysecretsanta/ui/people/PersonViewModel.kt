package io.github.chezmose.mysecretsanta.ui.people

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.withTransaction
import io.github.chezmose.mysecretsanta.SantaApp
import io.github.chezmose.mysecretsanta.data.AppDatabase
import io.github.chezmose.mysecretsanta.data.Person
import io.github.chezmose.mysecretsanta.data.SantaGroup
import io.github.chezmose.mysecretsanta.util.PhoneNumbers
import io.github.chezmose.mysecretsanta.util.sortedByName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Création ou modification d'une personne.
 * @param personId `null` pour une nouvelle personne.
 * @param presetGroupId groupe coché d'office, quand la personne est créée depuis un groupe.
 * @param fromContacts l'écran ouvre directement le sélecteur de contacts.
 */
class PersonViewModel(
    private val personId: Long?,
    presetGroupId: Long?,
    fromContacts: Boolean,
    private val db: AppDatabase,
) : ViewModel() {

    val isNew = personId == null

    /** Vrai tant que le sélecteur de contacts doit s'ouvrir à l'arrivée sur l'écran (une seule fois). */
    var pendingContactPicker by mutableStateOf(isNew && fromContacts)
        private set

    /** La création a été lancée depuis les contacts : annuler le sélecteur annule la création. */
    val startedFromContacts = isNew && fromContacts

    var name by mutableStateOf("")
        private set
    var phone by mutableStateOf("")
        private set

    /** Personne sans téléphone (un enfant, par exemple) : le numéro n'est pas demandé. */
    var noPhone by mutableStateOf(false)
        private set
    var nameError by mutableStateOf<String?>(null)
        private set
    var phoneError by mutableStateOf<String?>(null)
        private set
    var selectedGroupIds by mutableStateOf<Set<Long>>(emptySet())
        private set
    var saving by mutableStateOf(false)
        private set

    val groups: StateFlow<List<SantaGroup>> =
        db.groupDao().observeAll()
            .map { list -> list.sortedByName { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (personId != null) {
            viewModelScope.launch {
                db.personDao().get(personId)?.let {
                    name = it.name
                    phone = it.phoneE164?.let(PhoneNumbers::format).orEmpty()
                    noPhone = it.phoneE164 == null
                }
                selectedGroupIds = db.groupDao().groupIdsOf(personId).toSet()
            }
        } else if (presetGroupId != null) {
            selectedGroupIds = setOf(presetGroupId)
        }
    }

    fun onContactPickerLaunched() {
        pendingContactPicker = false
    }

    fun onNameChange(value: String) {
        name = value
        nameError = null
    }

    fun onPhoneChange(value: String) {
        phone = value
        phoneError = null
    }

    fun onNoPhoneChange(value: Boolean) {
        noPhone = value
        phoneError = null
    }

    fun toggleGroup(groupId: Long) {
        selectedGroupIds =
            if (groupId in selectedGroupIds) selectedGroupIds - groupId else selectedGroupIds + groupId
    }

    fun fillFromContact(contactName: String?, contactPhone: String?) {
        if (!contactName.isNullOrBlank()) onNameChange(contactName.trim())
        if (!contactPhone.isNullOrBlank()) {
            onNoPhoneChange(false)
            onPhoneChange(
                when (val result = PhoneNumbers.normalize(contactPhone)) {
                    is PhoneNumbers.Result.Valid -> PhoneNumbers.format(result.e164)
                    is PhoneNumbers.Result.Invalid -> contactPhone.trim()
                },
            )
        }
    }

    fun save(onSaved: () -> Unit) {
        if (saving) return
        val trimmedName = name.trim()
        nameError = if (trimmedName.isEmpty()) "Le nom est obligatoire." else null

        val phoneE164: String?
        if (noPhone) {
            phoneE164 = null
        } else {
            val normalized = PhoneNumbers.normalize(phone)
            phoneError = (normalized as? PhoneNumbers.Result.Invalid)?.message
            if (normalized !is PhoneNumbers.Result.Valid) return
            phoneE164 = normalized.e164
        }
        if (nameError != null) return

        saving = true
        viewModelScope.launch {
            if (phoneE164 != null) {
                val owner = db.personDao().findByPhone(phoneE164)
                if (owner != null && owner.id != personId) {
                    phoneError = "Ce numéro est déjà celui de ${owner.name}."
                    saving = false
                    return@launch
                }
            }
            db.withTransaction {
                val id = if (personId == null) {
                    db.personDao().insert(Person(name = trimmedName, phoneE164 = phoneE164))
                } else {
                    db.personDao().update(Person(id = personId, name = trimmedName, phoneE164 = phoneE164))
                    personId
                }
                db.groupDao().setMemberships(id, selectedGroupIds)
            }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = personId ?: return
        viewModelScope.launch {
            db.personDao().delete(id)
            onDeleted()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SantaApp
                val args = createSavedStateHandle()
                PersonViewModel(
                    personId = args.get<Long>("personId")?.takeIf { it >= 0 },
                    presetGroupId = args.get<Long>("groupId")?.takeIf { it >= 0 },
                    fromContacts = args.get<Boolean>("fromContacts") ?: false,
                    db = app.database,
                )
            }
        }
    }
}
