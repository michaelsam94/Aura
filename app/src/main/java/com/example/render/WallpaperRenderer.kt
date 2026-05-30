package com.example.render

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.BatteryManager
import com.example.data.PreferencesManager
import com.example.model.BatteryState
import com.example.model.ChargingStatus
import com.example.sensor.SensorTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WallpaperRenderer(private val context: Context) {
    private val prefsManager = PreferencesManager.getInstance(context)
    private val sensorTracker = SensorTracker(context)

    val patternRenderer = PatternRenderer()
    val fluidRenderer = FluidRenderer()

    private val bgPaint = Paint().apply { isAntiAlias = true }

    // Dynamic states and hot values
    private val _batteryState = MutableStateFlow(BatteryState())
    val batteryState = _batteryState.asStateFlow()

    private var currentLux = 150f
    private var tiltRoll = 0f
    private var tiltPitch = 0f

    private var scope: CoroutineScope? = null
    private var jobs = mutableListOf<Job>()

    fun start(scope: CoroutineScope) {
        this.scope = scope
        jobs.forEach { it.cancel() }
        jobs.clear()

        // 1. Initialise fluids
        val ecoMode = prefsManager.getEcoMode()
        fluidRenderer.setup(prefsManager.getFluidConfig(), ecoMode)

        // 2. Observe sensor flow - Ambient Light (debounced via tracker or sampled at 200ms)
        jobs.add(scope.launch(Dispatchers.Default) {
            val delayUs = if (ecoMode) 500000 else 100000 // 500ms eco vs 100ms standard
            sensorTracker.observeLightSensor(delayUs).collectLatest { lux ->
                currentLux = lux
            }
        })

        // 3. Observe sensor flow - Gyroscope / Accelerometer
        jobs.add(scope.launch(Dispatchers.Default) {
            val delayUs = if (ecoMode) android.hardware.SensorManager.SENSOR_DELAY_NORMAL else android.hardware.SensorManager.SENSOR_DELAY_GAME
            sensorTracker.observeGyroscope(delayUs).collectLatest { gyro ->
                tiltRoll = gyro.roll
                tiltPitch = gyro.pitch
            }
        })

        // 4. Poll battery status immediately
        updateBatteryState()
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    fun updateBatteryState() {
        // Query current battery condition using sticky system intent
        val batteryStatusIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryStatusIntent != null) {
            val level = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 50

            val chargePlug = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val isCharging = chargePlug == BatteryManager.BATTERY_PLUGGED_AC ||
                    chargePlug == BatteryManager.BATTERY_PLUGGED_USB ||
                    chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS

            val tempMc = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val tempCelsius = tempMc / 10.0f

            // Fast-charging detection via extra current parameter (amperes >2A)
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val currentNowMicroAmps = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val isFastCharging = isCharging && (currentNowMicroAmps > 2_000_000 || currentNowMicroAmps < -2_000_000)

            val status = when {
                isFastCharging -> ChargingStatus.FAST_CHARGING
                isCharging -> ChargingStatus.CHARGING
                else -> ChargingStatus.DISCHARGING
            }

            _batteryState.value = BatteryState(
                status = status,
                temperatureCelsius = tempCelsius,
                levelPercent = percent,
                isOverheating = tempCelsius > 40.0f
            )
        }
    }

    fun tickPhysics(width: Int, height: Int) {
        // Tick physical particles on each animation frame
        fluidRenderer.stepSimulation(_batteryState.value, prefsManager.getFluidConfig(), width, height)
    }

    fun drawFrame(canvas: Canvas, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return

        val ambient = prefsManager.getAmbientConfig()
        val parallax = prefsManager.getParallaxConfig()
        val fluid = prefsManager.getFluidConfig()

        // 1. Feature 1: Ambient Light Palette Color Shift
        // Normalise current lux level within [minLux, maxLux] config range
        val range = (ambient.maxLux - ambient.minLux).coerceAtLeast(10f)
        val t = ((currentLux - ambient.minLux) / range).coerceIn(0f, 1f)

        // Interpolate colors based on ambient light level
        // High light -> Cool slate blue/teal. Low light -> Warm cozy amber/crimson.
        val baseColors = getInterpolatedColors(ambient.baseSchemeId, t)
        val bgColor = baseColors.first
        val glowAccent = baseColors.second

        // Draw background
        bgPaint.shader = RadialGradient(
            width / 2f, height / 2f, Math.max(width, height) * 0.75f,
            intArrayOf(glowAccent, bgColor),
            floatArrayOf(0f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Feature 3: Base Charger Fluids Simulation particle layer
        fluidRenderer.draw(canvas, _batteryState.value, fluid, width, height)

        // 3. Feature 2: Geometric Hexagonal Parallax vector overlay
        patternRenderer.draw(canvas, parallax, tiltRoll, tiltPitch, width, height)
    }

    private fun getInterpolatedColors(schemeId: String, t: Float): Pair<Int, Int> {
        // t representing day light amount: 0f dark room (use warmer cozy colors), 1f bright sun (use cool colors)
        return when (schemeId) {
            "Warm" -> {
                // Dark: deep mahogany scarlet, Bright: cozy sunset glow amber
                val bg = lerpColor(0xFF1E0804.toInt(), 0xFF3E1F1A.toInt(), t)
                val acc = lerpColor(0xFFD84315.toInt(), 0xFFFFB74D.toInt(), t)
                Pair(bg, acc)
            }
            "Cool" -> {
                // Dark: deep twilight violet, Bright: electrical charging neon purple
                val bg = lerpColor(0xFF0F0A1C.toInt(), 0xFF1B122B.toInt(), t)
                val acc = lerpColor(0xFF512DA8.toInt(), 0xFF9C27B0.toInt(), t)
                Pair(bg, acc)
            }
            "Arctic" -> {
                // Dark: midnight sea abyss, Bright: glacier glowing ice cyan
                val bg = lerpColor(0xFF0A192F.toInt(), 0xFF112240.toInt(), t)
                val acc = lerpColor(0xFF172A45.toInt(), 0xFF64FFDA.toInt(), t)
                Pair(bg, acc)
            }
            else -> { // "Neutral"
                // Dark: space grey black, Bright: deep ocean cobalt teal
                val bg = lerpColor(0xFF0D0E15.toInt(), 0xFF14243B.toInt(), t)
                val acc = lerpColor(0xFF00796B.toInt(), 0xFF26A69A.toInt(), t)
                Pair(bg, acc)
            }
        }
    }

    private fun lerpColor(colorStart: Int, colorEnd: Int, t: Float): Int {
        val startAlpha = Color.alpha(colorStart)
        val startRed = Color.red(colorStart)
        val startGreen = Color.green(colorStart)
        val startBlue = Color.blue(colorStart)

        val endAlpha = Color.alpha(colorEnd)
        val endRed = Color.red(colorEnd)
        val endGreen = Color.green(colorEnd)
        val endBlue = Color.blue(colorEnd)

        return Color.argb(
            (startAlpha + t * (endAlpha - startAlpha)).toInt(),
            (startRed + t * (endRed - startRed)).toInt(),
            (startGreen + t * (endGreen - startGreen)).toInt(),
            (startBlue + t * (endBlue - startBlue)).toInt()
        )
    }
}
