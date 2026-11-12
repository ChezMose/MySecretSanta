package io.github.chezmose.mysecretsanta.ui.draws

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chezmose.mysecretsanta.data.DrawStatus
import io.github.chezmose.mysecretsanta.ui.common.EmptyState
import io.github.chezmose.mysecretsanta.util.formatShortDate
import java.time.LocalDate

val DrawStatus.label: String
    get() = when (this) {
        DrawStatus.DRAFT -> "Brouillon"
        DrawStatus.DRAWN -> "Tiré"
        DrawStatus.SENT -> "Envoyé"
    }

fun participantsLabel(count: Int): String = if (count <= 1) "$count participant" else "$count participants"

@Composable
fun DrawsTab(vm: DrawsViewModel, onOpenDraw: (Long) -> Unit) {
    val draws by vm.draws.collectAsStateWithLifecycle()
    val list = draws ?: return

    if (list.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Star,
            title = "Aucun tirage",
            message = "Crée un tirage à partir d'un groupe : choisis les participants, " +
                "la date, le budget et le message envoyé à chacun.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(list, key = { it.id }) { draw ->
            ListItem(
                headlineContent = { Text(draw.name) },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            draw.groupName ?: "Groupe supprimé",
                            draw.dateEpochDay?.let { formatShortDate(LocalDate.ofEpochDay(it)) },
                            participantsLabel(draw.participantCount),
                        ).joinToString(" · "),
                    )
                },
                leadingContent = { Icon(Icons.Filled.Star, contentDescription = null) },
                trailingContent = {
                    Text(
                        draw.status.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier.clickable { onOpenDraw(draw.id) },
            )
            HorizontalDivider()
        }
    }
}
