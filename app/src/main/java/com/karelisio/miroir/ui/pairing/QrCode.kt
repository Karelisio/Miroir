package com.karelisio.miroir.ui.pairing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR code dessiné en points arrondis, avec repères de position adoucis. Toujours sombre sur clair. */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier, dark: Color = Color(0xFF1D1B20), accent: Color = dark) {
    val matrix = remember(content) {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
    }
    Canvas(modifier.aspectRatio(1f)) {
        val n = matrix.width
        val cell = size.width / n
        fun inFinder(x: Int, y: Int) = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
        for (y in 0 until n) for (x in 0 until n) {
            if (matrix[x, y] && !inFinder(x, y)) {
                val inset = cell * 0.08f
                drawRoundRect(
                    color = dark,
                    topLeft = Offset(x * cell + inset, y * cell + inset),
                    size = Size(cell - 2 * inset, cell - 2 * inset),
                    cornerRadius = CornerRadius(cell * 0.45f),
                )
            }
        }
        finder(0f, 0f, cell, accent)
        finder((n - 7) * cell, 0f, cell, accent)
        finder(0f, (n - 7) * cell, cell, accent)
    }
}

private fun DrawScope.finder(x: Float, y: Float, cell: Float, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x + cell / 2, y + cell / 2),
        size = Size(cell * 6, cell * 6),
        cornerRadius = CornerRadius(cell * 2),
        style = Stroke(width = cell),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(x + cell * 2, y + cell * 2),
        size = Size(cell * 3, cell * 3),
        cornerRadius = CornerRadius(cell * 1.1f),
    )
}
