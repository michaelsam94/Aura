package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AmbientConfig
import com.example.model.FluidConfig
import com.example.model.ParallaxConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)

    private val _ecoMode = MutableStateFlow(getEcoMode())
    val ecoMode: StateFlow<Boolean> = _ecoMode.asStateFlow()

    private val _ambientConfig = MutableStateFlow(getAmbientConfig())
    val ambientConfig: StateFlow<AmbientConfig> = _ambientConfig.asStateFlow()

    private val _parallaxConfig = MutableStateFlow(getParallaxConfig())
    val parallaxConfig: StateFlow<ParallaxConfig> = _parallaxConfig.asStateFlow()

    private val _fluidConfig = MutableStateFlow(getFluidConfig())
    val fluidConfig: StateFlow<FluidConfig> = _fluidConfig.asStateFlow()

    private val _activePresetId = MutableStateFlow(getActivePresetId())
    val activePresetId: StateFlow<Int> = _activePresetId.asStateFlow()

    fun getEcoMode(): Boolean = prefs.getBoolean("eco_mode", false)

    fun setEcoMode(enabled: Boolean) {
        prefs.edit().putBoolean("eco_mode", enabled).apply()
        _ecoMode.value = enabled
    }

    fun getActivePresetId(): Int = prefs.getInt("active_preset_id", -1)

    fun setActivePresetId(id: Int) {
        prefs.edit().putInt("active_preset_id", id).apply()
        _activePresetId.value = id
    }

    fun getAmbientConfig(): AmbientConfig {
        val json = prefs.getString("ambient_config", null)
        return AmbientConfig.fromJson(json)
    }

    fun setAmbientConfig(config: AmbientConfig) {
        prefs.edit().putString("ambient_config", config.toJson()).apply()
        _ambientConfig.value = config
    }

    fun getParallaxConfig(): ParallaxConfig {
        val json = prefs.getString("parallax_config", null)
        return ParallaxConfig.fromJson(json)
    }

    fun setParallaxConfig(config: ParallaxConfig) {
        prefs.edit().putString("parallax_config", config.toJson()).apply()
        _parallaxConfig.value = config
    }

    fun getFluidConfig(): FluidConfig {
        val json = prefs.getString("fluid_config", null)
        return FluidConfig.fromJson(json)
    }

    fun setFluidConfig(config: FluidConfig) {
        prefs.edit().putString("fluid_config", config.toJson()).apply()
        _fluidConfig.value = config
    }

    // Apply multiple configs at once
    fun saveAllConfigs(ambient: AmbientConfig, parallax: ParallaxConfig, fluid: FluidConfig, presetId: Int) {
        prefs.edit()
            .putString("ambient_config", ambient.toJson())
            .putString("parallax_config", parallax.toJson())
            .putString("fluid_config", fluid.toJson())
            .putInt("active_preset_id", presetId)
            .apply()

        _ambientConfig.value = ambient
        _parallaxConfig.value = parallax
        _fluidConfig.value = fluid
        _activePresetId.value = presetId
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PreferencesManager(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
