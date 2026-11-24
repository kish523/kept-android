package app.kept.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import app.kept.core.parser.model.Category

/**
 * Kept's line icons, drawn on a 24-unit grid with round 1.8 strokes (the same set as the design prototype).
 * `Icon()` tints them with the current content colour.
 */
object KeptIcons {
    private fun icon(name: String, vararg paths: String, stroke: Float = 1.8f, filled: String? = null): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            filled?.let { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()

    val Search by lazy { icon("search", "M18,11a7,7 0,1 1,-14 0a7,7 0,1 1,14 0", "M20,20l-3.5,-3.5") }
    val Settings by lazy {
        icon("settings", "M4,7h9M17,7h3M4,17h3M11,17h9", "M17,7a2,2 0,1 1,-4 0a2,2 0,1 1,4 0", "M11,17a2,2 0,1 1,-4 0a2,2 0,1 1,4 0")
    }
    val Moon by lazy { icon("moon", "M20,14.5A8,8 0,0 1,9.5 4a8,8 0,1 0,10.5 10.5z") }
    val Sun by lazy {
        icon(
            "sun",
            "M16,12a4,4 0,1 1,-8 0a4,4 0,1 1,8 0",
            "M12,2.5v2M12,19.5v2M4.6,4.6L6,6M18,18l1.4,1.4M2.5,12h2M19.5,12h2M4.6,19.4L6,18M18,6l1.4,-1.4",
        )
    }
    val Scan by lazy {
        icon(
            "scan",
            "M4,8V6.5A2.5,2.5 0,0 1,6.5 4H8M16,4h1.5A2.5,2.5 0,0 1,20 6.5V8M20,16v1.5a2.5,2.5 0,0 1,-2.5 2.5H16M8,20H6.5A2.5,2.5 0,0 1,4 17.5V16M8,12h8",
            stroke = 2f,
        )
    }
    val Check by lazy { icon("check", "M5,12.5l4.5,4.5L19,7.5", stroke = 2.4f) }
    val Close by lazy { icon("close", "M7,7l10,10M17,7L7,17", stroke = 2f) }
    val Back by lazy { icon("back", "M15,18l-6,-6 6,-6", stroke = 2f) }
    val ChevronDown by lazy { icon("chevron_down", "M6,9l6,6 6,-6") }
    val Warning by lazy { icon("warning", "M12,3.5L2.8,19.5h18.4L12,3.5z", "M12,10v4M12,17v0.01", stroke = 2f) }
    val ReturnArrow by lazy { icon("return", "M9,14L4,9l5,-5", "M4,9h10.5a5.5,5.5 0,0 1,0 11H11") }
    val Shield by lazy { icon("shield", "M12,3L5,6v5c0,4.5 3,8 7,10 4,-2 7,-5.5 7,-10V6l-7,-3z", "M9,12l2,2 4,-4") }
    val Bell by lazy { icon("bell", "M6,16v-5a6,6 0,0 1,12 0v5l1.5,2h-15L6,16z", "M10,20.5a2,2 0,0 0,4 0") }
    val Calendar by lazy {
        icon("calendar", "M6,5h12a2.5,2.5 0,0 1,2.5 2.5v10.5a2.5,2.5 0,0 1,-2.5 2.5H6a2.5,2.5 0,0 1,-2.5 -2.5V7.5A2.5,2.5 0,0 1,6 5z", "M3.5,10h17M8,3v4M16,3v4")
    }

    private val Headphones by lazy {
        icon(
            "headphones",
            "M4,15v-3a8,8 0,0 1,16 0v3",
            "M4.6,14h1.3a1.6,1.6 0,0 1,1.6 1.6v3.3a1.6,1.6 0,0 1,-1.6 1.6H4.6A1.6,1.6 0,0 1,3 18.9v-3.3A1.6,1.6 0,0 1,4.6 14z",
            "M18.1,14h1.3a1.6,1.6 0,0 1,1.6 1.6v3.3a1.6,1.6 0,0 1,-1.6 1.6h-1.3a1.6,1.6 0,0 1,-1.6 -1.6v-3.3a1.6,1.6 0,0 1,1.6 -1.6z",
        )
    }
    private val Shirt by lazy { icon("shirt", "M8,4L4,6.5 5.5,11l2,-1v10h9V10l2,1L20,6.5 16,4c-0.5,1.5 -2,2.5 -4,2.5S8.5,5.5 8,4z") }
    private val Pot by lazy { icon("pot", "M4.5,10h15v5.5a4.5,4.5 0,0 1,-4.5 4.5H9a4.5,4.5 0,0 1,-4.5 -4.5V10z", "M2,10h20M9.5,6.5h5") }
    private val Bolt by lazy { icon("bolt", "M13,3L5,13.5h6L10,21l8,-10.5h-6L13,3z") }
    private val Chair by lazy { icon("chair", "M7,3.5h10v8H7z", "M5,11.5h14M7,11.5V21M17,11.5V21M7,16h10") }
    private val Wrench by lazy {
        icon("wrench", "M14.5,6.5a4,4 0,0 0,-5 5L4,17l3,3 5.5,-5.5a4,4 0,0 0,5 -5l-2.5,2.5 -2.5,-0.5 -0.5,-2.5z")
    }
    private val Bag by lazy { icon("bag", "M5,8h14l-1,12H6L5,8z", "M9,8V6a3,3 0,0 1,6 0v2") }
    private val Receipt by lazy { icon("receipt", "M7,3h10v18l-2.5,-1.5L12,21l-2.5,-1.5L7,21z", "M9.5,8h5M9.5,12h5") }

    fun forCategory(c: Category?): ImageVector = when (c) {
        Category.ELECTRONICS -> Headphones
        Category.APPLIANCES -> Bolt
        Category.HOME -> Pot
        Category.FURNITURE -> Chair
        Category.CLOTHING -> Shirt
        Category.TOOLS -> Wrench
        Category.GROCERIES -> Bag
        Category.OTHER, null -> Receipt
    }
}
