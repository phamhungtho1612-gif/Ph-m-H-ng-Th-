package com.example.imaging

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.CreativeOverlayTextState
import com.example.model.OverlayFontStyle
import com.example.model.OverlayTextShape
import com.example.model.OverlayTextStyle
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object CreativeTextRenderer {

    private val typefaceCache = ConcurrentHashMap<OverlayFontStyle, Typeface>()

    val curatedPaletteHex: List<String> = listOf(
        "#FFFFFF", // Pure White
        "#FFD426", // Friends Sun Yellow
        "#141416", // Studio Onyx
        "#FF5CA8", // Korean Pink
        "#FF5733", // Sunset Coral
        "#4DF3FF", // Cyber Cyan
        "#5CE69F", // Mint Pop
        "#B983FF", // Lavender Glow
        "#FFE8A3", // Cream Gold
        "#FF2A85", // Neon Magenta
        "#3A86FF", // Electric Blue
        "#FF9F1C"  // Amber Pop
    )

    fun resolveTypeface(context: Context, fontStyle: OverlayFontStyle): Typeface {
        typefaceCache[fontStyle]?.let { return it }
        val fontRes = when (fontStyle) {
            OverlayFontStyle.BE_VIETNAM_PRO -> R.font.be_vietnam_pro
            OverlayFontStyle.ANTON -> R.font.anton
            OverlayFontStyle.PLAYFAIR -> R.font.playfair_display
            OverlayFontStyle.PACIFICO -> R.font.pacifico
            OverlayFontStyle.DANCING_SCRIPT -> R.font.dancing_script
            OverlayFontStyle.OSWALD -> R.font.oswald
            OverlayFontStyle.COMFORTAA -> R.font.comfortaa
            OverlayFontStyle.MONTSERRAT -> R.font.montserrat
            OverlayFontStyle.PATRICK_HAND -> R.font.patrick_hand
            OverlayFontStyle.JETBRAINS_MONO -> R.font.jetbrains_mono
            OverlayFontStyle.FRAUNCES -> R.font.fraunces
            OverlayFontStyle.CHAKRA_PETCH -> R.font.chakra_petch
            OverlayFontStyle.SYSTEM_DEFAULT -> 0
        }

        val resolved = if (fontRes != 0) {
            runCatching { ResourcesCompat.getFont(context, fontRes) }.getOrNull()
                ?: Typeface.DEFAULT_BOLD
        } else {
            Typeface.DEFAULT_BOLD
        }
        typefaceCache[fontStyle] = resolved
        return resolved
    }

    fun drawCreativeTextInSlot(
        context: Context,
        canvas: Canvas,
        slotRect: RectF,
        cornerRadius: Float,
        overlay: CreativeOverlayTextState
    ) {
        val rawText = overlay.text.trim()
        if (rawText.isEmpty() || slotRect.width() <= 0f || slotRect.height() <= 0f) return

        val clipPath = Path().apply {
            addRoundRect(slotRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        val saveCount = canvas.save()
        canvas.clipPath(clipPath)

        val typeface = resolveTypeface(context, overlay.font)
        val baseColor = parseHexSafe(overlay.colorHex)
        val alphaInt = (overlay.opacity.coerceIn(0.1f, 1f) * 255f).toInt()

        val cx = slotRect.left + slotRect.width() * overlay.centerXFraction.coerceIn(0.15f, 0.85f)
        val cy = slotRect.top + slotRect.height() * overlay.centerYFraction.coerceIn(0.12f, 0.88f)
        val w = slotRect.width()
        val h = slotRect.height()
        val scale = overlay.scale.coerceIn(0.45f, 3.5f)
        val baseTextSize = (w * 0.135f * scale).coerceAtLeast(16f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = baseTextSize
            textAlign = Paint.Align.CENTER
            letterSpacing = overlay.letterSpacingEm.coerceIn(-0.05f, 0.45f)
        }

        if (overlay.shape == OverlayTextShape.MAGAZINE_STACK) {
            drawStackedPosterText(
                canvas = canvas,
                rawText = rawText,
                slotRect = slotRect,
                cx = cx,
                cy = cy,
                baseTextSize = baseTextSize * 1.15f,
                scale = scale,
                lineSpacingMultiplier = overlay.lineSpacingMultiplier.coerceIn(0.75f, 1.45f),
                bendAngle = overlay.bendAngleDegrees,
                style = overlay.style,
                baseColor = baseColor,
                alphaInt = alphaInt,
                paint = paint
            )
            canvas.restoreToCount(saveCount)
            return
        }

        if (overlay.shape == OverlayTextShape.STRAIGHT ||
            overlay.shape == OverlayTextShape.DIAGONAL_UP ||
            overlay.shape == OverlayTextShape.DIAGONAL_DOWN
        ) {
            val angle = when (overlay.shape) {
                OverlayTextShape.DIAGONAL_UP -> -16f + overlay.bendAngleDegrees * 0.6f
                OverlayTextShape.DIAGONAL_DOWN -> 16f + overlay.bendAngleDegrees * 0.6f
                else -> overlay.bendAngleDegrees
            }

            // Fit comfortably within frame unless scaled high intentionally
            val maxLineWidth = w * 0.94f * scale.coerceAtLeast(1.0f)
            while (paint.measureText(rawText) > maxLineWidth && paint.textSize > w * 0.045f) {
                paint.textSize -= 2f
            }

            canvas.save()
            canvas.rotate(angle, cx, cy)
            val fm = paint.fontMetrics
            val baselineY = cy - (fm.ascent + fm.descent) / 2f

            drawStyledTextPrimitives(
                canvas = canvas,
                text = rawText,
                path = null,
                x = cx,
                y = baselineY,
                bounds = RectF(
                    cx - paint.measureText(rawText) / 2f,
                    baselineY + fm.ascent,
                    cx + paint.measureText(rawText) / 2f,
                    baselineY + fm.descent
                ),
                style = overlay.style,
                baseColor = baseColor,
                alphaInt = alphaInt,
                paint = paint,
                drawFrostedPill = true
            )
            canvas.restore()
            canvas.restoreToCount(saveCount)
            return
        }

        // Curved & Geometric Path Shapes
        val isLoopingShape = overlay.repeatAlongShape &&
            (overlay.shape == OverlayTextShape.HEART ||
                overlay.shape == OverlayTextShape.CIRCLE ||
                overlay.shape == OverlayTextShape.WAVE ||
                overlay.shape == OverlayTextShape.ARC_UP ||
                overlay.shape == OverlayTextShape.ARC_DOWN)

        val shapePath = buildShapePath(
            shape = overlay.shape,
            cx = cx,
            cy = cy,
            slotW = w,
            slotH = h,
            scale = scale,
            bendDegrees = overlay.bendAngleDegrees,
            fullLoop = isLoopingShape
        )

        val pathMeasure = PathMeasure(shapePath, false)
        val pathLength = pathMeasure.length.coerceAtLeast(1f)

        val renderedString: String
        if (isLoopingShape) {
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = (w * 0.088f * scale).coerceAtLeast(14f)
            val token = "$rawText • "
            val tokenWidth = paint.measureText(token).coerceAtLeast(24f)
            val repeatCount = (pathLength / tokenWidth).toInt().coerceIn(2, 16)
            // Adjust letter spacing or textSize slightly so loop wraps nicely without overlap
            val targetTokenWidth = pathLength / repeatCount
            val ratio = (targetTokenWidth / tokenWidth).coerceIn(0.6f, 1.35f)
            paint.textSize = (paint.textSize * ratio).coerceAtLeast(14f)
            renderedString = token.repeat(repeatCount).trimEnd()
        } else {
            paint.textAlign = Paint.Align.CENTER
            val maxCurveLen = pathLength * 0.94f * scale.coerceAtLeast(1.0f)
            while (paint.measureText(rawText) > maxCurveLen && paint.textSize > w * 0.042f) {
                paint.textSize -= 2f
            }
            renderedString = rawText
        }

        val pathBounds = RectF()
        shapePath.computeBounds(pathBounds, true)

        drawStyledTextPrimitives(
            canvas = canvas,
            text = renderedString,
            path = shapePath,
            x = 0f,
            y = 0f,
            bounds = pathBounds,
            style = overlay.style,
            baseColor = baseColor,
            alphaInt = alphaInt,
            paint = paint,
            drawFrostedPill = false
        )

        canvas.restoreToCount(saveCount)
    }

    private fun drawStackedPosterText(
        canvas: Canvas,
        rawText: String,
        slotRect: RectF,
        cx: Float,
        cy: Float,
        baseTextSize: Float,
        scale: Float,
        lineSpacingMultiplier: Float,
        bendAngle: Float,
        style: OverlayTextStyle,
        baseColor: Int,
        alphaInt: Int,
        paint: Paint
    ) {
        val words = rawText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lines = when {
            rawText.contains('\n') -> rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
            words.size <= 1 -> listOf(rawText.uppercase())
            words.size == 2 -> words.map { it.uppercase() }
            else -> {
                val mid = (words.size + 1) / 2
                listOf(
                    words.take(mid).joinToString(" ").uppercase(),
                    words.drop(mid).joinToString(" ").uppercase()
                )
            }
        }

        val w = slotRect.width()
        paint.textSize = baseTextSize
        val longest = lines.maxByOrNull { paint.measureText(it) } ?: rawText
        val targetWidth = w * 0.92f * scale.coerceAtLeast(1.0f)
        while (paint.measureText(longest) > targetWidth && paint.textSize > w * 0.055f) {
            paint.textSize -= 2f
        }

        val lineHeight = paint.textSize * lineSpacingMultiplier
        val totalHeight = lineHeight * lines.size
        val startCenterY = cy - totalHeight / 2f + lineHeight / 2f

        canvas.save()
        canvas.rotate(bendAngle * 0.6f, cx, cy)

        lines.forEachIndexed { idx, line ->
            val lineCenterY = startCenterY + idx * lineHeight
            val fm = paint.fontMetrics
            val baselineY = lineCenterY - (fm.ascent + fm.descent) / 2f
            val lineW = paint.measureText(line)
            drawStyledTextPrimitives(
                canvas = canvas,
                text = line,
                path = null,
                x = cx,
                y = baselineY,
                bounds = RectF(
                    cx - lineW / 2f,
                    baselineY + fm.ascent,
                    cx + lineW / 2f,
                    baselineY + fm.descent
                ),
                style = style,
                baseColor = baseColor,
                alphaInt = alphaInt,
                paint = paint,
                drawFrostedPill = idx == 0 && lines.size == 1
            )
        }
        canvas.restore()
    }

    private fun buildShapePath(
        shape: OverlayTextShape,
        cx: Float,
        cy: Float,
        slotW: Float,
        slotH: Float,
        scale: Float,
        bendDegrees: Float,
        fullLoop: Boolean
    ): Path {
        val path = Path()
        val bendFactor = (1f + bendDegrees / 45f).coerceIn(0.45f, 1.7f)

        when (shape) {
            OverlayTextShape.ARC_UP -> {
                val radiusScale = 0.88f + 0.14f * scale.coerceAtMost(2.5f)
                val radius = (slotW * 0.48f * radiusScale / bendFactor)
                    .coerceIn(slotW * 0.24f, slotW * 1.15f)
                val oval = RectF(
                    cx - radius,
                    cy,
                    cx + radius,
                    cy + radius * 2f
                )
                val sweep = (138f * bendFactor).coerceIn(75f, 175f)
                val startAngle = 270f - sweep / 2f
                path.addArc(oval, startAngle, sweep)
            }

            OverlayTextShape.ARC_DOWN -> {
                val radiusScale = 0.88f + 0.14f * scale.coerceAtMost(2.5f)
                val radius = (slotW * 0.48f * radiusScale / bendFactor)
                    .coerceIn(slotW * 0.24f, slotW * 1.15f)
                val oval = RectF(
                    cx - radius,
                    cy - radius * 2f,
                    cx + radius,
                    cy
                )
                val sweep = (138f * bendFactor).coerceIn(75f, 175f)
                val startAngle = 90f + sweep / 2f
                path.addArc(oval, startAngle, -sweep)
            }

            OverlayTextShape.CIRCLE -> {
                val radius = (min(slotW, slotH) * 0.28f * (0.75f + 0.25f * scale.coerceAtMost(2.2f)))
                    .coerceIn(slotW * 0.16f, slotW * 0.50f)
                val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                if (fullLoop) {
                    path.addArc(oval, -90f, 359f)
                } else {
                    path.addArc(oval, 195f, 150f)
                }
            }

            OverlayTextShape.HEART -> {
                val unit = (min(slotW, slotH) * 0.0195f * (0.85f + 0.18f * scale.coerceAtMost(2.0f)))
                    .coerceIn(slotW * 0.011f, slotW * 0.029f)
                val startT: Double
                val endT: Double
                if (fullLoop) {
                    startT = -PI
                    endT = PI
                } else {
                    // Upper twin lobes swept cleanly left-to-right
                    startT = -0.78 * PI
                    endT = 0.78 * PI
                }
                val steps = 72
                for (i in 0..steps) {
                    val t = startT + (endT - startT) * (i.toDouble() / steps)
                    val hx = 16.0 * sin(t).pow(3.0)
                    val hy = -(13.0 * cos(t) - 5.0 * cos(2.0 * t) - 2.0 * cos(3.0 * t) - cos(4.0 * t))
                    val px = cx + (hx * unit).toFloat()
                    // Shift y so heart center sits naturally around cy
                    val py = cy + ((hy + 2.2) * unit).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
            }

            OverlayTextShape.WAVE -> {
                val spanW = (slotW * 0.86f * (0.90f + 0.12f * scale.coerceAtMost(2.2f)))
                    .coerceAtMost(slotW * 1.12f)
                val startX = cx - spanW / 2f
                val amplitude = (slotW * 0.075f * bendFactor).coerceIn(10f, slotW * 0.14f)
                val cycles = 1.6f
                val steps = 56
                for (i in 0..steps) {
                    val frac = i.toFloat() / steps
                    val px = startX + spanW * frac
                    val py = cy + sin((frac - 0.5f) * 2.0 * PI * cycles).toFloat() * amplitude
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
            }

            OverlayTextShape.RIBBON_V -> {
                val halfW = (slotW * 0.41f * (0.90f + 0.12f * scale.coerceAtMost(2.2f)))
                    .coerceAtMost(slotW * 0.56f)
                val dip = slotW * 0.10f * bendFactor
                path.moveTo(cx - halfW, cy - dip * 0.55f)
                path.quadTo(cx - halfW * 0.25f, cy + dip * 0.4f, cx, cy + dip * 0.65f)
                path.quadTo(cx + halfW * 0.25f, cy + dip * 0.4f, cx + halfW, cy - dip * 0.55f)
            }

            else -> {
                path.moveTo(cx - slotW * 0.44f, cy)
                path.lineTo(cx + slotW * 0.44f, cy)
            }
        }
        return path
    }

    private fun drawStyledTextPrimitives(
        canvas: Canvas,
        text: String,
        path: Path?,
        x: Float,
        y: Float,
        bounds: RectF,
        style: OverlayTextStyle,
        baseColor: Int,
        alphaInt: Int,
        paint: Paint,
        drawFrostedPill: Boolean
    ) {
        val textSize = paint.textSize

        fun emit(p: Paint, dx: Float = 0f, dy: Float = 0f) {
            if (path != null) {
                if (dx != 0f || dy != 0f) {
                    canvas.save()
                    canvas.translate(dx, dy)
                    canvas.drawTextOnPath(text, path, 0f, 0f, p)
                    canvas.restore()
                } else {
                    canvas.drawTextOnPath(text, path, 0f, 0f, p)
                }
            } else {
                canvas.drawText(text, x + dx, y + dy, p)
            }
        }

        when (style) {
            OverlayTextStyle.SOLID_SHADOW -> {
                val p = Paint(paint).apply {
                    this.style = Paint.Style.FILL
                    color = withAlpha(baseColor, alphaInt)
                    setShadowLayer(textSize * 0.14f, 0f, textSize * 0.06f, Color.argb(175, 0, 0, 0))
                }
                emit(p)
            }

            OverlayTextStyle.FROSTED_GLASS -> {
                if (drawFrostedPill && path == null) {
                    val padH = textSize * 0.42f
                    val padV = textSize * 0.22f
                    val pillRect = RectF(
                        bounds.left - padH,
                        bounds.top - padV,
                        bounds.right + padH,
                        bounds.bottom + padV
                    )
                    val radius = pillRect.height() / 2f
                    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.style = Paint.Style.FILL
                        color = Color.argb((alphaInt * 0.34f).toInt(), 255, 255, 255)
                    }
                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.style = Paint.Style.STROKE
                        strokeWidth = (textSize * 0.045f).coerceAtLeast(2f)
                        color = Color.argb((alphaInt * 0.65f).toInt(), 255, 255, 255)
                    }
                    canvas.drawRoundRect(pillRect, radius, radius, bgPaint)
                    canvas.drawRoundRect(pillRect, radius, radius, borderPaint)
                } else {
                    val frostHalo = Paint(paint).apply {
                        this.style = Paint.Style.STROKE
                        strokeJoin = Paint.Join.ROUND
                        strokeCap = Paint.Cap.ROUND
                        strokeWidth = textSize * 0.22f
                        color = Color.argb((alphaInt * 0.28f).toInt(), 255, 255, 255)
                        maskFilter = BlurMaskFilter((textSize * 0.12f).coerceAtLeast(2f), BlurMaskFilter.Blur.NORMAL)
                    }
                    emit(frostHalo)
                }
                val core = Paint(paint).apply {
                    this.style = Paint.Style.FILL
                    color = withAlpha(baseColor, alphaInt)
                    setShadowLayer(textSize * 0.18f, 0f, textSize * 0.03f, Color.argb(120, 0, 0, 0))
                }
                emit(core)
            }

            OverlayTextStyle.CANDY_STICKER -> {
                val outerShadow = Paint(paint).apply {
                    this.style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = textSize * 0.24f
                    color = Color.argb((alphaInt * 0.42f).toInt(), 0, 0, 0)
                }
                emit(outerShadow, dx = textSize * 0.04f, dy = textSize * 0.06f)

                val stickerBorder = Paint(paint).apply {
                    this.style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = textSize * 0.22f
                    color = Color.argb(alphaInt, 255, 255, 255)
                }
                emit(stickerBorder)

                val innerFill = Paint(paint).apply {
                    this.style = Paint.Style.FILL
                    color = withAlpha(if (baseColor == Color.WHITE) Color.parseColor("#FF5CA8") else baseColor, alphaInt)
                }
                emit(innerFill)
            }

            OverlayTextStyle.NEON_GLOW -> {
                val neonColor = if (baseColor == Color.BLACK || baseColor == Color.parseColor("#141416")) {
                    Color.parseColor("#FFD426")
                } else {
                    baseColor
                }
                val outerGlow = Paint(paint).apply {
                    this.style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = textSize * 0.14f
                    color = withAlpha(neonColor, (alphaInt * 0.65f).toInt())
                    maskFilter = BlurMaskFilter((textSize * 0.22f).coerceAtLeast(3f), BlurMaskFilter.Blur.NORMAL)
                }
                emit(outerGlow)

                val innerGlow = Paint(paint).apply {
                    this.style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = textSize * 0.06f
                    color = withAlpha(neonColor, alphaInt)
                    maskFilter = BlurMaskFilter((textSize * 0.08f).coerceAtLeast(2f), BlurMaskFilter.Blur.NORMAL)
                }
                emit(innerGlow)

                val hotCore = Paint(paint).apply {
                    this.style = Paint.Style.FILL
                    color = Color.argb(alphaInt, 255, 255, 255)
                }
                emit(hotCore)
            }

            OverlayTextStyle.RETRO_3D -> {
                val depthColor = contrastingDepthColor(baseColor)
                val step = (textSize * 0.016f).coerceAtLeast(1.2f)
                val depthPaint = Paint(paint).apply {
                    this.style = Paint.Style.FILL
                    color = withAlpha(depthColor, alphaInt)
                }
                for (i in 7 downTo 1) {
                    emit(depthPaint, dx = step * i, dy = step * i)
                }
                val outline = Paint(paint).apply {
                    this.style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = textSize * 0.065f
                    color = withAlpha(depthColor, alphaInt)
                }
                emit(outline)
                val fill = Paint(paint).apply {
                    this.style = Paint.Style.FILL
                    color = withAlpha(baseColor, alphaInt)
                }
                emit(fill)
            }

            OverlayTextStyle.OUTLINE_HOLLOW -> {
                val shadowStroke = Paint(paint).apply {
                    this.style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = textSize * 0.085f
                    color = withAlpha(baseColor, alphaInt)
                    setShadowLayer(textSize * 0.10f, 0f, textSize * 0.04f, Color.argb(140, 0, 0, 0))
                }
                emit(shadowStroke)
            }

            OverlayTextStyle.DOUBLE_STROKE -> {
                val outerStroke = Paint(paint).apply {
                    this.style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = textSize * 0.19f
                    color = withAlpha(baseColor, alphaInt)
                }
                emit(outerStroke)

                val midStroke = Paint(paint).apply {
                    this.style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = textSize * 0.10f
                    color = withAlpha(contrastingDepthColor(baseColor), alphaInt)
                }
                emit(midStroke)

                val fill = Paint(paint).apply {
                    this.style = Paint.Style.FILL
                    color = withAlpha(baseColor, alphaInt)
                }
                emit(fill)
            }

            OverlayTextStyle.GRADIENT_GOLD -> drawGradientText(
                paint = paint,
                bounds = bounds,
                colors = intArrayOf(
                    Color.parseColor("#FFF8CE"),
                    Color.parseColor("#FFD426"),
                    Color.parseColor("#E08E00")
                ),
                outlineColor = Color.argb(200, 36, 24, 0),
                alphaInt = alphaInt,
                emit = ::emit
            )

            OverlayTextStyle.GRADIENT_SUNSET -> drawGradientText(
                paint = paint,
                bounds = bounds,
                colors = intArrayOf(
                    Color.parseColor("#FF4D6D"),
                    Color.parseColor("#FF8A3D"),
                    Color.parseColor("#FFE066")
                ),
                outlineColor = Color.argb(180, 40, 12, 24),
                alphaInt = alphaInt,
                emit = ::emit
            )

            OverlayTextStyle.GRADIENT_OCEAN -> drawGradientText(
                paint = paint,
                bounds = bounds,
                colors = intArrayOf(
                    Color.parseColor("#80FFDB"),
                    Color.parseColor("#4CC9F0"),
                    Color.parseColor("#4361EE")
                ),
                outlineColor = Color.argb(180, 8, 22, 54),
                alphaInt = alphaInt,
                emit = ::emit
            )

            OverlayTextStyle.GRADIENT_RAINBOW -> drawGradientText(
                paint = paint,
                bounds = bounds,
                colors = intArrayOf(
                    Color.parseColor("#FF595E"),
                    Color.parseColor("#FFCA3A"),
                    Color.parseColor("#8AC926"),
                    Color.parseColor("#1982C4"),
                    Color.parseColor("#B5179E")
                ),
                outlineColor = Color.argb(195, 255, 255, 255),
                alphaInt = alphaInt,
                emit = ::emit
            )

            OverlayTextStyle.GLITCH_RGB -> {
                val shift = (textSize * 0.045f).coerceAtLeast(2.5f)
                val cyan = Paint(paint).apply {
                    this.style = Paint.Style.FILL
                    color = Color.argb((alphaInt * 0.85f).toInt(), 0, 245, 212)
                }
                val magenta = Paint(paint).apply {
                    this.style = Paint.Style.FILL
                    color = Color.argb((alphaInt * 0.85f).toInt(), 255, 42, 133)
                }
                val main = Paint(paint).apply {
                    this.style = Paint.Style.FILL
                    color = withAlpha(baseColor, alphaInt)
                }
                emit(cyan, dx = -shift, dy = -shift * 0.35f)
                emit(magenta, dx = shift, dy = shift * 0.35f)
                emit(main)
            }

            OverlayTextStyle.CHROME_METALLIC -> {
                val grad = LinearGradient(
                    bounds.left,
                    bounds.top - textSize * 0.4f,
                    bounds.left,
                    bounds.bottom + textSize * 0.2f,
                    intArrayOf(
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#CBD5E1"),
                        Color.parseColor("#475569"),
                        Color.parseColor("#F8FAFC")
                    ),
                    floatArrayOf(0f, 0.45f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                val outline = Paint(paint).apply {
                    this.style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = textSize * 0.08f
                    color = Color.argb(alphaInt, 20, 24, 33)
                }
                emit(outline)
                val fill = Paint(paint).apply {
                    this.style = Paint.Style.FILL
                    shader = grad
                    alpha = alphaInt
                }
                emit(fill)
            }

            OverlayTextStyle.EDITORIAL_SOFT -> {
                val softPaint = Paint(paint).apply {
                    this.style = Paint.Style.FILL
                    color = withAlpha(baseColor, (alphaInt * 0.72f).toInt())
                    setShadowLayer(textSize * 0.16f, 0f, textSize * 0.04f, Color.argb(90, 0, 0, 0))
                }
                emit(softPaint)
            }
        }
    }

    private inline fun drawGradientText(
        paint: Paint,
        bounds: RectF,
        colors: IntArray,
        outlineColor: Int,
        alphaInt: Int,
        emit: (Paint, Float, Float) -> Unit
    ) {
        val textSize = paint.textSize
        val safeRight = if (bounds.width() > 4f) bounds.right else bounds.left + textSize * 4f
        val safeBottom = if (bounds.height() > 4f) bounds.bottom else bounds.top + textSize
        val gradient = LinearGradient(
            bounds.left,
            bounds.top,
            safeRight,
            safeBottom,
            colors,
            null,
            Shader.TileMode.CLAMP
        )
        val strokePaint = Paint(paint).apply {
            this.style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = textSize * 0.075f
            color = withAlpha(outlineColor, (alphaInt * 0.75f).toInt())
        }
        emit(strokePaint, 0f, 0f)
        val fillPaint = Paint(paint).apply {
            this.style = Paint.Style.FILL
            shader = gradient
            alpha = alphaInt
            setShadowLayer(textSize * 0.12f, 0f, textSize * 0.05f, Color.argb(140, 0, 0, 0))
        }
        emit(fillPaint, 0f, 0f)
    }

    private fun parseHexSafe(hex: String): Int {
        return runCatching { Color.parseColor(hex) }.getOrDefault(Color.WHITE)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        val clamped = alpha.coerceIn(0, 255)
        return Color.argb(clamped, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun contrastingDepthColor(color: Int): Int {
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return if (luminance > 0.55) Color.parseColor("#18181B") else Color.parseColor("#FFFFFF")
    }
}
