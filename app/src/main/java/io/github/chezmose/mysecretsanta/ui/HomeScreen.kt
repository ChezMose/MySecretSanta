package io.github.chezmose.mysecretsanta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chezmose.mysecretsanta.ui.common.NameDialog
import io.github.chezmose.mysecretsanta.ui.draws.DrawsTab
import io.github.chezmose.mysecretsanta.ui.draws.DrawsViewModel
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
    onShare: () -> Unit,
    onSettings: () -> Unit,
    onOpenDraw: (Long) -> Unit,
    onNewDraw: () -> Unit,
    onOpenGroup: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onNewPerson: (fromContacts: Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Draws) }
    var showNewGroup by rememberSaveable { mutableStateOf(false) }
    var showNewPersonChoice by rememberSaveable { mutableStateOf(false) }
    val drawsVm: DrawsViewModel = viewModel(factory = DrawsViewModel.Factory)
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
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Réglages")
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
                    onClick = { showNewPersonChoice = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Nouvelle personne") },
                )
                HomeTab.Draws -> ExtendedFloatingActionButton(
                    onClick = onNewDraw,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Nouveau tirage") },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                HomeTab.Draws -> DrawsTab(vm = drawsVm, onOpenDraw = onOpenDraw)
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

    if (showNewPersonChoice) {
        ModalBottomSheet(onDismissRequest = { showNewPersonChoice = false }) {
            ListItem(
                headlineContent = { Text("Choisir dans les contacts") },
                leadingContent = { Icon(Icons.Filled.AccountBox, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    showNewPersonChoice = false
                    onNewPerson(true)
                },
            )
            ListItem(
                headlineContent = { Text("Saisir à la main") },
                leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    showNewPersonChoice = false
                    onNewPerson(false)
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
