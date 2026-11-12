package io.github.chezmose.mysecretsanta.ui.draws

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chezmose.mysecretsanta.draw.Feasibility
import io.github.chezmose.mysecretsanta.draw.SecretSanta
import io.github.chezmose.mysecretsanta.ui.common.AlertCard
import io.github.chezmose.mysecretsanta.ui.common.CheckboxRow
import io.github.chezmose.mysecretsanta.ui.common.ConfirmDeleteDialog
import io.github.chezmose.mysecretsanta.ui.common.MessageEditor
import io.github.chezmose.mysecretsanta.ui.common.RadioRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawEditScreen(
    onBack: () -> Unit,
    onSaved: (drawId: Long) -> Unit,
    vm: DrawEditViewModel = viewModel(factory = DrawEditViewModel.Factory),
) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val feasibility by vm.feasibility.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "Nouveau tirage" else "Modifier le tirage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (!vm.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // --- Groupe ---
            if (vm.canChooseGroup) {
                SectionTitle("Groupe")
                if (groups.isEmpty()) {
                    Text(
                        "Aucun groupe pour l'instant. Crée d'abord un groupe dans l'onglet Groupes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                groups.forEach { group ->
                    RadioRow(
                        label = group.name,
                        selected = vm.groupId == group.id,
                        onClick = { vm.selectGroup(group.id) },
                    )
                }
                vm.groupError?.let { ErrorText(it) }
                Spacer(Modifier.height(8.dp))
            } else {
                val groupName = groups.firstOrNull { it.id == vm.groupId }?.name
                Text(
                    "Groupe : ${groupName ?: "supprimé"}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            // --- Nom, budget ---
            OutlinedTextField(
                value = vm.name,
                onValueChange = vm::onNameChange,
                label = { Text("Nom du tirage") },
                isError = vm.nameError != null,
                supportingText = { vm.nameError?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.budget,
                onValueChange = vm::onBudgetChange,
                label = { Text("Budget (facultatif)") },
                placeholder = { Text("Ex. : 30 €") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // --- Participants ---
            val memberList = members.orEmpty()
            if (vm.groupId != null) {
                Spacer(Modifier.height(8.dp))
                SectionTitle("Participants (${vm.selected.count { id -> memberList.any { it.id == id } }}/${memberList.size})")
                memberList.forEach { person ->
                    CheckboxRow(
                        label = if (person.phoneE164 == null) "${person.name} (pas de téléphone)" else person.name,
                        checked = person.id in vm.selected,
                        onCheckedChange = { vm.toggle(person.id) },
                    )
                }
                when (val status = feasibility) {
                    Feasibility.TooFewMembers -> ErrorText(
                        "Il faut au moins ${SecretSanta.MIN_PARTICIPANTS} participants pour tirer au sort.",
                    )
                    is Feasibility.Impossible -> AlertCard(
                        title = "Tirage impossible",
                        message = (
                            status.reasons +
                                "Les exclusions du groupe ne laissent aucune répartition possible avec ces participants."
                            ).joinToString("\n"),
                        isError = true,
                    )
                    Feasibility.OnlyWithReciprocal -> AlertCard(
                        title = "Tirage possible seulement avec un échange réciproque",
                        message = "Avec ces participants et les exclusions du groupe, au moins deux personnes " +
                            "devront s'offrir un cadeau l'une à l'autre. Tu pourras l'accepter au moment du tirage.",
                        isError = false,
                    )
                    else -> Unit
                }
            }

            // --- Message ---
            Spacer(Modifier.height(8.dp))
            SectionTitle("Message envoyé à chaque participant")
            MessageEditor(
                template = vm.template,
                onTemplateChange = vm::onTemplateChange,
                defaultTemplate = vm.defaultTemplate,
                drawName = vm.name,
                budget = vm.budget,
                longestName = vm.longestName(memberList),
                error = vm.templateError,
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.save(onSaved = onSaved) },
                enabled = !vm.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enregistrer")
            }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Supprimer « ${vm.name} » ?",
            message = "Ce tirage en brouillon sera supprimé.",
            onConfirm = {
                confirmDelete = false
                vm.delete(onDeleted = onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}
