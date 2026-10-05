package com.example.gasuschedule.presentation.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Иконки нижней панели: контур на сетке 24, у активной вкладки — тональная заливка. */
internal enum class NavIconKind { HOME, SCHEDULE, HOMEWORK, SETTINGS }

private const val STROKE_WIDTH = 1.8f

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

/**
 * @param color цвет контура
 * @param fill заливка фигур (прозрачная у неактивной вкладки)
 * @param hole цвет "отверстий" — кружков ползунков и скрепки; у неактивной — цвет панели
 */
@Composable
internal fun NavIcon(kind: NavIconKind, color: Color, fill: Color, hole: Color, modifier: Modifier = Modifier) {
    val paths = remember(kind) {
        when (kind) {
            NavIconKind.HOME -> listOf(path("M4 11.2 12 4l8 7.2V19a1.2 1.2 0 0 1-1.2 1.2H15v-5.2a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v5.2H5.2A1.2 1.2 0 0 1 4 19z"))
            NavIconKind.SCHEDULE -> listOf(path("M3.8 10.2h16.4M8 3v4M16 3v4"))
            NavIconKind.HOMEWORK -> listOf(
                path("M9.2 4.2V3.6a.8.8 0 0 1 .8-.8h4a.8.8 0 0 1 .8.8v.6"),
                path("m8.8 13 2.3 2.3 4.2-4.5"),
            )
            NavIconKind.SETTINGS -> listOf(path("M4 8h8.2M17.8 8H20M4 16h2.2M11.8 16H20"))
        }
    }
    val stroke = Stroke(width = STROKE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round)
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.width / 24f, pivot = Offset.Zero) {
            when (kind) {
                NavIconKind.HOME -> shape(paths[0], color, fill, stroke)
                NavIconKind.SCHEDULE -> {
                    box(3.8f, 5f, 16.4f, 15.2f, 3.2f, color, fill, stroke)
                    drawPath(paths[0], color, style = stroke)
                    // Ячейка "сегодня".
                    drawRoundRect(color, Offset(13f, 13.2f), Size(3.6f, 3.6f), CornerRadius(1f))
                }
                NavIconKind.HOMEWORK -> {
                    box(4.8f, 4.2f, 14.4f, 16.6f, 3f, color, fill, stroke)
                    shape(paths[0], color, hole, stroke)
                    drawPath(paths[1], color, style = stroke)
                }
                NavIconKind.SETTINGS -> {
                    drawPath(paths[0], color, style = stroke)
                    for (c in listOf(Offset(15f, 8f), Offset(9f, 16f))) {
                        drawCircle(hole, radius = 2.8f, center = c)
                        drawCircle(color, radius = 2.8f, center = c, style = stroke)
                    }
                }
            }
        }
    }
}

private fun DrawScope.shape(p: Path, color: Color, fill: Color, stroke: Stroke) {
    drawPath(p, fill)
    drawPath(p, color, style = stroke)
}

private fun DrawScope.box(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, fill: Color, stroke: Stroke) {
    drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(r))
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r), style = stroke)
}
