package win.enderr.voidray.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke icons on a 24×24 grid, taken from the subscription page's SVGs. */
object Ico {
    private fun svg(vararg paths: String, filled: Boolean = false, stroke: Float = 1.9f): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                for (d in paths) {
                    addPath(
                        pathData = addPathNodes(d),
                        fill = if (filled) SolidColor(Color.Black) else null,
                        stroke = if (filled) null else SolidColor(Color.Black),
                        strokeLineWidth = stroke,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }.build()

    val palette = svg(
        "M12 3 A9 9 0 1 0 12 21 C13 21 13.6 20.3 13.6 19.5 C13.6 19.1 13.4 18.7 13.1 18.4 C12.8 18.1 12.7 17.8 12.7 17.4 C12.7 16.6 13.4 16 14.2 16 L16 16 A5 5 0 0 0 21 11 C21 6.6 17 3 12 3 Z",
        "M8.6 11.5 A1.1 1.1 0 1 1 6.4 11.5 A1.1 1.1 0 1 1 8.6 11.5 Z M12.1 7.5 A1.1 1.1 0 1 1 9.9 7.5 A1.1 1.1 0 1 1 12.1 7.5 Z M17.1 9.5 A1.1 1.1 0 1 1 14.9 9.5 A1.1 1.1 0 1 1 17.1 9.5 Z",
    )
    val moon = svg("M20 14.5 A8.5 8.5 0 0 1 9.5 4 A8.5 8.5 0 1 0 20 14.5 Z")
    val sun = svg(
        "M16.2 12 A4.2 4.2 0 1 1 7.8 12 A4.2 4.2 0 1 1 16.2 12 Z",
        "M12 2 L12 4.4 M12 19.6 L12 22 M2 12 L4.4 12 M19.6 12 L22 12 M4.9 4.9 L6.6 6.6 M17.4 17.4 L19.1 19.1 M19.1 4.9 L17.4 6.6 M6.6 17.4 L4.9 19.1",
    )
    val autoRing = svg("M20 12 A8 8 0 1 1 4 12 A8 8 0 1 1 20 12 Z")
    val autoHalf = svg("M12 4 A8 8 0 0 0 12 20 Z", filled = true)
    val copy = svg("M9 9 L21 9 L21 21 L9 21 Z M5 15 L5 5 A2 2 0 0 1 7 3 L17 3")
    val up = svg("M12 19 L12 5 M5 12 L12 5 L19 12", stroke = 2.2f)
    val down = svg("M12 5 L12 19 M19 12 L12 19 L5 12", stroke = 2.2f)
    val refresh = svg("M20 12 A8 8 0 1 1 17.66 6.34 M20 4 L20 9 L15 9")
    val exit = svg("M15 3 L19 3 A2 2 0 0 1 21 5 L21 19 A2 2 0 0 1 19 21 L15 21 M10 17 L15 12 L10 7 M15 12 L3 12")
    val bolt = svg("M13 2 L4 14 L11 14 L10 22 L19 10 L12 10 Z")
    val power = svg("M12 3 L12 11 M6.3 6.8 A8 8 0 1 0 17.7 6.8", stroke = 2f)
    val arrowRight = svg("M5 12 L19 12 M13 6 L19 12 L13 18")
    val paste = svg("M9 4 L15 4 L15 7 L9 7 Z M15 5 L18 5 A1 1 0 0 1 19 6 L19 20 A1 1 0 0 1 18 21 L6 21 A1 1 0 0 1 5 20 L5 6 A1 1 0 0 1 6 5 L9 5")
}
