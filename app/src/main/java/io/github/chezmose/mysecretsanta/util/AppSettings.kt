package io.github.chezmose.mysecretsanta.util

import android.content.Context
import io.github.chezmose.mysecretsanta.message.MessageTemplate

/** Réglages de l'app, stockés sur le téléphone (non sauvegardés dans le cloud : allowBackup=false). */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Modèle copié dans chaque nouveau tirage (SPEC.md §5.7). */
    var defaultMessageTemplate: String
        get() = prefs.getString(KEY_TEMPLATE, null) ?: MessageTemplate.DEFAULT
        set(value) {
            prefs.edit().putString(KEY_TEMPLATE, value).apply()
        }

    private companion object {
        // « _v2 » : les modèles enregistrés avant le passage à un seul SMS sans date sont ignorés.
        const val KEY_TEMPLATE = "default_message_template_v2"
    }
}
