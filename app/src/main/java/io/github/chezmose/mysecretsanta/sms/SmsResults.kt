package io.github.chezmose.mysecretsanta.sms

import android.app.Activity
import android.telephony.SmsManager
import io.github.chezmose.mysecretsanta.data.DeliveryStatus

/** Traduction des comptes rendus d'envoi et de distribution d'Android (SPEC.md §5.6). */
object SmsResults {

    /** Compte rendu d'envoi : `resultCode` reçu par le récepteur de l'intention « envoyé ». */
    fun afterSend(resultCode: Int): Pair<DeliveryStatus, String?> =
        if (resultCode == Activity.RESULT_OK) {
            DeliveryStatus.SENT to null
        } else {
            DeliveryStatus.FAILED to sendError(resultCode)
        }

    fun sendError(resultCode: Int): String =
        when (resultCode) {
            SmsManager.RESULT_ERROR_RADIO_OFF -> "réseau mobile coupé (mode avion ?)"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "pas de réseau"
            SmsManager.RESULT_ERROR_NULL_PDU -> "message invalide"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "limite d'envoi de SMS atteinte, réessaie plus tard"
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED,
            -> "envoi refusé par le téléphone"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "échec de l'envoi"
            else -> "échec de l'envoi (code $resultCode)"
        }

    /**
     * Accusé de réception : statut TP-Status du rapport de l'opérateur.
     * 0x00–0x1F : distribué ; 0x20–0x3F : en cours, on attend ; 0x40 et plus : échec définitif.
     * Renvoie `null` quand il n'y a rien à changer.
     */
    fun afterDelivery(tpStatus: Int?): Pair<DeliveryStatus, String?>? =
        when {
            tpStatus == null -> null
            tpStatus < 0x20 -> DeliveryStatus.DELIVERED to null
            tpStatus < 0x40 -> null
            else -> DeliveryStatus.FAILED to "non distribué par l'opérateur"
        }
}
