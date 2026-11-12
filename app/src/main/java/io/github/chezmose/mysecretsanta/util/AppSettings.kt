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

    /** SIM choisie au dernier envoi, sur un téléphone à plusieurs SIM ; `null` : SIM par défaut. */
    var smsSubscriptionId: Int?
        get() = prefs.getInt(KEY_SUBSCRIPTION, -1).takeIf { it >= 0 }
        set(value) {
            prefs.edit().putInt(KEY_SUBSCRIPTION, value ?: -1).apply()
        }

    private companion object {
        const val KEY_SUBSCRIPTION = "sms_subscription_id"
        // « _v2 » : les modèles enregistrés avant le passage à un seul SMS sans date sont ignorés.
        const val KEY_TEMPLATE = "default_message_template_v2"
    }
}
