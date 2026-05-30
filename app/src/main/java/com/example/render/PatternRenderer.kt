package com.example.render

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import com.example.model.ParallaxConfig
import kotlin.math.cos
import kotlin.math.sin

class PatternRenderer {
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private var cachedStyle: String = ""
    private var cachedWidth = 0
    private var cachedHeight = 0
    private val cachedPath = Path()

    // Matrix for parallax transformation
    private val transformMatrix = Matrix()

    fun draw(canvas: Canvas, config: ParallaxConfig, roll: Float, pitch: Float, width: Int, height: Int) {
        if (!config.enabled) return

        // Verify if cache is valid; if not, rebuild the pattern path
        if (cachedStyle != config.patternStyle || cachedWidth != width || cachedHeight != height) {
            rebuildPath(config.patternStyle, width, height)
            cachedStyle = config.patternStyle
            cachedWidth = width
            cachedHeight = height
        }

        // Configure paint styling
        paint.color = config.lineColor
        // Scale stroke width with respect to config and device density factor
        paint.strokeWidth = config.lineWidthDp * 3f

        // Map tilt angles to displacement offsets
        // Max parallax travel is proportional to depth setting
        val maxOffset = 80f * config.depth
        val dx = roll * maxOffset
        val dy = pitch * maxOffset

        // Apply matrix offset and draw the cached path
        canvas.save()
        transformMatrix.reset()
        transformMatrix.setTranslate(dx, dy)
        canvas.concat(transformMatrix)

        if (config.patternStyle == "DOTS") {
            paint.style = Paint.Style.FILL
            drawDots(canvas, width, height)
        } else {
            paint.style = Paint.Style.STROKE
            canvas.drawPath(cachedPath, paint)
        }
        canvas.restore()
    }

    private fun rebuildPath(style: String, width: Int, height: Int) {
        cachedPath.reset()
        if (width <= 0 || height <= 0) return

        when (style) {
            "HEXAGON" -> {
                val size = 60f
                val h = size * Math.sqrt(3.0).toFloat()
                val w = size * 2f
                val rowHeight = h
                val colWidth = w * 0.75f

                val cols = (width / colWidth).toInt() + 2
                val rows = (height / rowHeight).toInt() + 2

                for (c in -1..cols) {
                    for (r in -1..rows) {
                        val cx = c * colWidth
                        var cy = r * rowHeight
                        if (c % 2 != 0) {
                            cy += rowHeight / 2f
                        }
                        drawHexagonAt(cachedPath, cx, cy, size)
                    }
                }
            }
            "CIRCUIT" -> {
                // Generate electronic motherboard circuits organically
                val spacing = 120f
                val cols = (width / spacing).toInt() + 2
                val rows = (height / spacing).toInt() + 2

                for (c in 0..cols) {
                    val startX = c * spacing + 20f
                    // Alternating trunk lines that bend at 45 degree angles
                    cachedPath.moveTo(startX, 0f)
                    var currentY = 0f
                    var currentX = startX
                    while (currentY < height + 50f) {
                        val nextY = currentY + 150f + (Math.random() * 100f).toFloat()
                        currentY = nextY
                        // Add organic circuit turn
                        val branch = Math.random()
                        if (branch < 0.35f) {
                            currentX += 45f
                            cachedPath.lineTo(currentX, currentY - 45f)
                            cachedPath.lineTo(currentX, currentY)
                            // Draw circle pad
                            cachedPath.addCircle(currentX, currentY, 8f, Path.Direction.CW)
                            cachedPath.moveTo(currentX, currentY)
                        } else if (branch < 0.7f) {
                            currentX -= 45f
                            cachedPath.lineTo(currentX, currentY - 45f)
                            cachedPath.lineTo(currentX, currentY)
                            cachedPath.addCircle(currentX, currentY, 8f, Path.Direction.CW)
                            cachedPath.moveTo(currentX, currentY)
                        } else {
                            cachedPath.lineTo(currentX, currentY)
                        }
                    }
                }
            }
            "GRID" -> {
                // Dual diagonal grid overlay
                val spacing = 100f
                val stepsX = (width / spacing).toInt() + 2
                val stepsY = (height / spacing).toInt() + 2

                for (i in -5..stepsX + 5) {
                    // Forward slash lines
                    cachedPath.moveTo(i * spacing, -50f)
                    cachedPath.lineTo(i * spacing - height, height + 50f)

                    // Backward slash lines
                    cachedPath.moveTo(i * spacing, -50f)
                    cachedPath.lineTo(i * spacing + height, height + 50f)
                }
            }
        }
    }

    private fun drawHexagonAt(path: Path, x: Float, y: Float, size: Float) {
        for (i in 0..5) {
            val angleRad = (Math.PI / 180f) * (60 * i)
            val px = x + size * cos(angleRad).toFloat()
            val py = y + size * sin(angleRad).toFloat()
            if (i == 0) {
                path.moveTo(px, py)
            } else {
                path.lineTo(px, py)
            }
        }
        path.close()
    }

    private fun drawDots(canvas: Canvas, width: Int, height: Int) {
        val spacing = 70f
        val cols = (width / spacing).toInt() + 2
        val rows = (height / spacing).toInt() + 2

        for (c in 0..cols) {
            for (r in 0..rows) {
                val cx = c * spacing + 10f
                val cy = r * spacing + 10f
                // Beautiful matrix grid dot styling
                canvas.drawCircle(cx, cy, 6f, paint)
            }
        }
    }
}
