package io.github.chezmose.mysecretsanta.ui.exclusions

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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import io.github.chezmose.mysecretsanta.data.ExclusionType
import io.github.chezmose.mysecretsanta.ui.common.CheckboxRow
import io.github.chezmose.mysecretsanta.ui.common.ConfirmDeleteDialog
import io.github.chezmose.mysecretsanta.ui.common.RadioRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExclusionScreen(
    onBack: () -> Unit,
    vm: ExclusionViewModel = viewModel(factory = ExclusionViewModel.Factory),
) {
    val members by vm.members.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "Nouvelle exclusion" else "Modifier l'exclusion") },
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
            val types = ExclusionType.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = vm.type == type,
                        onClick = { vm.onTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                    ) {
                        Text(type.label)
                    }
                }
            }
            Text(
                vm.type.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            when (vm.type) {
                ExclusionType.COUPLE, ExclusionType.HOUSEHOLD -> {
                    SectionTitle(
                        if (vm.type == ExclusionType.COUPLE) {
                            "Les 2 personnes du couple"
                        } else {
                            "Les membres du foyer"
                        },
                    )
                    members.forEach { person ->
                        val checked = person.id in vm.selected
                        CheckboxRow(
                            label = person.name,
                            checked = checked,
                            onCheckedChange = { vm.toggle(person.id) },
                            enabled = checked || vm.type != ExclusionType.COUPLE || vm.selected.size < 2,
                        )
                    }
                }
                ExclusionType.ONE_WAY -> {
                    SectionTitle("Qui n'offre pas…")
                    members.forEach { person ->
                        RadioRow(
                            label = person.name,
                            selected = vm.giver == person.id,
                            onClick = { vm.onGiverChange(person.id) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    SectionTitle("… à qui ?")
                    members.filter { it.id != vm.giver }.forEach { person ->
                        RadioRow(
                            label = person.name,
                            selected = vm.receiver == person.id,
                            onClick = { vm.onReceiverChange(person.id) },
                        )
                    }
                }
            }

            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.label,
                onValueChange = vm::onLabelChange,
                label = { Text("Libellé (facultatif)") },
                placeholder = { Text("Ex. : mariés, frères et sœurs…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.save(onSaved = onBack) },
                enabled = !vm.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enregistrer")
            }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Supprimer cette exclusion ?",
            message = "Les personnes concernées pourront de nouveau se tirer entre elles.",
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
