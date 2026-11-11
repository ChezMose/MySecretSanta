package io.github.chezmose.mysecretsanta.ui.people

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chezmose.mysecretsanta.ui.common.ConfirmDeleteDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    onBack: () -> Unit,
    vm: PersonViewModel = viewModel(factory = PersonViewModel.Factory),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups by vm.groups.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // Création « depuis les contacts » : le sélecteur du système s'ouvre dès l'arrivée sur l'écran.
    // Il ne demande pas la permission READ_CONTACTS.
    val pickContact = rememberLauncherForActivityResult(PickPhoneContact()) { uri ->
        if (uri != null) {
            scope.launch {
                val contact = withContext(Dispatchers.IO) { readContact(context, uri) }
                if (contact != null) vm.fillFromContact(contact.first, contact.second)
            }
        } else if (vm.startedFromContacts && vm.name.isEmpty() && vm.phone.isEmpty()) {
            // Sélecteur annulé : rien à créer.
            onBack()
        }
    }
    LaunchedEffect(Unit) {
        if (vm.pendingContactPicker) {
            vm.onContactPickerLaunched()
            try {
                pickContact.launch(Unit)
            } catch (_: ActivityNotFoundException) {
                // Pas d'app Contacts : la saisie manuelle reste possible.
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "Nouvelle personne" else "Modifier la personne") },
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
            OutlinedTextField(
                value = vm.name,
                onValueChange = vm::onNameChange,
                label = { Text("Nom") },
                isError = vm.nameError != null,
                supportingText = { vm.nameError?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = if (vm.noPhone) ImeAction.Done else ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            CheckboxRow(
                label = "Pas de téléphone",
                checked = vm.noPhone,
                onCheckedChange = vm::onNoPhoneChange,
            )

            if (!vm.noPhone) {
                OutlinedTextField(
                    value = vm.phone,
                    onValueChange = vm::onPhoneChange,
                    label = { Text("Numéro de mobile") },
                    isError = vm.phoneError != null,
                    supportingText = { Text(vm.phoneError ?: "06 12 34 56 78, ou +32 … pour l'étranger") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "Cette personne ne recevra pas de SMS : elle découvrira son tirage sur ce téléphone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("Groupes", style = MaterialTheme.typography.titleMedium)
            if (groups.isEmpty()) {
                Text(
                    "Aucun groupe pour l'instant.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            groups.forEach { group ->
                CheckboxRow(
                    label = group.name,
                    checked = group.id in vm.selectedGroupIds,
                    onCheckedChange = { vm.toggleGroup(group.id) },
                )
            }

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
            title = "Supprimer ${vm.name} ?",
            message = "Cette personne sera retirée du carnet et de tous ses groupes.",
            onConfirm = {
                confirmDelete = false
                vm.delete(onDeleted = onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Case à cocher dont toute la ligne est cliquable. */
@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** Ouvre le sélecteur de numéros de téléphone des contacts. */
private class PickPhoneContact : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Nom et numéro du contact choisi, lisibles grâce à l'accès temporaire accordé par le sélecteur. */
private fun readContact(context: Context, uri: Uri): Pair<String?, String?>? =
    try {
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1) else null
        }
    } catch (_: SecurityException) {
        null
    }
