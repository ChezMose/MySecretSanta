package io.github.chezmose.mysecretsanta.ui.people

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.chezmose.mysecretsanta.SantaApp
import io.github.chezmose.mysecretsanta.data.Person
import io.github.chezmose.mysecretsanta.data.PersonDao
import io.github.chezmose.mysecretsanta.util.PhoneNumbers
import io.github.chezmose.mysecretsanta.util.searchKey
import io.github.chezmose.mysecretsanta.util.sortedByName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class PeopleViewModel(personDao: PersonDao) : ViewModel() {

    var query by mutableStateOf("")
        private set

    fun updateQuery(value: String) {
        query = value
    }

    /** `null` tant que la base n'a pas répondu. */
    val people: StateFlow<List<Person>?> =
        combine(personDao.observeAll(), snapshotFlow { query }) { all, q ->
            all.filter { it.matches(q) }.sortedByName { it.name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SantaApp
                PeopleViewModel(app.database.personDao())
            }
        }
    }
}

/** Recherche dans le nom (sans accents ni casse) et dans le numéro. */
private fun Person.matches(query: String): Boolean {
    val key = query.searchKey()
    if (key.isEmpty() || name.searchKey().contains(key)) return true
    val phone = phoneE164 ?: return false
    val digits = query.filter { it in '0'..'9' }
    if (digits.length < 2) return false
    return phone.contains(digits) ||
        PhoneNumbers.format(phone).filter { it in '0'..'9' }.contains(digits)
}
