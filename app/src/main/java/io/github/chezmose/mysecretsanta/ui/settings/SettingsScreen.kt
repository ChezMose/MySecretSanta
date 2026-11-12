package io.github.chezmose.mysecretsanta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.chezmose.mysecretsanta.SantaApp
import io.github.chezmose.mysecretsanta.message.MessageTemplate
import io.github.chezmose.mysecretsanta.ui.common.MessageEditor
import io.github.chezmose.mysecretsanta.util.nextChristmasEve

/** Réglages (écran 9) : message par défaut des nouveaux tirages, version de l'app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(version: String, onBack: () -> Unit) {
    val settings = (LocalContext.current.applicationContext as SantaApp).settings
    var template by rememberSaveable { mutableStateOf(settings.defaultMessageTemplate) }
    var error by remember { mutableStateOf<String?>(null) }
    val exampleDate = remember { nextChristmasEve() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
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
            Text("Message par défaut", style = MaterialTheme.typography.titleMedium)
            Text(
                "Copié dans chaque nouveau tirage, où tu peux encore l'adapter. " +
                    "Les tirages existants gardent leur message.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MessageEditor(
                template = template,
                onTemplateChange = {
                    template = it
                    error = null
                },
                defaultTemplate = MessageTemplate.DEFAULT,
                drawName = "Noël ${exampleDate.year}",
                budget = "30 €",
                date = exampleDate,
                longestName = "Alice",
                error = error,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    error = MessageTemplate.validate(template)
                    if (error == null) {
                        settings.defaultMessageTemplate = template
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enregistrer")
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Text(
                "MySecretSanta, version $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
