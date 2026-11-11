package io.github.chezmose.mysecretsanta.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.chezmose.mysecretsanta.ui.groups.GroupScreen
import io.github.chezmose.mysecretsanta.ui.people.PersonScreen
import io.github.chezmose.mysecretsanta.ui.share.ShareScreen

private object Routes {
    const val HOME = "home"
    const val SHARE = "share"
    const val GROUP = "group/{groupId}"
    const val PERSON = "person?personId={personId}&groupId={groupId}"

    fun group(groupId: Long) = "group/$groupId"
    fun editPerson(personId: Long) = "person?personId=$personId"
    fun newPerson(groupId: Long? = null) = if (groupId == null) "person" else "person?groupId=$groupId"
}

@Composable
fun AppNavHost(version: String) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                version = version,
                onShare = { nav.navigate(Routes.SHARE) },
                onOpenGroup = { nav.navigate(Routes.group(it)) },
                onOpenPerson = { nav.navigate(Routes.editPerson(it)) },
                onNewPerson = { nav.navigate(Routes.newPerson()) },
            )
        }
        composable(Routes.SHARE) {
            ShareScreen(onBack = { nav.back() })
        }
        composable(
            Routes.GROUP,
            arguments = listOf(navArgument("groupId") { type = NavType.LongType }),
        ) {
            GroupScreen(
                onBack = { nav.back() },
                onOpenPerson = { nav.navigate(Routes.editPerson(it)) },
                onNewPerson = { groupId -> nav.navigate(Routes.newPerson(groupId)) },
                onOpenGroup = { nav.navigate(Routes.group(it)) },
            )
        }
        composable(
            Routes.PERSON,
            arguments = listOf(
                navArgument("personId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("groupId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            PersonScreen(onBack = { nav.back() })
        }
    }
}

/** Retour en arrière sans jamais dépiler l'écran d'accueil (double appui rapide). */
private fun NavController.back() {
    if (previousBackStackEntry != null) popBackStack()
}
