package io.github.chezmose.mysecretsanta.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.chezmose.mysecretsanta.message.Gsm
import io.github.chezmose.mysecretsanta.message.MessageTemplate

/**
 * Édition du modèle de message, avec un aperçu sur des noms fictifs (jamais le vrai tirage)
 * et la longueur du message le plus long. La saisie est convertie en alphabet SMS au fil de l'eau
 * (ê → e, émojis retirés) : le message tient toujours en un seul SMS.
 * @param longestName le plus long nom de participant, pour calculer le pire cas.
 */
@Composable
fun MessageEditor(
    template: String,
    onTemplateChange: (String) -> Unit,
    defaultTemplate: String,
    drawName: String,
    budget: String?,
    longestName: String,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = template,
            onValueChange = { onTemplateChange(Gsm.sanitize(it)) },
            label = { Text("Message") },
            isError = error != null,
            supportingText = {
                Text(error ?: ("Variables : " + MessageTemplate.VARIABLES.joinToString(", ")))
            },
            minLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (template != defaultTemplate) {
            TextButton(onClick = { onTemplateChange(defaultTemplate) }) {
                Text("Rétablir le message par défaut")
            }
        }

        Text("Aperçu, avec des noms fictifs", style = MaterialTheme.typography.titleSmall)
        val name = drawName.ifBlank { "Noël" }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                MessageTemplate.render(template, "Alice", "Bob", name, budget),
                modifier = Modifier.padding(12.dp),
            )
        }

        val length = Gsm.length(MessageTemplate.render(template, longestName, longestName, name, budget))
        val tooLong = length > MessageTemplate.MAX_LENGTH
        Text(
            "$length / ${MessageTemplate.MAX_LENGTH} caractères avec le nom le plus long ($longestName)" +
                if (tooLong) " : trop long pour un seul SMS." else ", soit 1 SMS par participant.",
            style = MaterialTheme.typography.bodySmall,
            color = if (tooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Les émojis et accents spéciaux (ê, ç, « »…) sont remplacés ou retirés, " +
                "y compris dans les noms : « Noël » devient « Noel ».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
