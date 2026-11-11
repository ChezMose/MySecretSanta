package io.github.chezmose.mysecretsanta.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor

/**
 * QR code dessiné par l'app, sans accès réseau.
 * Toujours en noir sur fond clair : les lecteurs de QR code lisent mal les couleurs inversées.
 * La marge blanche autour du code (zone de silence) est à la charge de l'appelant.
 */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier, color: Color = Color.Black) {
    val matrix = remember(content) {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(
                EncodeHintType.MARGIN to 0,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            ),
        )
    }
    Canvas(modifier) {
        val modules = matrix.width
        // Taille de module entière, pour éviter les fines lignes entre les carrés.
        val cell = floor(size.minDimension / modules)
        val origin = Offset((size.width - cell * modules) / 2, (size.height - cell * modules) / 2)
        for (y in 0 until modules) {
            for (x in 0 until modules) {
                if (matrix[x, y]) {
                    drawRect(
                        color = color,
                        topLeft = Offset(origin.x + x * cell, origin.y + y * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }
}
