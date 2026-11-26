// Icon paths are coordinates on a 24 dp grid; naming every coordinate adds nothing.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The project's own monochrome line icons (`tokens.md` 8): 1.75 dp stroke on a 24 dp grid, round caps; filled
 * variants for selected navigation items. Drawn for NightBaseline, no vendor or third-party icon art. Tinted by the
 * caller (`Icon(tint = ...)`).
 */
object NbIcons {
    private const val STROKE = 1.75f

    private fun icon(
        name: String,
        autoMirror: Boolean = false,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
                autoMirror = autoMirror,
            ).apply(block)
            .build()

    private fun ImageVector.Builder.line(pathBuilder: PathBuilder.() -> Unit) =
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )

    private fun ImageVector.Builder.fill(pathBuilder: PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.Black), pathBuilder = pathBuilder)

    private fun PathBuilder.addCircle(
        cx: Float,
        cy: Float,
        r: Float,
    ) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }

    /** Last night (outline): crescent moon. */
    val Night: ImageVector =
        icon("night") {
            line {
                moveTo(14.5f, 3.5f)
                arcTo(8.5f, 8.5f, 0f, true, false, 20.5f, 15f)
                arcTo(6.5f, 6.5f, 0f, false, true, 14.5f, 3.5f)
                close()
            }
        }

    /** Last night (selected): filled crescent. */
    val NightFilled: ImageVector =
        icon("night_filled") {
            fill {
                moveTo(14.5f, 3.5f)
                arcTo(8.5f, 8.5f, 0f, true, false, 20.5f, 15f)
                arcTo(6.5f, 6.5f, 0f, false, true, 14.5f, 3.5f)
                close()
            }
        }

    /** History (outline): calendar grid. */
    val History: ImageVector =
        icon("history") {
            line {
                moveTo(5f, 5.5f)
                horizontalLineTo(19f)
                arcTo(1.5f, 1.5f, 0f, false, true, 20.5f, 7f)
                verticalLineTo(19f)
                arcTo(1.5f, 1.5f, 0f, false, true, 19f, 20.5f)
                horizontalLineTo(5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 3.5f, 19f)
                verticalLineTo(7f)
                arcTo(1.5f, 1.5f, 0f, false, true, 5f, 5.5f)
                close()
                moveTo(3.5f, 10f)
                horizontalLineTo(20.5f)
                moveTo(8f, 3.5f)
                verticalLineTo(7f)
                moveTo(16f, 3.5f)
                verticalLineTo(7f)
            }
        }

    /** History (selected). */
    val HistoryFilled: ImageVector =
        icon("history_filled") {
            fill {
                moveTo(5f, 5f)
                horizontalLineTo(19f)
                arcTo(2f, 2f, 0f, false, true, 21f, 7f)
                verticalLineTo(9.25f)
                horizontalLineTo(3f)
                verticalLineTo(7f)
                arcTo(2f, 2f, 0f, false, true, 5f, 5f)
                close()
                moveTo(3f, 10.75f)
                horizontalLineTo(21f)
                verticalLineTo(19f)
                arcTo(2f, 2f, 0f, false, true, 19f, 21f)
                horizontalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 3f, 19f)
                close()
            }
            line {
                moveTo(8f, 3f)
                verticalLineTo(6f)
                moveTo(16f, 3f)
                verticalLineTo(6f)
            }
        }

    /** Settings (outline): three sliders. */
    val Settings: ImageVector =
        icon("settings") {
            line {
                moveTo(4f, 7f)
                horizontalLineTo(20f)
                moveTo(4f, 12f)
                horizontalLineTo(20f)
                moveTo(4f, 17f)
                horizontalLineTo(20f)
            }
            line {
                moveTo(9f, 5f)
                verticalLineTo(9f)
                moveTo(15f, 10f)
                verticalLineTo(14f)
                moveTo(7f, 15f)
                verticalLineTo(19f)
            }
        }

    /** Settings (selected): sliders with filled knobs. */
    val SettingsFilled: ImageVector =
        icon("settings_filled") {
            line {
                moveTo(4f, 7f)
                horizontalLineTo(20f)
                moveTo(4f, 12f)
                horizontalLineTo(20f)
                moveTo(4f, 17f)
                horizontalLineTo(20f)
            }
            fill {
                addCircle(9f, 7f, 2.5f)
                addCircle(15f, 12f, 2.5f)
                addCircle(7f, 17f, 2.5f)
            }
        }

    /** Back chevron (mirrored in RTL by the caller with `autoMirror`). */
    val Back: ImageVector =
        icon("back", autoMirror = true) {
            line {
                moveTo(15f, 5f)
                lineTo(8f, 12f)
                lineTo(15f, 19f)
            }
        }

    /**
     * Back arrow of the top bar's round back button (`coloros-conformance.md` C1: an arrow, not a chevron); mirrored
     * in RTL. Drawn at 20 dp it has a 1.5 dp stroke.
     */
    val ArrowBack: ImageVector =
        icon("arrow_back", autoMirror = true) {
            line {
                moveTo(20f, 12f)
                horizontalLineTo(4.5f)
                moveTo(11f, 5f)
                lineTo(4f, 12f)
                lineTo(11f, 19f)
            }
        }

    /**
     * Small list chevron (`coloros-conformance.md` L3): draw at 12 x 24 dp; the glyph is about 5 x 10 dp with a
     * 1.3 dp stroke. The viewport is half as wide as tall.
     */
    val ChevronSmall: ImageVector =
        ImageVector
            .Builder(
                name = "chevron_small",
                defaultWidth = 12.dp,
                defaultHeight = 24.dp,
                viewportWidth = 12f,
                viewportHeight = 24f,
                autoMirror = true,
            ).apply {
                path(
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.3f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(3.5f, 7f)
                    lineTo(8.5f, 12f)
                    lineTo(3.5f, 17f)
                }
            }.build()

    /** Forward chevron. */
    val Chevron: ImageVector =
        icon("chevron", autoMirror = true) {
            line {
                moveTo(9f, 5f)
                lineTo(16f, 12f)
                lineTo(9f, 19f)
            }
        }

    /** Info (circle with i). */
    val Info: ImageVector =
        icon("info") {
            line {
                addCircle(12f, 12f, 8.5f)
                moveTo(12f, 11f)
                verticalLineTo(16.5f)
            }
            fill { addCircle(12f, 7.75f, 1.1f) }
        }

    /** Close (x). */
    val Close: ImageVector =
        icon("close") {
            line {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }

    /** More, vertical (three dots stacked), as on ColorOS toolbars. */
    val MoreVertical: ImageVector =
        icon("more_vertical") {
            fill {
                addCircle(12f, 5.5f, 1.7f)
                addCircle(12f, 12f, 1.7f)
                addCircle(12f, 18.5f, 1.7f)
            }
        }

    /** More (three dots). */
    val More: ImageVector =
        icon("more") {
            fill {
                addCircle(6f, 12f, 1.6f)
                addCircle(12f, 12f, 1.6f)
                addCircle(18f, 12f, 1.6f)
            }
        }

    /** Key outline (permission problems). */
    val Key: ImageVector =
        icon("key") {
            line {
                addCircle(8f, 12f, 4f)
                moveTo(12f, 12f)
                horizontalLineTo(20.5f)
                moveTo(17f, 12f)
                verticalLineTo(15f)
                moveTo(20f, 12f)
                verticalLineTo(14.5f)
            }
        }

    /** Cloud with clock (source has not synced). */
    val CloudClock: ImageVector =
        icon("cloud_clock") {
            line {
                moveTo(7f, 18f)
                horizontalLineTo(6.5f)
                arcTo(4f, 4f, 0f, false, true, 6.2f, 10f)
                arcTo(6f, 6f, 0f, false, true, 17.5f, 9.5f)
                arcTo(3.5f, 3.5f, 0f, false, true, 19.8f, 13f)
            }
            line {
                addCircle(15f, 17f, 4f)
                moveTo(15f, 15f)
                verticalLineTo(17f)
                lineTo(16.5f, 18f)
            }
        }

    /** Cloud (source quiet). */
    val Cloud: ImageVector =
        icon("cloud") {
            line {
                moveTo(7f, 18.5f)
                horizontalLineTo(17.5f)
                arcTo(3.75f, 3.75f, 0f, false, false, 17.5f, 11f)
                arcTo(6f, 6f, 0f, false, false, 6.2f, 10.5f)
                arcTo(4f, 4f, 0f, false, false, 7f, 18.5f)
                close()
            }
        }

    /** Download arrow (Health Connect update). */
    val Download: ImageVector =
        icon("download") {
            line {
                moveTo(12f, 4f)
                verticalLineTo(15f)
                moveTo(7.5f, 10.5f)
                lineTo(12f, 15f)
                lineTo(16.5f, 10.5f)
                moveTo(5f, 19.5f)
                horizontalLineTo(19f)
            }
        }

    /** Moon outline (waiting for last night). */
    val Moon: ImageVector = Night

    /** Bell with slash (notifications off). */
    val BellOff: ImageVector =
        icon("bell_off") {
            line {
                moveTo(6.5f, 16.5f)
                verticalLineTo(11f)
                arcTo(5.5f, 5.5f, 0f, false, true, 16.6f, 8f)
                moveTo(17.5f, 11.5f)
                verticalLineTo(16.5f)
                horizontalLineTo(8.5f)
                moveTo(5f, 16.5f)
                horizontalLineTo(19f)
                moveTo(10f, 19.5f)
                horizontalLineTo(14f)
                moveTo(4f, 4f)
                lineTo(20f, 20f)
            }
        }

    /** Check mark. */
    val Check: ImageVector =
        icon("check") {
            line {
                moveTo(5f, 12.5f)
                lineTo(10f, 17.5f)
                lineTo(19f, 7f)
            }
        }
}
