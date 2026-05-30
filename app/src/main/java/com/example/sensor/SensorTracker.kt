package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

data class GyroState(val roll: Float, val pitch: Float, val yaw: Float)

class SensorTracker(private val context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun isSensorAvailable(sensorType: Int): Boolean {
        return sensorManager.getDefaultSensor(sensorType) != null
    }

    fun observeLightSensor(samplingUs: Int): Flow<Float> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sensor == null) {
            // Emulate default light value (ambient comfortable room lighting)
            trySend(300f)
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            private var lastValue = -1f
            override fun onSensorChanged(event: SensorEvent) {
                val rawValue = event.values[0]
                // Simple low-pass filter to prevent fluorescent-light flickering
                val filtered = if (lastValue < 0) rawValue else 0.8f * lastValue + 0.2f * rawValue
                lastValue = filtered
                trySend(filtered)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, samplingUs)
        awaitClose { sensorManager.unregisterListener(listener) }
    }.flowOn(Dispatchers.Default)

    fun observeGyroscope(samplingUs: Int): Flow<GyroState> = callbackFlow {
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // If gyroscope is available, use it. If not, fallback to accelerometer for tilt
        if (gyroSensor != null) {
            val listener = object : SensorEventListener {
                private var pitch = 0f
                private var roll = 0f
                private var yaw = 0f
                private var lastTimestamp = 0L

                override fun onSensorChanged(event: SensorEvent) {
                    if (lastTimestamp != 0L) {
                        val dT = (event.timestamp - lastTimestamp) * 1.0f / 1000000000.0f
                        // Angular speed in rad/s, integrate to get radians
                        val omegaX = event.values[0]
                        val omegaY = event.values[1]
                        val omegaZ = event.values[2]

                        // Low-pass noise threshold to prevent static drift
                        val threshold = 0.02f
                        val filtX = if (Math.abs(omegaX) > threshold) omegaX else 0f
                        val filtY = if (Math.abs(omegaY) > threshold) omegaY else 0f
                        val filtZ = if (Math.abs(omegaZ) > threshold) omegaZ else 0f

                        pitch += filtX * dT
                        roll += filtY * dT
                        yaw += filtZ * dT

                        // Wrap angles within reasonable limits (visual parallax offsets)
                        pitch = pitch.coerceIn(-1.5f, 1.5f)
                        roll = roll.coerceIn(-1.5f, 1.5f)

                        // Gradual reset towards center when stationary
                        if (filtX == 0f && filtY == 0f) {
                            pitch *= 0.98f
                            roll *= 0.98f
                        }

                        trySend(GyroState(roll, pitch, yaw))
                    }
                    lastTimestamp = event.timestamp
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, gyroSensor, samplingUs)
            awaitClose { sensorManager.unregisterListener(listener) }
        } else if (accelSensor != null) {
            // Fallback to stable Accelerometer-based tilt estimation
            val listener = object : SensorEventListener {
                private var currentRoll = 0f
                private var currentPitch = 0f
                override fun onSensorChanged(event: SensorEvent) {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val az = event.values[2]

                    // Calculate pitch and roll angles
                    val targetPitch = Math.atan2(ay.toDouble(), az.toDouble()).toFloat()
                    val targetRoll = Math.atan2(-ax.toDouble(), Math.sqrt((ay * ay + az * az).toDouble())).toFloat()

                    // Apply standard low-pass filter
                    val alpha = 0.1f
                    currentPitch = alpha * targetPitch + (1f - alpha) * currentPitch
                    currentRoll = alpha * targetRoll + (1f - alpha) * currentRoll

                    trySend(GyroState(currentRoll, currentPitch, 0f))
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, accelSensor, samplingUs)
            awaitClose { sensorManager.unregisterListener(listener) }
        } else {
            // Emulate slow sinusoidal motion if no hardware sensor is available
            var ticks = 0f
            val listener = object : Runnable {
                override fun run() {
                    ticks += 0.05f
                    val r = (Math.sin(ticks.toDouble()) * 0.35).toFloat()
                    val p = (Math.cos((ticks * 0.7f).toDouble()) * 0.35).toFloat()
                    trySend(GyroState(r, p, 0f))
                    // Loop manually if channel is still active
                }
            }
            // Just emit a fixed pleasant offset so the rendering keeps ticking gracefully
            trySend(GyroState(0.15f, -0.1f, 0f))
            close()
        }
    }.flowOn(Dispatchers.Default)
}
