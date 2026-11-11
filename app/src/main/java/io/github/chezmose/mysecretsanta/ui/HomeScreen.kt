package io.github.chezmose.mysecretsanta.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chezmose.mysecretsanta.ui.common.EmptyState
import io.github.chezmose.mysecretsanta.ui.common.NameDialog
import io.github.chezmose.mysecretsanta.ui.groups.GroupsTab
import io.github.chezmose.mysecretsanta.ui.groups.GroupsViewModel
import io.github.chezmose.mysecretsanta.ui.people.PeopleTab
import io.github.chezmose.mysecretsanta.ui.people.PeopleViewModel

private enum class HomeTab(val label: String, val icon: ImageVector) {
    Draws("Tirages", Icons.Filled.Star),
    Groups("Groupes", Icons.AutoMirrored.Filled.List),
    People("Personnes", Icons.Filled.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    version: String,
    onShare: () -> Unit,
    onOpenGroup: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onNewPerson: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Draws) }
    var showNewGroup by rememberSaveable { mutableStateOf(false) }
    val groupsVm: GroupsViewModel = viewModel(factory = GroupsViewModel.Factory)
    val peopleVm: PeopleViewModel = viewModel(factory = PeopleViewModel.Factory)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MySecretSanta") },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Partager")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            when (tab) {
                HomeTab.Groups -> ExtendedFloatingActionButton(
                    onClick = { showNewGroup = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Nouveau groupe") },
                )
                HomeTab.People -> ExtendedFloatingActionButton(
                    onClick = onNewPerson,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Nouvelle personne") },
                )
                HomeTab.Draws -> Unit
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                HomeTab.Draws -> EmptyState(
                    icon = Icons.Filled.Star,
                    title = "Les tirages arrivent bientôt",
                    message = "Le tirage au sort arrivera dans une prochaine version. " +
                        "En attendant, enregistre les personnes et réunis-les en groupes.\n\n" +
                        "Version $version",
                )
                HomeTab.Groups -> GroupsTab(vm = groupsVm, onOpenGroup = onOpenGroup)
                HomeTab.People -> PeopleTab(vm = peopleVm, onOpenPerson = onOpenPerson)
            }
        }
    }

    if (showNewGroup) {
        NameDialog(
            title = "Nouveau groupe",
            initialName = "",
            confirmLabel = "Créer",
            onConfirm = { name ->
                showNewGroup = false
                groupsVm.create(name, onOpenGroup)
            },
            onDismiss = { showNewGroup = false },
        )
    }
}
