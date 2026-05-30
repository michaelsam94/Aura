package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.PresetEntity
import com.example.model.AmbientConfig
import com.example.model.BatteryImpactEstimate
import com.example.model.DrainRating
import com.example.model.FluidConfig
import com.example.model.ParallaxConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState<out T> {
    object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
    object Empty : UiState<Nothing>
}

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.presetDao()
    private val prefs = PreferencesManager.getInstance(application)

    // Flow of presets from Room DB
    val presets: StateFlow<UiState<List<PresetEntity>>> = dao.getAllPresets()
        .map { list ->
            if (list.isEmpty()) {
                UiState.Empty
            } else {
                UiState.Success(list)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UiState.Loading
        )

    // Read real-time states from preference flows
    val ecoMode: StateFlow<Boolean> = prefs.ecoMode
    val ambientConfig: StateFlow<AmbientConfig> = prefs.ambientConfig
    val parallaxConfig: StateFlow<ParallaxConfig> = prefs.parallaxConfig
    val fluidConfig: StateFlow<FluidConfig> = prefs.fluidConfig
    val activePresetId: StateFlow<Int> = prefs.activePresetId

    // Real-time battery impact calculation estimation
    val batteryImpactEstimate: StateFlow<BatteryImpactEstimate> = combine(
        ecoMode, ambientConfig, parallaxConfig, fluidConfig
    ) { eco, amb, para, flu ->
        calculateHeuristicBatteryImpact(eco, amb, para, flu)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BatteryImpactEstimate(DrainRating.MINIMAL, 1.2f, listOf("Core Grid"))
    )

    init {
        // Automatically seed standard bundled presets if database is fresh
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getBundledPresets()
            if (existing.isEmpty()) {
                seedBundledPresets()
            }
        }
    }

    private suspend fun seedBundledPresets() = withContext(Dispatchers.IO) {
        val arcticNight = PresetEntity(
            name = "Arctic Night",
            isBundled = true,
            ambientConfigJson = AmbientConfig(enabled = true, baseSchemeId = "Arctic", minLux = 10f, maxLux = 800f).toJson(),
            parallaxConfigJson = ParallaxConfig(enabled = true, patternStyle = "HEXAGON", depth = 0.5f, lineColor = 0xFF64FFDA.toInt()).toJson(),
            fluidConfigJson = FluidConfig(enabled = true, particleCount = 100, velocityScale = 0.6f, primaryColor = 0xFF00E5FF.toInt(), accentColor = 0xFF1A237E.toInt()).toJson()
        )

        val solarFlare = PresetEntity(
            name = "Solar Flare",
            isBundled = true,
            ambientConfigJson = AmbientConfig(enabled = true, baseSchemeId = "Warm", minLux = 5f, maxLux = 1200f).toJson(),
            parallaxConfigJson = ParallaxConfig(enabled = true, patternStyle = "CIRCUIT", depth = 0.8f, lineColor = 0xFFFFD54F.toInt()).toJson(),
            fluidConfigJson = FluidConfig(enabled = true, particleCount = 140, velocityScale = 1.3f, primaryColor = 0xFFD84315.toInt(), accentColor = 0xFFFFAB00.toInt()).toJson()
        )

        val deepCircuit = PresetEntity(
            name = "Deep Circuit",
            isBundled = true,
            ambientConfigJson = AmbientConfig(enabled = true, baseSchemeId = "Neutral", minLux = 5f, maxLux = 1000f).toJson(),
            parallaxConfigJson = ParallaxConfig(enabled = true, patternStyle = "CIRCUIT", depth = 0.4f, lineColor = 0xFF00796B.toInt()).toJson(),
            fluidConfigJson = FluidConfig(enabled = true, particleCount = 80, velocityScale = 0.7f, primaryColor = 0xFF00E676.toInt(), accentColor = 0xFFCFD8DC.toInt()).toJson()
        )

        val mysticNeon = PresetEntity(
            name = "Mystic Neon",
            isBundled = true,
            ambientConfigJson = AmbientConfig(enabled = true, baseSchemeId = "Cool", minLux = 2f, maxLux = 900f).toJson(),
            parallaxConfigJson = ParallaxConfig(enabled = true, patternStyle = "GRID", depth = 0.7f, lineColor = 0xFFE040FB.toInt()).toJson(),
            fluidConfigJson = FluidConfig(enabled = true, particleCount = 120, velocityScale = 0.9f, primaryColor = 0xFF651FFF.toInt(), accentColor = 0xFFFF1744.toInt()).toJson()
        )

        dao.insertPreset(arcticNight)
        dao.insertPreset(solarFlare)
        dao.insertPreset(deepCircuit)
        dao.insertPreset(mysticNeon)
    }

    fun toggleEcoMode(enabled: Boolean) {
        prefs.setEcoMode(enabled)
    }

    fun updateAmbientConfig(config: AmbientConfig) {
        prefs.setAmbientConfig(config)
        prefs.setActivePresetId(-1) // Broken preset reference due to manual tweaking
    }

    fun updateParallaxConfig(config: ParallaxConfig) {
        prefs.setParallaxConfig(config)
        prefs.setActivePresetId(-1)
    }

    fun updateFluidConfig(config: FluidConfig) {
        prefs.setFluidConfig(config)
        prefs.setActivePresetId(-1)
    }

    fun saveCustomPreset(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val amb = prefs.getAmbientConfig()
            val para = prefs.getParallaxConfig()
            val flu = prefs.getFluidConfig()

            val newPreset = PresetEntity(
                name = name,
                isBundled = false,
                ambientConfigJson = amb.toJson(),
                parallaxConfigJson = para.toJson(),
                fluidConfigJson = flu.toJson()
            )
            val insertId = dao.insertPreset(newPreset)
            prefs.setActivePresetId(insertId.toInt())
        }
    }

    fun deletePreset(preset: PresetEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deletePreset(preset)
            if (prefs.getActivePresetId() == preset.id) {
                prefs.setActivePresetId(-1)
            }
        }
    }

    fun applyPreset(preset: PresetEntity) {
        val amb = AmbientConfig.fromJson(preset.ambientConfigJson)
        val para = ParallaxConfig.fromJson(preset.parallaxConfigJson)
        val flu = FluidConfig.fromJson(preset.fluidConfigJson)

        prefs.saveAllConfigs(amb, para, flu, preset.id)
    }

    private fun calculateHeuristicBatteryImpact(
        eco: Boolean,
        amb: AmbientConfig,
        para: ParallaxConfig,
        flu: FluidConfig
    ): BatteryImpactEstimate {
        val activeList = mutableListOf<String>()
        var basePowerMilliAmps = 4.2f // Baseline rendering overhead

        if (amb.enabled) {
            activeList.add("Light Shift")
            basePowerMilliAmps += 0.5f // Light sensor poll
        }
        if (para.enabled) {
            activeList.add("Parallax Pattern")
            basePowerMilliAmps += 3.0f // Gyro poll rate
            // Dynamic scale with depth/complexity
            if (para.patternStyle == "HEXAGON" || para.patternStyle == "CIRCUIT") {
                basePowerMilliAmps += 1.5f
            }
        }
        if (flu.enabled) {
            activeList.add("Rising Fluids")
            val pCount = if (eco) (flu.particleCount / 2) else flu.particleCount
            basePowerMilliAmps += (pCount * 0.08f) // Fluid physics computation scaling
        }

        // Eco Mode caps rate to 30 FPS decreasing overall consumption
        if (eco) {
            basePowerMilliAmps *= 0.5f
        }

        val rating = when {
            basePowerMilliAmps < 6.0f -> DrainRating.MINIMAL
            basePowerMilliAmps < 11.0f -> DrainRating.LOW
            basePowerMilliAmps < 18.0f -> DrainRating.MODERATE
            else -> DrainRating.HIGH
        }

        return BatteryImpactEstimate(
            drainRating = rating,
            estimatedMahPerHour = Math.round(basePowerMilliAmps * 10f) / 10f,
            activeEffects = activeList
        )
    }
}
