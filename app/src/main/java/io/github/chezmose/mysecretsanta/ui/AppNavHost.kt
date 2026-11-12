package io.github.chezmose.mysecretsanta.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.chezmose.mysecretsanta.ui.draws.DrawEditScreen
import io.github.chezmose.mysecretsanta.ui.draws.DrawScreen
import io.github.chezmose.mysecretsanta.ui.exclusions.ExclusionScreen
import io.github.chezmose.mysecretsanta.ui.groups.GroupScreen
import io.github.chezmose.mysecretsanta.ui.people.PersonScreen
import io.github.chezmose.mysecretsanta.ui.settings.SettingsScreen
import io.github.chezmose.mysecretsanta.ui.share.ShareScreen

private object Routes {
    const val HOME = "home"
    const val SHARE = "share"
    const val SETTINGS = "settings"
    const val GROUP = "group/{groupId}"
    const val PERSON = "person?personId={personId}&groupId={groupId}&fromContacts={fromContacts}"
    const val EXCLUSION = "exclusion/{groupId}?ruleId={ruleId}"
    const val DRAW_EDIT = "draw/edit?drawId={drawId}&groupId={groupId}"
    const val DRAW = "draws/{drawId}"

    fun draw(drawId: Long) = "draws/$drawId"

    fun group(groupId: Long) = "group/$groupId"
    fun newExclusion(groupId: Long) = "exclusion/$groupId"
    fun editExclusion(groupId: Long, ruleId: Long) = "exclusion/$groupId?ruleId=$ruleId"
    fun editPerson(personId: Long) = "person?personId=$personId"

    /** @param fromContacts ouvre directement le sélecteur de contacts. */
    fun newPerson(groupId: Long?, fromContacts: Boolean): String =
        listOfNotNull(groupId?.let { "groupId=$it" }, "fromContacts=$fromContacts")
            .joinToString("&", prefix = "person?")

    fun newDraw(groupId: Long?) = if (groupId == null) "draw/edit" else "draw/edit?groupId=$groupId"
    fun editDraw(drawId: Long) = "draw/edit?drawId=$drawId"
}

@Composable
fun AppNavHost(version: String) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onShare = { nav.navigate(Routes.SHARE) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenDraw = { nav.navigate(Routes.draw(it)) },
                onNewDraw = { nav.navigate(Routes.newDraw(null)) },
                onOpenGroup = { nav.navigate(Routes.group(it)) },
                onOpenPerson = { nav.navigate(Routes.editPerson(it)) },
                onNewPerson = { fromContacts -> nav.navigate(Routes.newPerson(null, fromContacts)) },
            )
        }
        composable(Routes.SHARE) {
            ShareScreen(onBack = { nav.back() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(version = version, onBack = { nav.back() })
        }
        composable(
            Routes.GROUP,
            arguments = listOf(navArgument("groupId") { type = NavType.LongType }),
        ) {
            GroupScreen(
                onBack = { nav.back() },
                onOpenPerson = { nav.navigate(Routes.editPerson(it)) },
                onNewPerson = { groupId, fromContacts ->
                    nav.navigate(Routes.newPerson(groupId, fromContacts))
                },
                onOpenGroup = { nav.navigate(Routes.group(it)) },
                onAddExclusion = { groupId -> nav.navigate(Routes.newExclusion(groupId)) },
                onOpenExclusion = { groupId, ruleId -> nav.navigate(Routes.editExclusion(groupId, ruleId)) },
                onNewDraw = { groupId -> nav.navigate(Routes.newDraw(groupId)) },
                onOpenDraw = { nav.navigate(Routes.draw(it)) },
            )
        }
        composable(
            Routes.EXCLUSION,
            arguments = listOf(
                navArgument("groupId") { type = NavType.LongType },
                navArgument("ruleId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            ExclusionScreen(onBack = { nav.back() })
        }
        composable(
            Routes.PERSON,
            arguments = listOf(
                navArgument("personId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("groupId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("fromContacts") { type = NavType.BoolType; defaultValue = false },
            ),
        ) {
            PersonScreen(onBack = { nav.back() })
        }
        composable(
            Routes.DRAW_EDIT,
            arguments = listOf(
                navArgument("drawId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("groupId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            DrawEditScreen(
                onBack = { nav.back() },
                onSaved = { drawId, wasNew ->
                    if (wasNew) {
                        // Nouveau tirage : on arrive sur son écran, sans repasser par le formulaire au retour.
                        nav.navigate(Routes.draw(drawId)) { popUpTo(Routes.DRAW_EDIT) { inclusive = true } }
                    } else {
                        nav.back()
                    }
                },
                onDeleted = { nav.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(
            Routes.DRAW,
            arguments = listOf(navArgument("drawId") { type = NavType.LongType }),
        ) {
            DrawScreen(
                onBack = { nav.back() },
                onEdit = { drawId -> nav.navigate(Routes.editDraw(drawId)) },
            )
        }
    }
}

/** Retour en arrière sans jamais dépiler l'écran d'accueil (double appui rapide). */
private fun NavController.back() {
    if (previousBackStackEntry != null) popBackStack()
}
