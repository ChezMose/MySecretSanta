package io.github.chezmose.mysecretsanta.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/** Une carte SIM active, pour choisir celle qui envoie les SMS. */
data class Sim(val subscriptionId: Int, val label: String)

/**
 * Envoi direct des SMS avec [SmsManager] (SPEC.md §5.6). Les comptes rendus d'envoi et de
 * distribution arrivent dans [SmsResultReceiver].
 */
class SmsSender(context: Context) {

    private val context = context.applicationContext

    fun hasSendPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * Les cartes SIM actives. Liste vide si la permission de lire l'état du téléphone est refusée :
     * le SMS part alors par la SIM par défaut.
     */
    fun activeSims(): List<Sim> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        return try {
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList
                .orEmpty()
                .map { Sim(it.subscriptionId, "SIM ${it.simSlotIndex + 1} : ${it.displayName}") }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /**
     * Confie le SMS au téléphone. Lève une exception si l'envoi est refusé d'emblée
     * (permission retirée, numéro invalide…).
     * @param subscriptionId la SIM à utiliser, ou `null` pour la SIM par défaut.
     */
    fun send(participantId: Long, phoneE164: String, text: String, subscriptionId: Int?) {
        val manager = smsManager(subscriptionId)
        val parts = manager.divideMessage(text)
        val sent = ArrayList(parts.indices.map { resultIntent(ACTION_SENT, participantId, it) })
        val delivered = ArrayList(parts.indices.map { resultIntent(ACTION_DELIVERED, participantId, it) })
        manager.sendMultipartTextMessage(phoneE164, null, parts, sent, delivered)
    }

    private fun smsManager(subscriptionId: Int?): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(SmsManager::class.java)
            if (subscriptionId != null) manager.createForSubscriptionId(subscriptionId) else manager
        } else {
            @Suppress("DEPRECATION")
            if (subscriptionId != null) SmsManager.getSmsManagerForSubscriptionId(subscriptionId) else SmsManager.getDefault()
        }

    private fun resultIntent(action: String, participantId: Long, part: Int): PendingIntent {
        val intent = Intent(context, SmsResultReceiver::class.java)
            .setAction(action)
            // L'URI rend chaque intention unique : sans elle, Android réutiliserait la même pour tous.
            .setData(Uri.parse("mysecretsanta://sms/$action/$participantId/$part"))
            .putExtra(EXTRA_PARTICIPANT_ID, participantId)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val ACTION_SENT = "io.github.chezmose.mysecretsanta.SMS_SENT"
        const val ACTION_DELIVERED = "io.github.chezmose.mysecretsanta.SMS_DELIVERED"
        const val EXTRA_PARTICIPANT_ID = "participantId"
    }
}
