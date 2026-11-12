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
import io.github.chezmose.mysecretsanta.message.MessageTemplate
import io.github.chezmose.mysecretsanta.message.SmsSegments
import java.time.LocalDate

/**
 * Édition du modèle de message, avec un aperçu sur des noms fictifs (jamais le vrai tirage)
 * et le nombre de SMS estimé.
 * @param longestName le plus long nom de participant, pour estimer le pire cas.
 */
@Composable
fun MessageEditor(
    template: String,
    onTemplateChange: (String) -> Unit,
    defaultTemplate: String,
    drawName: String,
    budget: String?,
    date: LocalDate?,
    longestName: String,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = template,
            onValueChange = onTemplateChange,
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
                MessageTemplate.render(template, "Alice", "Bob", name, budget, date),
                modifier = Modifier.padding(12.dp),
            )
        }

        val worstCase = MessageTemplate.render(template, longestName, longestName, name, budget, date)
        Text(
            smsCountLabel(SmsSegments.estimate(worstCase)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun smsCountLabel(estimate: SmsSegments.Estimate): String {
    val count = if (estimate.segments <= 1) {
        "1 SMS par participant"
    } else {
        "Jusqu'à ${estimate.segments} SMS par participant"
    }
    if (!estimate.unicode) return "$count."
    val chars = estimate.unicodeCharacters.take(5).joinToString(" ")
    return "$count. Message en Unicode (70 caractères par SMS au lieu de 160), à cause de : $chars"
}
