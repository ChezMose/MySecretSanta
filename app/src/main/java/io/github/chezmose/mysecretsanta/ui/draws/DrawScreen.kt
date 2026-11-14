package io.github.chezmose.mysecretsanta.ui.draws

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.chezmose.mysecretsanta.data.DeliveryStatus
import io.github.chezmose.mysecretsanta.data.DrawStatus
import io.github.chezmose.mysecretsanta.data.Participant
import io.github.chezmose.mysecretsanta.draw.Feasibility
import io.github.chezmose.mysecretsanta.draw.SecretSanta
import io.github.chezmose.mysecretsanta.sms.Sim
import io.github.chezmose.mysecretsanta.ui.common.AlertCard
import io.github.chezmose.mysecretsanta.ui.common.ConfirmDeleteDialog
import io.github.chezmose.mysecretsanta.ui.common.RadioRow
import io.github.chezmose.mysecretsanta.util.PhoneNumbers
import kotlinx.coroutines.delay

/** Valeur de [sendTarget] pour « tous les SMS restants ». */
private const val ALL = -1L

/** Durée du compte à rebours avant d'afficher le résultat d'un participant sans téléphone (SPEC.md §5.9). */
private const val COUNTDOWN_SECONDS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawScreen(
    onBack: () -> Unit,
    onEdit: (drawId: Long) -> Unit,
    vm: DrawViewModel = viewModel(factory = DrawViewModel.Factory),
) {
    val context = LocalContext.current
    val draw by vm.draw.collectAsStateWithLifecycle()
    val participants by vm.participants.collectAsStateWithLifecycle()
    val groupName by vm.groupName.collectAsStateWithLifecycle()
    val feasibility by vm.feasibility.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var revealStep by rememberSaveable { mutableStateOf(0) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    /** Envoi demandé, en attente de la permission : [ALL] ou l'identifiant d'un participant. */
    var pendingTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    /** Envoi à confirmer : [ALL] ou l'identifiant d'un participant. */
    var sendTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var showFor by rememberSaveable { mutableStateOf<Long?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.SEND_SMS] == true) {
            sendTarget = pendingTarget
        } else {
            permissionDenied = true
        }
        pendingTarget = null
    }
    val requestSend = { target: Long ->
        if (vm.hasSendPermission()) {
            sendTarget = target
        } else {
            pendingTarget = target
            permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE))
        }
    }

    val current = draw
    val isDraft = current?.status == DrawStatus.DRAFT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (current != null && isDraft) {
                        IconButton(onClick = { onEdit(current.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Modifier le tirage")
                        }
                    }
                    if (current != null && !isDraft) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Plus d'actions")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (!current.revealed) {
                                    DropdownMenuItem(
                                        text = { Text("Révéler le tirage") },
                                        onClick = {
                                            menuOpen = false
                                            revealStep = 1
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Refaire le tirage") },
                                    onClick = {
                                        menuOpen = false
                                        confirmReset = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Supprimer le tirage") },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (current == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Groupe : ${groupName ?: "supprimé"}", style = MaterialTheme.typography.bodyLarge)
                    current.budget?.let { Text("Budget : $it", style = MaterialTheme.typography.bodyLarge) }
                    if (current.allowReciprocal) {
                        Text(
                            "Échanges réciproques autorisés pour ce tirage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isDraft) {
                draftSection(
                    participants = participants,
                    feasibility = feasibility,
                    drawing = vm.drawing,
                    onDraw = vm::performDraw,
                    onEdit = { onEdit(current.id) },
                )
            } else {
                val pending = vm.pendingSms(participants)
                item {
                    ResultSummary(participants)
                }
                if (pending.isNotEmpty()) {
                    item {
                        Button(
                            onClick = { requestSend(ALL) },
                            enabled = !vm.sending,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Text(
                                if (vm.sending) "Envoi en cours…" else "Envoyer les SMS (${pending.size})",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                item {
                    Text(
                        if (current.revealed) "Tirage révélé" else "Participants",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(participants, key = { it.id }) { participant ->
                    ResultRow(
                        participant = participant,
                        receiverName = if (current.revealed) vm.receiverNameOf(participant) else null,
                        onResend = { requestSend(participant.id) },
                        onShow = { showFor = participant.id },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // --- Boîtes de dialogue ---

    when (val problem = vm.problem) {
        is DrawViewModel.DrawProblem.Message -> AlertDialog(
            onDismissRequest = vm::dismissProblem,
            title = { Text(problem.title) },
            text = { Text(problem.text) },
            confirmButton = { TextButton(onClick = vm::dismissProblem) { Text("OK") } },
        )
        DrawViewModel.DrawProblem.OnlyWithReciprocal -> AlertDialog(
            onDismissRequest = vm::dismissProblem,
            title = { Text("Accepter un échange réciproque ?") },
            text = {
                Text(
                    "Avec ces participants et les exclusions du groupe, le tirage n'est possible que si " +
                        "au moins deux personnes s'offrent un cadeau l'une à l'autre. L'accepter pour ce tirage ?",
                )
            },
            confirmButton = { TextButton(onClick = vm::acceptReciprocalAndDraw) { Text("Accepter et tirer") } },
            dismissButton = { TextButton(onClick = vm::dismissProblem) { Text("Annuler") } },
        )
        null -> Unit
    }

    sendTarget?.let { target ->
        val one = participants.firstOrNull { it.id == target }
        val count = if (target == ALL) vm.pendingSms(participants).size else 1
        SendDialog(
            title = if (target == ALL) "Envoyer $count SMS ?" else "Envoyer le SMS de ${one?.name.orEmpty()} ?",
            sims = remember { vm.activeSims() },
            initialSubscriptionId = vm.savedSubscriptionId,
            onConfirm = { subscriptionId ->
                sendTarget = null
                if (target == ALL) vm.sendPending(subscriptionId) else vm.resend(target, subscriptionId)
            },
            onDismiss = { sendTarget = null },
        )
    }

    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = { permissionDenied = false },
            title = { Text("Autorisation nécessaire") },
            text = {
                Text(
                    "Pour envoyer à chacun le résultat du tirage, l'app doit pouvoir envoyer des SMS.\n\n" +
                        "Dans les réglages de l'app : Autorisations → SMS → Autoriser.\n\n" +
                        "Si Android affiche « Paramètre restreint » (Android 13 et plus, app installée hors " +
                        "du Play Store) : reviens sur la page de l'app, touche ⋮ en haut à droite, " +
                        "« Autoriser les paramètres restreints », confirme, puis autorise les SMS.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionDenied = false
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                ) { Text("Ouvrir les réglages") }
            },
            dismissButton = { TextButton(onClick = { permissionDenied = false }) { Text("Annuler") } },
        )
    }

    showFor?.let { id ->
        val participant = participants.firstOrNull { it.id == id }
        if (participant != null) {
            ShowResultDialog(
                giverName = participant.name,
                receiverName = { vm.receiverNameOf(participant) },
                onShown = { vm.markShown(participant.id) },
                onClose = { showFor = null },
            )
        }
    }

    when (revealStep) {
        1 -> AlertDialog(
            onDismissRequest = { revealStep = 0 },
            title = { Text("Révéler le tirage ?") },
            text = { Text("Tu vas voir qui offre à qui. Si tu participes, la surprise sera gâchée.") },
            confirmButton = { TextButton(onClick = { revealStep = 2 }) { Text("Continuer") } },
            dismissButton = { TextButton(onClick = { revealStep = 0 }) { Text("Annuler") } },
        )
        2 -> AlertDialog(
            onDismissRequest = { revealStep = 0 },
            title = { Text("Vraiment ?") },
            text = { Text("Le tirage restera marqué comme révélé.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        revealStep = 0
                        vm.reveal()
                    },
                ) { Text("Révéler") }
            },
            dismissButton = { TextButton(onClick = { revealStep = 0 }) { Text("Annuler") } },
        )
    }

    if (confirmReset) {
        val transmitted = participants.any { it.status?.isDone == true }
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Refaire le tirage ?") },
            text = {
                Text(
                    (
                        if (transmitted) {
                            "Des résultats ont déjà été transmis : ils ne seront plus valables, " +
                                "préviens les participants. "
                        } else {
                            ""
                        }
                        ) + "Le tirage redevient un brouillon, que tu pourras modifier puis tirer à nouveau.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        vm.resetToDraft()
                    },
                ) { Text("Refaire") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Annuler") } },
        )
    }

    if (confirmDelete && current != null) {
        ConfirmDeleteDialog(
            title = "Supprimer « ${current.name} » ?",
            message = "Le tirage et ses résultats seront supprimés de l'historique.",
            onConfirm = {
                confirmDelete = false
                vm.delete(onDeleted = onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.draftSection(
    participants: List<Participant>,
    feasibility: Feasibility?,
    drawing: Boolean,
    onDraw: () -> Unit,
    onEdit: () -> Unit,
) {
    item {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Brouillon : vérifie les participants et le message, puis tire au sort. " +
                    "Personne ne verra le résultat sur cet écran.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (feasibility) {
                is Feasibility.Impossible -> AlertCard(
                    title = "Tirage impossible",
                    message = (feasibility.reasons + "Les exclusions du groupe ne laissent aucune répartition possible.")
                        .joinToString("\n"),
                    isError = true,
                )
                Feasibility.OnlyWithReciprocal -> AlertCard(
                    title = "Tirage possible seulement avec un échange réciproque",
                    message = "Au moins deux personnes devront s'offrir un cadeau l'une à l'autre. " +
                        "L'app te demandera de l'accepter.",
                    isError = false,
                )
                Feasibility.TooFewMembers -> AlertCard(
                    title = "Pas assez de participants",
                    message = "Il faut au moins ${SecretSanta.MIN_PARTICIPANTS} participants.",
                    isError = true,
                )
                else -> Unit
            }
            Button(
                onClick = onDraw,
                enabled = !drawing && participants.size >= SecretSanta.MIN_PARTICIPANTS,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (drawing) "Tirage en cours…" else "Tirer au sort")
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text("Modifier le tirage")
            }
        }
    }
    item {
        Text(
            "Participants (${participants.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    items(participants, key = { it.id }) { participant ->
        ListItem(
            headlineContent = { Text(participant.name) },
            supportingContent = { Text(PhoneNumbers.label(participant.phoneE164)) },
            leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
        )
        HorizontalDivider()
    }
}

@Composable
private fun ResultSummary(participants: List<Participant>) {
    val withPhone = participants.filter { it.phoneE164 != null }
    val withoutPhone = participants.filter { it.phoneE164 == null }
    val lines = buildList {
        if (withPhone.isNotEmpty()) {
            val sent = withPhone.count { it.status == DeliveryStatus.SENT || it.status == DeliveryStatus.DELIVERED }
            val failed = withPhone.count { it.status == DeliveryStatus.FAILED }
            add(
                "SMS envoyés : $sent / ${withPhone.size}" +
                    if (failed > 0) " ($failed en échec)" else "",
            )
        }
        if (withoutPhone.isNotEmpty()) {
            add("Résultats montrés : ${withoutPhone.count { it.status == DeliveryStatus.SHOWN }} / ${withoutPhone.size}")
        }
    }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { Text(it, style = MaterialTheme.typography.titleSmall) }
        Text(
            "Les SMS envoyés apparaissent dans ta messagerie : si tu participes, ne les ouvre pas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultRow(
    participant: Participant,
    receiverName: String?,
    onResend: () -> Unit,
    onShow: () -> Unit,
) {
    val status = participant.status
    ListItem(
        headlineContent = {
            Text(if (receiverName != null) "${participant.name} → $receiverName" else participant.name)
        },
        supportingContent = {
            Text(
                statusLabel(participant),
                color = if (status == DeliveryStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
        trailingContent = {
            if (participant.phoneE164 == null) {
                TextButton(onClick = onShow) { Text("Voir pour ${participant.name}") }
            } else if (status != DeliveryStatus.SENDING) {
                IconButton(onClick = onResend) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer le SMS de ${participant.name}")
                }
            }
        },
    )
}

private fun statusLabel(participant: Participant): String =
    when (participant.status) {
        DeliveryStatus.PENDING -> "SMS à envoyer"
        DeliveryStatus.SENDING -> "Envoi en cours…"
        DeliveryStatus.SENT -> "SMS envoyé"
        DeliveryStatus.DELIVERED -> "SMS distribué"
        DeliveryStatus.FAILED -> "Échec : ${participant.statusError ?: "raison inconnue"}"
        DeliveryStatus.TO_SHOW -> "Pas de téléphone : résultat à lui montrer"
        DeliveryStatus.SHOWN -> "Pas de téléphone : résultat montré"
        null -> ""
    }

@Composable
private fun SendDialog(
    title: String,
    sims: List<Sim>,
    initialSubscriptionId: Int?,
    onConfirm: (subscriptionId: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var subscriptionId by remember {
        mutableStateOf(sims.firstOrNull { it.subscriptionId == initialSubscriptionId }?.subscriptionId ?: sims.firstOrNull()?.subscriptionId)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Chaque SMS indique à son destinataire à qui il offre un cadeau. " +
                        "Ils apparaîtront aussi dans ta messagerie : si tu participes, ne les ouvre pas.",
                )
                if (sims.size >= 2) {
                    Text("Envoyer avec :", style = MaterialTheme.typography.titleSmall)
                    sims.forEach { sim ->
                        RadioRow(
                            label = sim.label,
                            selected = subscriptionId == sim.subscriptionId,
                            onClick = { subscriptionId = sim.subscriptionId },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (sims.size >= 2) subscriptionId else null) }) { Text("Envoyer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * Résultat d'un participant sans téléphone (SPEC.md §5.9) : compte à rebours de 5 secondes,
 * annulable, pour tendre le téléphone sans voir le résultat ; puis le nom du receveur.
 */
@Composable
private fun ShowResultDialog(
    giverName: String,
    receiverName: () -> String?,
    onShown: () -> Unit,
    onClose: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(COUNTDOWN_SECONDS) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
        visible = true
        onShown()
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!visible) {
                    Text("Pour $giverName", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Text("$remaining", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Tends le téléphone à $giverName.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(32.dp))
                    OutlinedButton(onClick = onClose) { Text("Annuler") }
                } else {
                    Text("$giverName,", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("tu offres un cadeau à", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        receiverName() ?: "?",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Chut, c'est un secret !", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = onClose) { Text("Masquer") }
                }
            }
        }
    }
}
