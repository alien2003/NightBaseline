// detekt treats only src/test as tests; this is the test source set of the debug variant.
@file:Suppress("FunctionNaming", "MagicNumber")

package io.github.alien2003.nightbaseline.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import io.github.alien2003.nightbaseline.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Renders the adaptive launcher icon (R.mipmap.ic_launcher) on the JVM for design review: the icon under the common
 * launcher masks (circle, squircle, rounded square, teardrop), a 48 dp-equivalent small size, a contact sheet on light,
 * dark and busy wallpapers next to two neutral placeholder icons, and the monochrome layer tinted like a themed icon.
 * Every layer is drawn by the test itself (108 dp canvas, the visible 72 dp window centred), then masked with an
 * antialiased path, so the result does not depend on Robolectric's default icon mask.
 *
 * Output: build/icon-renders (module directory). Synthetic art only.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "xxhdpi")
class IconRenderTest {
    private val outputDir = File("build/icon-renders")

    private enum class Mask { Circle, Squircle, RoundedSquare, Teardrop }

    @Test
    fun renderLauncherIcon() {
        outputDir.mkdirs()
        val context = RuntimeEnvironment.getApplication()
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        assertTrue("ic_launcher is an adaptive icon", icon is AdaptiveIconDrawable)
        icon as AdaptiveIconDrawable
        assertNotNull("monochrome layer present", icon.monochrome)

        Mask.entries.forEach { mask -> write("icon-192-${mask.name.lowercase()}", render(icon, 192, mask)) }
        val small = render(icon, 48, Mask.Squircle)
        write("icon-48px-squircle", small)
        write("icon-48px-squircle-x4", Bitmap.createScaledBitmap(small, 192, 192, false))
        write("icon-126px-squircle", render(icon, 126, Mask.Squircle))
        write("icon-layers-108dp", layers(icon))
        write("contact-sheet", contactSheet(icon))
        write("themed-monochrome", themed(icon))
    }

    /** The full 108 dp canvas of each layer, unmasked, with the 72 dp window and the 66 dp safe circle outlined. */
    private fun layers(icon: AdaptiveIconDrawable): Bitmap {
        val size = 324
        val out = Bitmap.createBitmap(size * 3 + 40, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(40, 40, 44))
        val guide =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.argb(200, 255, 80, 80)
            }
        listOf(icon.background, icon.foreground, icon.monochrome).forEachIndexed { i, layer ->
            val left = i * (size + 20)
            canvas.save()
            canvas.translate(left.toFloat(), 0f)
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply { color = Color.rgb(90, 90, 96) })
            drawLayer(canvas, layer, 0, 0, size)
            val unit = size / 108f
            canvas.drawRect(18 * unit, 18 * unit, 90 * unit, 90 * unit, guide)
            canvas.drawCircle(54 * unit, 54 * unit, 33 * unit, guide)
            canvas.restore()
        }
        return out
    }

    /** Draws background and foreground for an icon of [px] pixels and applies [mask]. */
    private fun render(
        icon: AdaptiveIconDrawable,
        px: Int,
        mask: Mask,
        layersOf: List<Drawable?> = listOf(icon.background, icon.foreground),
    ): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val full = px * 108 / 72
        val offset = -(full - px) / 2
        layersOf.forEach { drawLayer(canvas, it, offset, offset, full) }
        val maskBitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(maskBitmap).drawPath(maskPath(mask, px.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
        canvas.drawBitmap(maskBitmap, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        return out
    }

    private fun drawLayer(
        canvas: Canvas,
        layer: Drawable?,
        left: Int,
        top: Int,
        size: Int,
    ) {
        val d = layer?.constantState?.newDrawable()?.mutate() ?: layer ?: return
        d.setBounds(left, top, left + size, top + size)
        d.draw(canvas)
    }

    private fun maskPath(
        mask: Mask,
        s: Float,
    ): Path =
        when (mask) {
            Mask.Circle -> {
                Path().apply { addCircle(s / 2, s / 2, s / 2, Path.Direction.CW) }
            }

            Mask.RoundedSquare -> {
                Path().apply { addRoundRect(RectF(0f, 0f, s, s), s * 0.22f, s * 0.22f, Path.Direction.CW) }
            }

            Mask.Squircle -> {
                superellipse(s, 5.0)
            }

            Mask.Teardrop -> {
                Path().apply {
                    val r = s / 2
                    val small = s * 0.15f
                    moveTo(r, 0f)
                    arcTo(RectF(0f, 0f, s, s), 270f, 90f)
                    lineTo(s, s - small)
                    arcTo(RectF(s - 2 * small, s - 2 * small, s, s), 0f, 90f)
                    lineTo(r, s)
                    arcTo(RectF(0f, 0f, s, s), 90f, 180f)
                    close()
                }
            }
        }

    /** |x|^n + |y|^n = 1, scaled to [s]. */
    private fun superellipse(
        s: Float,
        n: Double,
    ): Path {
        val path = Path()
        val r = s / 2.0
        val steps = 360
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val c = cos(t)
            val sn = sin(t)
            val x = r + r * sign(c) * abs(c).pow(2.0 / n)
            val y = r + r * sign(sn) * abs(sn).pow(2.0 / n)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return path
    }

    /** Light, dark and busy wallpapers; on each: placeholder, our icon, placeholder (squircle, 144 px). */
    private fun contactSheet(icon: AdaptiveIconDrawable): Bitmap {
        val panelW = 600
        val panelH = 300
        val out = Bitmap.createBitmap(panelW, panelH * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val ours = render(icon, 144, Mask.Squircle)
        val grey = placeholder(144, Color.rgb(236, 238, 242), Color.rgb(200, 204, 212))
        val green = placeholder(144, Color.rgb(64, 190, 120), Color.rgb(28, 140, 84))
        for (i in 0 until 3) {
            val top = (i * panelH).toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val bottom = top + panelH
            val clamp = Shader.TileMode.CLAMP
            when (i) {
                0 -> {
                    paint.shader =
                        LinearGradient(0f, top, panelW.toFloat(), bottom, Color.rgb(244, 245, 249), Color.rgb(214, 222, 236), clamp)
                }

                1 -> {
                    paint.shader =
                        LinearGradient(0f, top, panelW.toFloat(), bottom, Color.rgb(10, 10, 12), Color.rgb(30, 32, 40), clamp)
                }

                else -> {
                    paint.shader =
                        LinearGradient(
                            0f,
                            top,
                            panelW.toFloat(),
                            top + panelH,
                            intArrayOf(Color.rgb(255, 150, 60), Color.rgb(220, 60, 130), Color.rgb(40, 110, 220), Color.rgb(30, 200, 170)),
                            null,
                            Shader.TileMode.CLAMP,
                        )
                }
            }
            canvas.save()
            canvas.clipRect(0f, top, panelW.toFloat(), top + panelH)
            canvas.drawRect(0f, top, panelW.toFloat(), top + panelH, paint)
            if (i == 2) busyDetails(canvas, top, panelW, panelH)
            canvas.restore()
            val y = top + (panelH - 144) / 2f
            listOf(grey, ours, green).forEachIndexed { k, bmp -> canvas.drawBitmap(bmp, 48f + k * 184f, y, null) }
        }
        return out
    }

    private fun busyDetails(
        canvas: Canvas,
        top: Float,
        w: Int,
        h: Int,
    ) {
        val blobs =
            listOf(
                Triple(120f, 60f, Color.argb(200, 255, 240, 120)),
                Triple(420f, 220f, Color.argb(200, 20, 20, 60)),
                Triple(300f, 40f, Color.argb(180, 255, 255, 255)),
                Triple(520f, 90f, Color.argb(200, 120, 255, 160)),
                Triple(60f, 250f, Color.argb(200, 90, 20, 120)),
            )
        blobs.forEach { (x, y, c) ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(x, top + y, 110f, c, c and 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, top + y, 110f, p)
        }
        val stripe =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(70, 255, 255, 255)
                strokeWidth = 10f
            }
        for (k in -4..w / 60) canvas.drawLine(k * 60f, top + h, k * 60f + 200f, top, stripe)
    }

    private fun placeholder(
        px: Int,
        light: Int,
        dark: Int,
    ): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, px.toFloat(), px.toFloat(), light, dark, Shader.TileMode.CLAMP)
        canvas.drawPath(superellipse(px.toFloat(), 5.0), paint)
        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255) }
        canvas.drawRoundRect(RectF(px * 0.3f, px * 0.3f, px * 0.7f, px * 0.7f), px * 0.08f, px * 0.08f, glyph)
        return out
    }

    /** Monochrome layer tinted as Android 13+ themed icons do, light and dark, on matching wallpapers. */
    private fun themed(icon: AdaptiveIconDrawable): Bitmap {
        val px = 144
        val out = Bitmap.createBitmap(600, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawRect(0f, 0f, 300f, 240f, Paint().apply { color = Color.rgb(226, 230, 240) })
        canvas.drawRect(300f, 0f, 600f, 240f, Paint().apply { color = Color.rgb(16, 18, 24) })
        listOf(
            Triple(Color.rgb(212, 222, 250), Color.rgb(24, 44, 104), 78f),
            Triple(Color.rgb(30, 40, 66), Color.rgb(196, 212, 255), 378f),
        ).forEach { (bg, fg, x) ->
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawPath(superellipse(px.toFloat(), 5.0), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
            val mono =
                icon.monochrome!!
                    .constantState!!
                    .newDrawable()
                    .mutate()
            mono.colorFilter = BlendModeColorFilter(fg, BlendMode.SRC_IN)
            val full = px * 108 / 72
            val offset = -(full - px) / 2
            c.save()
            c.clipPath(superellipse(px.toFloat(), 5.0))
            mono.setBounds(offset, offset, offset + full, offset + full)
            mono.draw(c)
            c.restore()
            canvas.drawBitmap(bmp, x, 48f, null)
        }
        return out
    }

    private fun write(
        name: String,
        bitmap: Bitmap,
    ) {
        File(outputDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
