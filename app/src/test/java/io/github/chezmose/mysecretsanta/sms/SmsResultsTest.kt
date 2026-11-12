package io.github.chezmose.mysecretsanta.sms

import android.app.Activity
import android.telephony.SmsManager
import io.github.chezmose.mysecretsanta.data.DeliveryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsResultsTest {

    @Test
    fun `compte rendu d'envoi`() {
        assertEquals(DeliveryStatus.SENT to null, SmsResults.afterSend(Activity.RESULT_OK))
        assertEquals(DeliveryStatus.FAILED to "pas de réseau", SmsResults.afterSend(SmsManager.RESULT_ERROR_NO_SERVICE))
        assertEquals(
            DeliveryStatus.FAILED to "réseau mobile coupé (mode avion ?)",
            SmsResults.afterSend(SmsManager.RESULT_ERROR_RADIO_OFF),
        )
        assertEquals(DeliveryStatus.FAILED to "échec de l'envoi (code 999)", SmsResults.afterSend(999))
    }

    @Test
    fun `accusé de réception`() {
        assertNull(SmsResults.afterDelivery(null))
        assertEquals(DeliveryStatus.DELIVERED to null, SmsResults.afterDelivery(0x00))
        assertNull(SmsResults.afterDelivery(0x20)) // en cours : on attend
        assertEquals(DeliveryStatus.FAILED, SmsResults.afterDelivery(0x40)?.first)
    }
}
