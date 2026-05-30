package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.example.data.PreferencesManager
import com.example.render.WallpaperRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AuraWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return AuraEngine()
    }

    private inner class AuraEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private var engineScope: CoroutineScope? = null
        private var renderer: WallpaperRenderer? = null
        private var loopJob: Job? = null
        private var isEngineVisible = false

        private val prefs = PreferencesManager.getInstance(applicationContext)

        // BroadcastReceiver for tracking dynamic battery variations
        private val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                renderer?.updateBatteryState()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            renderer = WallpaperRenderer(applicationContext)
            prefs.registerListener(this)

            // Listen for battery updates
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }

        override fun onDestroy() {
            super.onDestroy()
            prefs.unregisterListener(this)
            try {
                unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                // Ignore if not registered
            }
            stopEngineLoop()
            renderer?.stop()
            renderer = null
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.isEngineVisible = visible
            if (visible) {
                startEngineLoop()
            } else {
                stopEngineLoop()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            stopEngineLoop()
        }

        private fun startEngineLoop() {
            stopEngineLoop()
            val scope = CoroutineScope(Dispatchers.Default + Job())
            engineScope = scope

            renderer?.start(scope)

            // Dedicated physics tick & drawing loop
            loopJob = scope.launch(Dispatchers.Default) {
                val ecoMode = prefs.getEcoMode()
                val frameDelayMs = if (ecoMode) 33L else 16L // 30 FPS eco vs 60 FPS standard

                // Trigger initial particle setup
                renderer?.fluidRenderer?.setup(prefs.getFluidConfig(), ecoMode)

                while (isEngineVisible) {
                    val startTime = System.currentTimeMillis()

                    val holder = surfaceHolder
                    if (holder.surface.isValid) {
                        // Tick simulation physical coordinates
                        val width = holder.surfaceFrame.width()
                        val height = holder.surfaceFrame.height()

                        renderer?.tickPhysics(width, height)

                        var canvas: Canvas? = null
                        try {
                            canvas = holder.lockCanvas()
                            if (canvas != null) {
                                renderer?.drawFrame(canvas, width, height)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            if (canvas != null) {
                                try {
                                    holder.unlockCanvasAndPost(canvas)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }

                    // Strict FPS rate compliance limit
                    val elapsed = System.currentTimeMillis() - startTime
                    val delayVal = (frameDelayMs - elapsed).coerceAtLeast(1L)
                    delay(delayVal)
                }
            }
        }

        private fun stopEngineLoop() {
            loopJob?.cancel()
            loopJob = null
            engineScope?.cancel()
            engineScope = null
            renderer?.stop()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == "eco_mode" || key == "ambient_config" || key == "parallax_config" || key == "fluid_config") {
                // Refresh rendering configurations immediately from preferences files to active states
                if (isEngineVisible) {
                    // Re-start loop with updated FPS constraints or physical parameters
                    startEngineLoop()
                }
            }
        }
    }
}
