package com.healthapp.presentation.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

enum class ArtworkKind { GARDEN, FOOD, MIND }

/** Small decorative illustrations, drawn to sit on gradient (dark) cards. */
@Composable
fun WellnessArtwork(kind: ArtworkKind, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = minOf(w, h)
        val center = Offset(w * .5f, h * .5f)
        drawCircle(Color.White.copy(alpha = .14f), s * .48f, center)
        drawCircle(Color.White.copy(alpha = .22f), s * .33f, Offset(w * .55f, h * .52f))

        when (kind) {
            ArtworkKind.GARDEN -> {
                drawCircle(Color(0xFFFFD43B), s * .13f, Offset(w * .64f, h * .30f))
                drawLine(Color.White, Offset(w * .48f, h * .86f), Offset(w * .48f, h * .42f), s * .028f)
                val leaves = listOf(
                    Triple(Triple(.31f, .56f, -35f), Color(0xFF63E6BE), 0),
                    Triple(Triple(.65f, .52f, 35f), Color(0xFFFF8787), 0),
                    Triple(Triple(.32f, .75f, -35f), Color(0xFF74C0FC), 0),
                    Triple(Triple(.63f, .70f, 35f), Color(0xFFFFA94D), 0)
                )
                leaves.forEach { (pos, color, _) ->
                    val (x, y, angle) = pos
                    rotate(angle, Offset(w * x, h * y)) {
                        drawOval(color, Offset(w * x - s * .12f, h * y - s * .055f), Size(s * .24f, s * .11f))
                    }
                }
            }
            ArtworkKind.FOOD -> {
                val bowl = Path().apply {
                    moveTo(w * .21f, h * .52f)
                    quadraticBezierTo(w * .27f, h * .9f, w * .5f, h * .91f)
                    quadraticBezierTo(w * .73f, h * .9f, w * .79f, h * .52f)
                    close()
                }
                drawPath(bowl, Color.White)
                drawOval(Color(0xFFFFE8CC), Offset(w * .2f, h * .46f), Size(w * .6f, h * .15f))
                drawCircle(Color(0xFFFF8787), s * .1f, Offset(w * .36f, h * .42f))
                drawCircle(Color(0xFFFFD43B), s * .09f, Offset(w * .59f, h * .39f))
                drawCircle(Color(0xFF63E6BE), s * .1f, Offset(w * .50f, h * .50f))
            }
            ArtworkKind.MIND -> {
                drawCircle(Color.White, s * .22f, Offset(w * .5f, h * .52f))
                val ink = Color(0xFFD6336C)
                drawCircle(ink, s * .016f, Offset(w * .43f, h * .49f))
                drawCircle(ink, s * .016f, Offset(w * .57f, h * .49f))
                val smile = Path().apply {
                    moveTo(w * .43f, h * .58f)
                    quadraticBezierTo(w * .5f, h * .67f, w * .57f, h * .58f)
                }
                drawPath(smile, ink, style = Stroke(s * .022f))
                drawCircle(Color(0xFFFFD43B), s * .055f, Offset(w * .22f, h * .3f))
                drawCircle(Color(0xFF74C0FC), s * .045f, Offset(w * .79f, h * .73f))
            }
        }
    }
}
