package io.github.chezmose.mysecretsanta.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chezmose.mysecretsanta.data.Person
import io.github.chezmose.mysecretsanta.ui.common.ConfirmDeleteDialog
import io.github.chezmose.mysecretsanta.ui.common.NameDialog
import io.github.chezmose.mysecretsanta.util.PhoneNumbers
import io.github.chezmose.mysecretsanta.util.membersLabel

/** Nombre minimal de participants pour un tirage (SPEC.md §5.4). */
private const val MIN_DRAW_SIZE = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    onBack: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onNewPerson: (groupId: Long) -> Unit,
    onOpenGroup: (Long) -> Unit,
    vm: GroupViewModel = viewModel(factory = GroupViewModel.Factory),
) {
    val group by vm.group.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val candidates by vm.candidates.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Renommer")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Plus d'actions")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Dupliquer") },
                                onClick = {
                                    menuOpen = false
                                    vm.duplicate(onCreated = onOpenGroup)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Supprimer") },
                                onClick = {
                                    menuOpen = false
                                    showDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                Text(
                    "Membres (${members.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (members.size < MIN_DRAW_SIZE) {
                item {
                    Text(
                        if (members.isEmpty()) {
                            "Aucun membre pour l'instant. Il en faut au moins $MIN_DRAW_SIZE pour un tirage."
                        } else {
                            "Il faut au moins $MIN_DRAW_SIZE membres pour un tirage."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            items(members, key = { it.id }) { person ->
                ListItem(
                    headlineContent = { Text(person.name) },
                    supportingContent = { Text(PhoneNumbers.format(person.phoneE164)) },
                    leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { vm.removeMember(person.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Retirer ${person.name} du groupe")
                        }
                    },
                    modifier = Modifier.clickable { onOpenPerson(person.id) },
                )
                HorizontalDivider()
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showAdd = true },
                        enabled = candidates.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ajouter des personnes du carnet")
                    }
                    OutlinedButton(
                        onClick = { onNewPerson(vm.groupId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Créer une nouvelle personne")
                    }
                }
            }
        }
    }

    val currentGroup = group
    if (showRename && currentGroup != null) {
        NameDialog(
            title = "Renommer le groupe",
            initialName = currentGroup.name,
            confirmLabel = "Renommer",
            onConfirm = { name ->
                showRename = false
                vm.rename(name)
            },
            onDismiss = { showRename = false },
        )
    }
    if (showDelete && currentGroup != null) {
        ConfirmDeleteDialog(
            title = "Supprimer « ${currentGroup.name} » ?",
            message = when (members.size) {
                0 -> "Le groupe sera supprimé."
                1 -> "Le groupe sera supprimé. Son membre reste dans le carnet."
                else -> "Le groupe sera supprimé. Ses ${membersLabel(members.size)} restent dans le carnet."
            },
            onConfirm = {
                showDelete = false
                vm.delete(onDeleted = onBack)
            },
            onDismiss = { showDelete = false },
        )
    }
    if (showAdd) {
        AddMembersDialog(
            candidates = candidates,
            onConfirm = { ids ->
                showAdd = false
                vm.addMembers(ids)
            },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun AddMembersDialog(
    candidates: List<Person>,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter des personnes") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(candidates, key = { it.id }) { person ->
                    val checked = person.id in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = {
                                    selected = if (checked) selected - person.id else selected + person.id
                                },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(person.name)
                            Text(
                                PhoneNumbers.format(person.phoneE164),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) {
                Text(if (selected.size > 1) "Ajouter (${selected.size})" else "Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
