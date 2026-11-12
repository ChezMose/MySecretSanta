package io.github.chezmose.mysecretsanta.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import io.github.chezmose.mysecretsanta.SantaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reçoit les comptes rendus d'envoi et de distribution des SMS envoyés par [SmsSender],
 * et met à jour l'état du participant concerné. Déclaré dans le manifeste, non exporté.
 */
class SmsResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val participantId = intent.getLongExtra(SmsSender.EXTRA_PARTICIPANT_ID, -1)
        if (participantId < 0) return

        val update = when (intent.action) {
            SmsSender.ACTION_SENT -> SmsResults.afterSend(resultCode)
            SmsSender.ACTION_DELIVERED -> SmsResults.afterDelivery(tpStatus(intent))
            else -> null
        } ?: return

        val pending = goAsync()
        val dao = (context.applicationContext as SantaApp).database.drawDao()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dao.updateStatus(participantId, update.first, update.second, System.currentTimeMillis())
            } finally {
                pending.finish()
            }
        }
    }

    private fun tpStatus(intent: Intent): Int? {
        val pdu = intent.getByteArrayExtra("pdu") ?: return null
        val format = intent.getStringExtra("format")
        return try {
            SmsMessage.createFromPdu(pdu, format)?.status
        } catch (_: RuntimeException) {
            null
        }
    }
}
