package dev.foldman.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.sqrt

/**
 * Pure Android Canvas renderer that draws an ultra-realistic, authentic Compact Cassette tape
 * (styled after the Sony Walkman WM-RX707 & Sony MA-XG46) onto a [Bitmap] for Android widgets.
 *
 * Tuned for maximum legibility on mobile home screens:
 * - Large, high-contrast bold typography for song titles & artist info
 * - Generously sized album artwork sticker
 * - Authentic tape spools, red leader clamp, and metallic Walkman detailing
 */
object CassetteBitmapRenderer {

    fun render(
        widthPx: Int = 800,
        heightPx: Int = 512,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        artwork: Bitmap? = null,
        isPlaying: Boolean = false,
        progress: Float = 0.35f,
        source: String = "TAPEMAN",
        shellId: String = "tdk_maxg",
    ): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val u = widthPx / 100f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Dark outer drop shadow / bezel rim
        val shellRect = RectF(0f, 0f, 100f * u, 64f * u)
        val cornerRadius = 3.4f * u

        // Shell body gradient depending on cassette model
        paint.style = Paint.Style.FILL
        val shellColors = when (shellId) {
            "sony_hf" -> intArrayOf(
                Color.rgb(0xDF, 0xE2, 0xE6),
                Color.rgb(0xCF, 0xD2, 0xD6),
                Color.rgb(0xC4, 0xC7, 0xCB),
                Color.rgb(0xD8, 0xDB, 0xDF),
            )
            "tdk_sa" -> intArrayOf(
                Color.rgb(0x2A, 0x2C, 0x30),
                Color.rgb(0x1E, 0x20, 0x24),
                Color.rgb(0x16, 0x18, 0x1B),
                Color.rgb(0x22, 0x24, 0x28),
            )
            "maxell_xliis", "maxell_udii" -> intArrayOf(
                Color.rgb(0x1C, 0x1D, 0x20),
                Color.rgb(0x12, 0x13, 0x15),
                Color.rgb(0x0C, 0x0D, 0x0F),
                Color.rgb(0x16, 0x17, 0x1A),
            )
            "sony_uxpro" -> intArrayOf(
                Color.rgb(0x26, 0x2A, 0x30),
                Color.rgb(0x1A, 0x1D, 0x22),
                Color.rgb(0x14, 0x16, 0x1A),
                Color.rgb(0x20, 0x23, 0x28),
            )
            "teac_sound52" -> intArrayOf(
                Color.rgb(0x20, 0x22, 0x26),
                Color.rgb(0x14, 0x15, 0x18),
                Color.rgb(0x0E, 0x10, 0x12),
                Color.rgb(0x18, 0x1A, 0x1D),
            )
            else -> intArrayOf(
                Color.rgb(0x28, 0x2C, 0x33),
                Color.rgb(0x18, 0x1A, 0x1E),
                Color.rgb(0x12, 0x14, 0x17),
                Color.rgb(0x1C, 0x1E, 0x23),
            )
        }
        paint.shader = LinearGradient(
            0f, 0f, 0f, 64f * u,
            shellColors,
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(shellRect, cornerRadius, cornerRadius, paint)
        paint.shader = null

        // Specular outer bevel highlight
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * u * 0.2f
        paint.color = Color.argb(0x66, 0xFF, 0xFF, 0xFF)
        canvas.drawRoundRect(shellRect, cornerRadius, cornerRadius, paint)

        // Side textured grip ridges (7 ridges on left and right edges)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.6f * u
        paint.color = Color.argb(0x33, 0x00, 0x00, 0x00)
        for (i in 0 until 7) {
            val y = (22f + i * 3.2f) * u
            canvas.drawLine(1.2f * u, y, 3.4f * u, y, paint)
            canvas.drawLine(96.6f * u, y, 98.8f * u, y, paint)
        }
        paint.color = Color.argb(0x1F, 0xFF, 0xFF, 0xFF)
        for (i in 0 until 7) {
            val y = (22f + i * 3.2f) * u + 0.5f * u
            canvas.drawLine(1.2f * u, y, 3.4f * u, y, paint)
            canvas.drawLine(96.6f * u, y, 98.8f * u, y, paint)
        }

        // Top write-protect notch recesses
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(0x0C, 0x0D, 0x10)
        canvas.drawRect(RectF(14f * u, 0f, 22f * u, 2.2f * u), paint)
        canvas.drawRect(RectF(78f * u, 0f, 86f * u, 2.2f * u), paint)

        // Bottom tape head access bay trapezoid
        val headPath = Path().apply {
            moveTo(22f * u, 64f * u)
            lineTo(26f * u, 51.5f * u)
            lineTo(74f * u, 51.5f * u)
            lineTo(78f * u, 64f * u)
            close()
        }
        paint.color = Color.rgb(0x14, 0x16, 0x19)
        canvas.drawPath(headPath, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f * u
        paint.color = Color.argb(0x22, 0xFF, 0xFF, 0xFF)
        canvas.drawPath(headPath, paint)

        // Tape head guide pins & capstan holes
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(0x08, 0x09, 0x0A)
        canvas.drawCircle(32f * u, 57.5f * u, 1.8f * u, paint)
        canvas.drawCircle(68f * u, 57.5f * u, 1.8f * u, paint)
        canvas.drawCircle(50f * u, 56.5f * u, 2.4f * u, paint)
        paint.color = Color.rgb(0x3A, 0x3E, 0x47)
        canvas.drawCircle(32f * u, 57.5f * u, 0.8f * u, paint)
        canvas.drawCircle(68f * u, 57.5f * u, 0.8f * u, paint)

        // 2. Corner assembly screws
        val screwPositions = listOf(
            Pair(4.8f * u, 4.8f * u),
            Pair(95.2f * u, 4.8f * u),
            Pair(4.8f * u, 59.2f * u),
            Pair(95.2f * u, 59.2f * u),
        )
        for ((sx, sy) in screwPositions) {
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(0x10, 0x11, 0x14)
            canvas.drawCircle(sx, sy, 1.9f * u, paint)
            paint.color = Color.rgb(0x64, 0x69, 0x73)
            canvas.drawCircle(sx, sy, 1.4f * u, paint)
            // Screw slot
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.4f * u
            paint.color = Color.rgb(0x1C, 0x1E, 0x22)
            canvas.drawLine(sx - 0.9f * u, sy - 0.9f * u, sx + 0.9f * u, sy + 0.9f * u, paint)
        }

        // 3. Cassette Viewing Window
        val windowRect = RectF(15f * u, 25.5f * u, 85f * u, 47.0f * u)
        val windowRadius = 2.8f * u

        // Outer recessed trench
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * u
        paint.color = Color.rgb(0x09, 0x0A, 0x0C)
        canvas.drawRoundRect(windowRect, windowRadius, windowRadius, paint)

        // Window interior background (smoked clear)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(0x0E, 0x10, 0x13)
        canvas.drawRoundRect(windowRect, windowRadius, windowRadius, paint)

        // Hub Centers
        val hubLX = 28.75f * u
        val hubLY = 36.2f * u
        val hubRX = 71.25f * u
        val hubRY = 36.2f * u

        // Magnetic tape spools (radii scale based on playback progress)
        val p = progress.coerceIn(0.08f, 0.92f)
        val rMin = 5.8f * u
        val rMax = 13.8f * u
        val leftRadius = sqrt(rMin * rMin + (rMax * rMax - rMin * rMin) * (1f - p))
        val rightRadius = sqrt(rMin * rMin + (rMax * rMax - rMin * rMin) * p)

        // Clip tape reels within the window
        canvas.save()
        val clipWindow = Path().apply { addRoundRect(windowRect, windowRadius, windowRadius, Path.Direction.CW) }
        canvas.clipPath(clipWindow)

        // Tape Packs (dark magnetic oxide)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(0x24, 0x1C, 0x17)
        canvas.drawCircle(hubLX, hubLY, leftRadius, paint)
        canvas.drawCircle(hubRX, hubRY, rightRadius, paint)

        // Subtle concentric texture on tape packs
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.35f * u
        paint.color = Color.argb(0x28, 0xFF, 0xFF, 0xFF)
        if (leftRadius > rMin + 2f * u) canvas.drawCircle(hubLX, hubLY, leftRadius * 0.78f, paint)
        if (rightRadius > rMin + 2f * u) canvas.drawCircle(hubRX, hubRY, rightRadius * 0.78f, paint)

        // Tape ribbon connecting the spools across bottom rollers
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.9f * u
        paint.color = Color.rgb(0x2C, 0x22, 0x1B)
        canvas.drawLine(hubLX - leftRadius, hubLY, 18f * u, 45.5f * u, paint)
        canvas.drawLine(18f * u, 45.5f * u, 82f * u, 45.5f * u, paint)
        canvas.drawLine(82f * u, 45.5f * u, hubRX + rightRadius, hubRY, paint)

        // Guide rollers at bottom corners
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(0xE6, 0xEA, 0xDE)
        canvas.drawCircle(18.5f * u, 45.5f * u, 1.2f * u, paint)
        canvas.drawCircle(81.5f * u, 45.5f * u, 1.2f * u, paint)
        paint.color = Color.rgb(0x55, 0x59, 0x62)
        canvas.drawCircle(18.5f * u, 45.5f * u, 0.5f * u, paint)
        canvas.drawCircle(81.5f * u, 45.5f * u, 0.5f * u, paint)

        // Reel spools: Open-reel spun aluminum for TEAC Sound 52, standard hubs for other models
        if (shellId == "teac_sound52") {
            drawTeacFlange(canvas, hubLX, hubLY, u, paint, hasRedClamp = true)
            drawTeacFlange(canvas, hubRX, hubRY, u, paint, hasRedClamp = false)
        } else {
            // Left Hub (white composite with 3 drive teeth + red leader clamp)
            drawHub(canvas, hubLX, hubLY, u, hasRedClamp = true)
            // Right Hub
            drawHub(canvas, hubRX, hubRY, u, hasRedClamp = false)
        }

        // Scale tick dots in center window (0 ... 50 ... 100)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(0x55, 0xFF, 0xFF, 0xFF)
        for (i in 0..4) {
            canvas.drawCircle((43f + i * 3.5f) * u, 36.2f * u, 0.4f * u, paint)
        }

        // Smoked acrylic glass reflection
        val glarePath = Path().apply {
            moveTo(36f * u, 25.5f * u)
            lineTo(52f * u, 25.5f * u)
            lineTo(38f * u, 47.0f * u)
            lineTo(22f * u, 47.0f * u)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            22f * u, 25.5f * u, 52f * u, 47.0f * u,
            intArrayOf(
                Color.argb(0x00, 0xFF, 0xFF, 0xFF),
                Color.argb(0x18, 0xFF, 0xFF, 0xFF),
                Color.argb(0x05, 0xFF, 0xFF, 0xFF),
                Color.argb(0x00, 0xFF, 0xFF, 0xFF),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(glarePath, paint)
        paint.shader = null

        canvas.restore()

        // Inner window highlight rim
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.5f * u
        paint.color = Color.argb(0x35, 0xFF, 0xFF, 0xFF)
        canvas.drawRoundRect(windowRect, windowRadius, windowRadius, paint)

        // 4. Large, Highly-Legible Cassette Label Strip
        val labelRect = RectF(8.5f * u, 6.5f * u, 91.5f * u, 24.5f * u)
        val labelRadius = 2.0f * u

        // Warm crisp vintage label paper
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(0xFA, 0xF5, 0xEC)
        canvas.drawRoundRect(labelRect, labelRadius, labelRadius, paint)

        // Dual racing accent stripes along the bottom of the label
        // Sony Walkman signature orange stripe
        paint.color = Color.rgb(0xE8, 0x64, 0x2C)
        canvas.drawRect(RectF(8.5f * u, 22.8f * u, 91.5f * u, 23.8f * u), paint)
        // Gold/Yellow stripe
        paint.color = Color.rgb(0xF2, 0xB3, 0x3D)
        canvas.drawRect(RectF(8.5f * u, 23.8f * u, 91.5f * u, 24.5f * u), paint)

        // Album Art thumbnail on the left of the label (14.2u x 14.2u square)
        val artLeft = 10.0f * u
        val artTop = 8.0f * u
        val artSize = 13.8f * u
        val hasArt = artwork != null

        if (artwork != null) {
            val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(0x18, 0x1A, 0x1E)
            canvas.drawRoundRect(artRect, 1.8f * u, 1.8f * u, paint)

            canvas.save()
            val clipArt = Path().apply { addRoundRect(artRect, 1.8f * u, 1.8f * u, Path.Direction.CW) }
            canvas.clipPath(clipArt)
            val src = Rect(0, 0, artwork.width, artwork.height)
            canvas.drawBitmap(artwork, src, artRect, paint)
            canvas.restore()

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.4f * u
            paint.color = Color.argb(0x33, 0x00, 0x00, 0x00)
            canvas.drawRoundRect(artRect, 1.8f * u, 1.8f * u, paint)
        } else {
            // Stylized Side "A" badge when no album art
            val badgeRect = RectF(artLeft, artTop, artLeft + 11.5f * u, artTop + artSize)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(0x24, 0x26, 0x2C)
            canvas.drawRoundRect(badgeRect, 1.8f * u, 1.8f * u, paint)

            paint.color = Color.WHITE
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            paint.textSize = 6.8f * u
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("A", badgeRect.centerX(), badgeRect.centerY() + 2.4f * u, paint)
        }

        // Text typography on label
        val textStartX = if (hasArt) (artLeft + artSize + 2.6f * u) else (artLeft + 13.5f * u)
        val textMaxW = (90.0f * u) - textStartX

        // 1. Meta / Source indicator (Small bold silkscreen line)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(0x82, 0x7B, 0x72)
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = 2.2f * u
        paint.textAlign = Paint.Align.LEFT
        val metaText = when (shellId) {
            "sony_hf" -> "SIDE A · SONY HF 90 · NORMAL BIAS"
            "tdk_sa" -> "SIDE A · TDK SA-90 · HIGH BIAS 70µs"
            "maxell_xliis" -> "SIDE A · maxell XLII-S · HIGH (CrO2)"
            "sony_uxpro" -> "SIDE A · SONY UX-Pro · CERAMIC GUIDE"
            else -> if (hasArt) "SIDE A · TYPE IV · $source" else "TYPE IV METAL · $source · 4.76 cm/s"
        }
        canvas.drawText(metaText, textStartX, 10.8f * u, paint)

        // 2. Song Title: Significantly larger (5.2u), high-contrast bold typography
        val displayTitle = when {
            !title.isNullOrBlank() -> title
            isPlaying -> "Playing..."
            else -> "Tapeman"
        }
        paint.color = Color.rgb(0x10, 0x10, 0x14)
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = 5.2f * u
        val truncatedTitle = truncateText(displayTitle, paint, textMaxW)
        canvas.drawText(truncatedTitle, textStartX, 15.8f * u, paint)

        // 3. Artist & Album Subtitle: Bold (3.6u) for effortless reading
        val displayArtist = when {
            !artist.isNullOrBlank() && !album.isNullOrBlank() -> "$artist · $album"
            !artist.isNullOrBlank() -> artist
            !album.isNullOrBlank() -> album
            else -> "Sony Walkman WM-RX707"
        }
        paint.color = Color.rgb(0x3E, 0x3A, 0x34)
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.textSize = 3.6f * u
        val truncatedArtist = truncateText(displayArtist, paint, textMaxW)
        canvas.drawText(truncatedArtist, textStartX, 20.2f * u, paint)

        // Border around label paper
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.4f * u
        paint.color = Color.argb(0x28, 0x00, 0x00, 0x00)
        canvas.drawRoundRect(labelRect, labelRadius, labelRadius, paint)

        // 5. Lower Cassette Markings
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(0x88, 0xFF, 0xFF, 0xFF)
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        paint.textSize = 1.8f * u
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("SONY AUTO REVERSE", 16f * u, 50.5f * u, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("DOLBY B NR", 84f * u, 50.5f * u, paint)

        return bmp
    }

    private fun drawHub(canvas: Canvas, cx: Float, cy: Float, u: Float, hasRedClamp: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Outer white composite flange ring
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(0xEA, 0xED, 0xE4)
        canvas.drawCircle(cx, cy, 5.8f * u, paint)

        // Hub outer border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.35f * u
        paint.color = Color.argb(0x44, 0x00, 0x00, 0x00)
        canvas.drawCircle(cx, cy, 5.8f * u, paint)

        // Red leader tape clamp (authentic detail from Sony MA-XG46)
        if (hasRedClamp) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f * u
            paint.color = Color.rgb(0xE0, 0x3C, 0x24)
            val clampRect = RectF(cx - 5.2f * u, cy - 5.2f * u, cx + 5.2f * u, cy + 5.2f * u)
            canvas.drawArc(clampRect, 110f, 32f, false, paint)
        }

        // Center spindle hole
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(0x18, 0x1A, 0x1E)
        canvas.drawCircle(cx, cy, 3.2f * u, paint)

        // 3 Drive teeth spokes pointing inward
        paint.color = Color.rgb(0xEA, 0xED, 0xE4)
        for (i in 0 until 3) {
            canvas.save()
            canvas.rotate(i * 120f, cx, cy)
            canvas.drawRect(cx - 0.7f * u, cy - 3.2f * u, cx + 0.7f * u, cy - 1.6f * u, paint)
            canvas.restore()
        }

        // Center silver spindle pin
        paint.color = Color.rgb(0x7A, 0x80, 0x8E)
        canvas.drawCircle(cx, cy, 1.3f * u, paint)
        paint.color = Color.rgb(0x15, 0x17, 0x1A)
        canvas.drawCircle(cx, cy, 0.6f * u, paint)
    }

    private fun drawTeacFlange(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        u: Float,
        paint: Paint,
        hasRedClamp: Boolean = false,
    ) {
        val outerR = 13.8f * u
        val innerR = 5.8f * u

        // Spun aluminum outer rim
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.3f * u
        paint.color = Color.rgb(0xDC, 0xDF, 0xE4)
        canvas.drawCircle(cx, cy, outerR, paint)

        // Outer specular rim
        paint.strokeWidth = 0.3f * u
        paint.color = Color.argb(0x88, 0xFF, 0xFF, 0xFF)
        canvas.drawCircle(cx, cy, outerR + 0.4f * u, paint)

        // Inner aluminum hub ring
        paint.strokeWidth = 1.0f * u
        paint.color = Color.rgb(0xD0, 0xD4, 0xD9)
        canvas.drawCircle(cx, cy, innerR, paint)

        // 3 Solid aluminum spokes
        paint.style = Paint.Style.FILL
        for (i in 0 until 3) {
            canvas.save()
            canvas.rotate(i * 120f, cx, cy)
            val spokePath = Path().apply {
                val a1 = -16.0 * Math.PI / 180.0
                val a2 = 16.0 * Math.PI / 180.0
                val r1 = innerR
                val r2 = outerR - 0.4f * u
                val x1 = (cx + r1 * Math.sin(a1)).toFloat()
                val y1 = (cy - r1 * Math.cos(a1)).toFloat()
                val x2 = (cx + r2 * Math.sin(a1)).toFloat()
                val y2 = (cy - r2 * Math.cos(a1)).toFloat()
                val x3 = (cx + r2 * Math.sin(a2)).toFloat()
                val y3 = (cy - r2 * Math.cos(a2)).toFloat()
                val x4 = (cx + r1 * Math.sin(a2)).toFloat()
                val y4 = (cy - r1 * Math.cos(a2)).toFloat()
                moveTo(x1, y1)
                lineTo(x2, y2)
                lineTo(x3, y3)
                lineTo(x4, y4)
                close()
            }
            paint.color = Color.rgb(0xCD, 0xD1, 0xD8)
            canvas.drawPath(spokePath, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.25f * u
            paint.color = Color.argb(0x44, 0x00, 0x00, 0x00)
            canvas.drawPath(spokePath, paint)

            // Indexing pinhole
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(0x10, 0x12, 0x14)
            canvas.drawCircle(cx, cy - 9.4f * u, 0.8f * u, paint)

            canvas.restore()
        }

        // Standard center hub with 3 drive teeth and red leader clamp
        drawHub(canvas, cx, cy, u, hasRedClamp)
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length - 1
        while (end > 0 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return if (end > 0) text.substring(0, end) + ellipsis else text
    }
}
