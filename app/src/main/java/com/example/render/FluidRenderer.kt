package com.example.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.example.model.BatteryState
import com.example.model.ChargingStatus
import com.example.model.FluidConfig
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FluidRenderer {
    // Advection grid dimension
    private var gridN = 24
    private var uGrid = FloatArray(gridN * gridN)
    private var vGrid = FloatArray(gridN * gridN)

    private val gridRef = AtomicReference(GridData(gridN, uGrid, vGrid))

    private var particles = FloatArray(0) // x, y, age, vx, vy for each particle
    private var maxParticleCount = 120

    private val paint = Paint().apply {
        isAntiAlias = true
    }

    private val vignettePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private var particleBitmap: Bitmap? = null
    private var cachedPrimaryColor = 0
    private var cachedAccentColor = 0
    private var cachedGlowRadius = 0f

    private var densityRatioWidth = 1f
    private var densityRatioHeight = 1f

    private var timeTicks = 0f

    private class GridData(val size: Int, val u: FloatArray, val v: FloatArray)

    fun setup(config: FluidConfig, ecoMode: Boolean) {
        gridN = if (ecoMode) 16 else 24
        maxParticleCount = if (ecoMode) (config.particleCount / 2).coerceAtLeast(30) else config.particleCount

        // Preallocate grid buffers
        uGrid = FloatArray(gridN * gridN)
        vGrid = FloatArray(gridN * gridN)
        gridRef.set(GridData(gridN, uGrid, vGrid))

        // Preallocate particles: each particle has x, y, speed, angle, opacity
        val numParticles = maxParticleCount
        particles = FloatArray(numParticles * 5)
        for (i in 0 until numParticles) {
            resetParticle(i, 1000f, 1920f, initAge = Math.random().toFloat())
        }
    }

    private fun resetParticle(index: Int, width: Float, height: Float, initAge: Float = 0f) {
        val i = index * 5
        // Start particles along bottom or anywhere for beautiful initial scattering
        if (initAge > 0f) {
            particles[i] = (Math.random() * width).toFloat()
            particles[i + 1] = (Math.random() * height).toFloat()
        } else {
            particles[i] = (Math.random() * width).toFloat()
            particles[i + 1] = height - (Math.random() * 100f).toFloat() // Start at bottom
        }
        particles[i + 2] = initAge + (Math.random() * 0.3f).toFloat() // Age / Normalised progress
        particles[i + 3] = 0f // Current vx
        particles[i + 4] = -5f - (Math.random() * 10f).toFloat() // Current vy rising vertical
    }

    fun stepSimulation(batteryState: BatteryState, config: FluidConfig, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return

        densityRatioWidth = width.toFloat()
        densityRatioHeight = height.toFloat()

        // 1. Calculate charging characteristics
        val velocityScale = when (batteryState.status) {
            ChargingStatus.DISCHARGING -> 0.3f * config.velocityScale
            ChargingStatus.CHARGING -> 0.8f * config.velocityScale
            ChargingStatus.FAST_CHARGING -> 1.6f * config.velocityScale
        }

        timeTicks += 0.04f * velocityScale

        // 2. Compute 2D Vector Velocity Field in dispatchers (background advection helper)
        val data = gridRef.get() ?: return
        val n = data.size
        val u = data.u
        val v = data.v

        for (y in 0 until n) {
            for (x in 0 until n) {
                val idx = y * n + x
                // Sum trigonometric and current-based rising forces
                val angle = (x.toFloat() / n * 2f * PI.toFloat()) + timeTicks
                u[idx] = cos(angle) * 3f * velocityScale
                // Standard rising wind (y-axis goes down, so negative represents going up)
                v[idx] = -4f * velocityScale + sin(angle + y) * 2f * velocityScale
            }
        }

        // 3. Move Particles using local velocity grid
        val numParticles = (particles.size / 5).coerceAtMost(maxParticleCount)
        for (idx in 0 until numParticles) {
            val i = idx * 5
            var px = particles[i]
            var py = particles[i + 1]
            var age = particles[i + 2]

            // Find grid cell coordinates
            val cellX = ((px / width) * n).toInt().coerceIn(0, n - 1)
            val cellY = ((py / height) * n).toInt().coerceIn(0, n - 1)
            val gridIdx = cellY * n + cellX

            val targetVx = u[gridIdx]
            val targetVy = v[gridIdx]

            // Apply inertia to update velocity vectors
            particles[i + 3] = 0.85f * particles[i + 3] + 0.15f * targetVx
            particles[i + 4] = 0.85f * particles[i + 4] + 0.15f * targetVy

            // Move particle
            px += particles[i + 3]
            py += particles[i + 4]
            age += 0.003f + (Math.random() * 0.002f).toFloat() // Age incremental step

            particles[i] = px
            particles[i + 1] = py
            particles[i + 2] = age

            // Reset particle on border escape or death
            if (px < -50f || px > width + 50f || py < -50f || py > height + 50f || age >= 1f) {
                resetParticle(idx, width.toFloat(), height.toFloat())
            }
        }
    }

    fun draw(canvas: Canvas, batteryState: BatteryState, config: FluidConfig, width: Int, height: Int) {
        if (!config.enabled || width <= 0 || height <= 0) return

        // Configure Particle Bitmap Cache
        val spritePrimary = when (batteryState.status) {
            ChargingStatus.DISCHARGING -> 0xFF3F51B5.toInt() // Indigo/Cool Blue
            ChargingStatus.CHARGING -> 0xFFFFC107.toInt() // Gold/Amber
            ChargingStatus.FAST_CHARGING -> 0xFF00E5FF.toInt() // Cyan/Fast Electric
        }
        val spriteAccent = config.accentColor

        val particleRadius = config.glowRadius.coerceIn(5f, 60f)

        if (particleBitmap == null || cachedPrimaryColor != spritePrimary || cachedAccentColor != spriteAccent || cachedGlowRadius != particleRadius) {
            buildParticleCache(spritePrimary, spriteAccent, particleRadius)
            cachedPrimaryColor = spritePrimary
            cachedAccentColor = spriteAccent
            cachedGlowRadius = particleRadius
        }

        val bmp = particleBitmap ?: return
        val wHalf = bmp.width / 2f
        val hHalf = bmp.height / 2f

        // Draw particle layers
        val numParticles = (particles.size / 5).coerceAtMost(maxParticleCount)
        for (idx in 0 until numParticles) {
            val i = idx * 5
            val px = particles[i]
            val py = particles[i + 1]
            val age = particles[i + 2]

            // Fade in and fade out curve
            val alpha = if (age < 0.2f) {
                age / 0.2f
            } else if (age > 0.8f) {
                (1f - age) / 0.2f
            } else {
                1f
            }

            paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
            canvas.drawBitmap(bmp, px - wHalf, py - hHalf, paint)
        }

        // Draw pulsing overheat vignette if applicable
        if (batteryState.isOverheating) {
            drawOverheatVignette(canvas, width, height)
        }
    }

    private fun buildParticleCache(primary: Int, accent: Int, radius: Float) {
        val size = (radius * 3).toInt().coerceAtLeast(16)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cacheCanvas = Canvas(bitmap)

        val tempPaint = Paint().apply {
            isAntiAlias = true
        }

        val cx = size / 2f
        val cy = size / 2f

        // radial gradient for soft-glow sprites
        val gradient = RadialGradient(
            cx, cy, radius,
            intArrayOf(accent, primary, Color.TRANSPARENT),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        tempPaint.shader = gradient
        cacheCanvas.drawCircle(cx, cy, radius * 1.5f, tempPaint)

        particleBitmap = bitmap
    }

    private fun drawOverheatVignette(canvas: Canvas, width: Int, height: Int) {
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = Math.sqrt((cx * cx + cy * cy).toDouble()).toFloat()

        // Create a beautiful threat-alert red border indicating overheating temperatures
        val pulseIntensity = 0.5f + 0.3f * sin(System.currentTimeMillis() / 250f).toFloat()
        val borderTransRed = Color.argb((pulseIntensity * 120).toInt(), 229, 57, 53)

        val overlayShader = RadialGradient(
            cx, cy, maxRadius,
            intArrayOf(Color.TRANSPARENT, borderTransRed),
            floatArrayOf(0.7f, 1.0f),
            Shader.TileMode.CLAMP
        )
        vignettePaint.shader = overlayShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
    }
}
