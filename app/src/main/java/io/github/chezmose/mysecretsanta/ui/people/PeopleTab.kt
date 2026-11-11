package io.github.chezmose.mysecretsanta.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chezmose.mysecretsanta.ui.common.EmptyState
import io.github.chezmose.mysecretsanta.util.PhoneNumbers

@Composable
fun PeopleTab(vm: PeopleViewModel, onOpenPerson: (Long) -> Unit) {
    val people by vm.people.collectAsStateWithLifecycle()
    val list = people ?: return

    if (list.isEmpty() && vm.query.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Person,
            title = "Aucune personne",
            message = "Ajoute les personnes qui participeront à tes tirages, " +
                "à la main ou depuis tes contacts. Tu pourras ensuite les réunir en groupes.",
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::updateQuery,
            placeholder = { Text("Rechercher un nom ou un numéro") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.updateQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Effacer la recherche")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (list.isEmpty()) {
            Text(
                "Aucun résultat.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            items(list, key = { it.id }) { person ->
                ListItem(
                    headlineContent = { Text(person.name) },
                    supportingContent = { Text(PhoneNumbers.format(person.phoneE164)) },
                    leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenPerson(person.id) },
                )
                HorizontalDivider()
            }
        }
    }
}
