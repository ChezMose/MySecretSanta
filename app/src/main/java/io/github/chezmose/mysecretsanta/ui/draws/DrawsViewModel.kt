package io.github.chezmose.mysecretsanta.ui.draws

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.chezmose.mysecretsanta.SantaApp
import io.github.chezmose.mysecretsanta.data.DrawDao
import io.github.chezmose.mysecretsanta.data.DrawSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DrawsViewModel(drawDao: DrawDao) : ViewModel() {

    /** Du plus récent au plus ancien ; `null` tant que la base n'a pas répondu. */
    val draws: StateFlow<List<DrawSummary>?> =
        drawDao.observeSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SantaApp
                DrawsViewModel(app.database.drawDao())
            }
        }
    }
}
