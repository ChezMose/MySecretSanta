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
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import io.github.chezmose.mysecretsanta.draw.Feasibility
import io.github.chezmose.mysecretsanta.draw.SecretSanta
import io.github.chezmose.mysecretsanta.ui.common.AlertCard
import io.github.chezmose.mysecretsanta.ui.common.ConfirmDeleteDialog
import io.github.chezmose.mysecretsanta.ui.exclusions.describe
import io.github.chezmose.mysecretsanta.ui.exclusions.icon
import io.github.chezmose.mysecretsanta.ui.exclusions.label
import io.github.chezmose.mysecretsanta.ui.common.NameDialog
import io.github.chezmose.mysecretsanta.ui.draws.participantsLabel
import io.github.chezmose.mysecretsanta.ui.draws.statusLabel
import io.github.chezmose.mysecretsanta.util.PhoneNumbers
import io.github.chezmose.mysecretsanta.util.membersLabel

private const val MIN_DRAW_SIZE = SecretSanta.MIN_PARTICIPANTS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    onBack: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onNewPerson: (groupId: Long, fromContacts: Boolean) -> Unit,
    onOpenGroup: (Long) -> Unit,
    onAddExclusion: (groupId: Long) -> Unit,
    onOpenExclusion: (groupId: Long, ruleId: Long) -> Unit,
    onNewDraw: (groupId: Long) -> Unit,
    onOpenDraw: (drawId: Long) -> Unit,
    vm: GroupViewModel = viewModel(factory = GroupViewModel.Factory),
) {
    val group by vm.group.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val candidates by vm.candidates.collectAsStateWithLifecycle()
    val exclusions by vm.exclusions.collectAsStateWithLifecycle()
    val feasibility by vm.feasibility.collectAsStateWithLifecycle()
    val draws by vm.draws.collectAsStateWithLifecycle()
    val namesById = members.associate { it.id to it.name }
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNewDraw(vm.groupId) },
                icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                text = { Text("Nouveau tirage") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
        ) {
            when (val status = feasibility) {
                is Feasibility.Impossible -> item {
                    AlertCard(
                        title = "Tirage impossible",
                        message = (
                            status.reasons +
                                "Les exclusions ne laissent aucune répartition possible : retire ou modifie une exclusion."
                            ).joinToString("\n"),
                        isError = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Feasibility.OnlyWithReciprocal -> item {
                    AlertCard(
                        title = "Tirage possible seulement avec un échange réciproque",
                        message = "Avec ces exclusions, au moins deux personnes devront s'offrir un cadeau " +
                            "l'une à l'autre. Tu pourras l'accepter au moment du tirage, " +
                            "ou modifier les exclusions.",
                        isError = false,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                else -> Unit
            }
            item {
                SectionHeader("Membres (${members.size})")
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
                    supportingContent = { Text(PhoneNumbers.label(person.phoneE164)) },
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
                        onClick = { onNewPerson(vm.groupId, true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.AccountBox, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ajouter depuis les contacts")
                    }
                    OutlinedButton(
                        onClick = { onNewPerson(vm.groupId, false) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Saisir une nouvelle personne")
                    }
                }
            }

            item {
                SectionHeader("Exclusions (${exclusions.size})")
            }
            if (exclusions.isEmpty()) {
                item {
                    Text(
                        "Aucune exclusion. Ajoute par exemple les couples, " +
                            "qui ne doivent pas se tirer l'un l'autre.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            items(exclusions, key = { "exclusion-${it.rule.id}" }) { exclusion ->
                val type = exclusion.rule.type
                ListItem(
                    headlineContent = { Text(exclusion.describe { namesById[it] ?: "?" }) },
                    supportingContent = {
                        Text(listOfNotNull(type.label, exclusion.rule.label).joinToString(" · "))
                    },
                    leadingContent = { Icon(type.icon, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenExclusion(vm.groupId, exclusion.rule.id) },
                )
                HorizontalDivider()
            }
            item {
                OutlinedButton(
                    onClick = { onAddExclusion(vm.groupId) },
                    enabled = members.size >= 2,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ajouter une exclusion")
                }
            }

            item {
                SectionHeader("Tirages (${draws.size})")
            }
            if (draws.isEmpty()) {
                item {
                    Text(
                        "Aucun tirage pour l'instant.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            items(draws, key = { "draw-${it.id}" }) { draw ->
                ListItem(
                    headlineContent = { Text(draw.name) },
                    supportingContent = { Text(participantsLabel(draw.participantCount)) },
                    leadingContent = { Icon(Icons.Filled.Star, contentDescription = null) },
                    trailingContent = {
                        Text(
                            draw.statusLabel,
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
            } + " Ses tirages en brouillon seront supprimés ; ses tirages déjà faits restent dans l'historique.",
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
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
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
                                PhoneNumbers.label(person.phoneE164),
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
