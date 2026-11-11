package io.github.chezmose.mysecretsanta.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chezmose.mysecretsanta.ui.common.EmptyState
import io.github.chezmose.mysecretsanta.util.membersLabel

@Composable
fun GroupsTab(vm: GroupsViewModel, onOpenGroup: (Long) -> Unit) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val list = groups ?: return

    if (list.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.List,
            title = "Aucun groupe",
            message = "Crée un groupe pour réunir les personnes qui font un tirage ensemble, " +
                "par exemple ta famille ou tes collègues. Tu le réutiliseras chaque année.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(list, key = { it.id }) { group ->
            ListItem(
                headlineContent = { Text(group.name) },
                supportingContent = { Text(membersLabel(group.memberCount)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                modifier = Modifier.clickable { onOpenGroup(group.id) },
            )
            HorizontalDivider()
        }
    }
}
