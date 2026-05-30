package com.example.model

import androidx.compose.ui.graphics.Color

data class AmbientConfig(
    val enabled: Boolean = true,
    val sensitivity: Float = 0.5f,
    val baseSchemeId: String = "Neutral",
    val minLux: Float = 5f,
    val maxLux: Float = 1000f
) {
    fun toJson(): String {
        return """{"enabled":$enabled,"sensitivity":$sensitivity,"baseSchemeId":"$baseSchemeId","minLux":$minLux,"maxLux":$maxLux}"""
    }

    companion object {
        fun fromJson(json: String?): AmbientConfig {
            if (json == null) return AmbientConfig()
            val enabled = json.contains("\"enabled\":true")
            val sensitivity = json.substringAfter("\"sensitivity\":", "0.5").substringBefore(",").toFloatOrNull() ?: 0.5f
            val baseSchemeId = json.substringAfter("\"baseSchemeId\":\"", "Neutral").substringBefore("\"")
            val minLux = json.substringAfter("\"minLux\":", "5").substringBefore(",").substringBefore("}").toFloatOrNull() ?: 5f
            val maxLux = json.substringAfter("\"maxLux\":", "1000").substringBefore(",").substringBefore("}").toFloatOrNull() ?: 1000f
            return AmbientConfig(enabled, sensitivity, baseSchemeId, minLux, maxLux)
        }
    }
}

data class ParallaxConfig(
    val enabled: Boolean = true,
    val patternStyle: String = "GRID", // GRID, HEXAGON, CIRCUIT, DOTS
    val depth: Float = 0.6f,
    val lineColor: Int = 0xFF81D4FA.toInt(), // Light blue
    val lineWidthDp: Float = 1.5f
) {
    fun toJson(): String {
        return """{"enabled":$enabled,"patternStyle":"$patternStyle","depth":$depth,"lineColor":$lineColor,"lineWidthDp":$lineWidthDp}"""
    }

    companion object {
        fun fromJson(json: String?): ParallaxConfig {
            if (json == null) return ParallaxConfig()
            val enabled = json.contains("\"enabled\":true")
            val patternStyle = json.substringAfter("\"patternStyle\":\"", "GRID").substringBefore("\"")
            val depth = json.substringAfter("\"depth\":", "0.6").substringBefore(",").toFloatOrNull() ?: 0.6f
            val lineColor = json.substringAfter("\"lineColor\":", "-8268550").substringBefore(",").substringBefore("}").toLongOrNull()?.toInt() ?: 0xFF81D4FA.toInt()
            val lineWidthDp = json.substringAfter("\"lineWidthDp\":", "1.5").substringBefore(",").substringBefore("}").toFloatOrNull() ?: 1.5f
            return ParallaxConfig(enabled, patternStyle, depth, lineColor, lineWidthDp)
        }
    }
}

data class FluidConfig(
    val enabled: Boolean = true,
    val particleCount: Int = 120,
    val velocityScale: Float = 0.8f,
    val primaryColor: Int = 0xFF26A69A.toInt(), // Teal
    val accentColor: Int = 0xFFFFB74D.toInt(), // Amber
    val glowRadius: Float = 15f
) {
    fun toJson(): String {
        return """{"enabled":$enabled,"particleCount":$particleCount,"velocityScale":$velocityScale,"primaryColor":$primaryColor,"accentColor":$accentColor,"glowRadius":$glowRadius}"""
    }

    companion object {
        fun fromJson(json: String?): FluidConfig {
            if (json == null) return FluidConfig()
            val enabled = json.contains("\"enabled\":true")
            val particleCount = json.substringAfter("\"particleCount\":", "120").substringBefore(",").toIntOrNull() ?: 120
            val velocityScale = json.substringAfter("\"velocityScale\":", "0.8").substringBefore(",").toFloatOrNull() ?: 0.8f
            val primaryColor = json.substringAfter("\"primaryColor\":", "-14244230").substringBefore(",").substringBefore("}").toLongOrNull()?.toInt() ?: 0xFF26A69A.toInt()
            val accentColor = json.substringAfter("\"accentColor\":", "-11571").substringBefore(",").substringBefore("}").toLongOrNull()?.toInt() ?: 0xFFFFB74D.toInt()
            val glowRadius = json.substringAfter("\"glowRadius\":", "15").substringBefore(",").substringBefore("}").toFloatOrNull() ?: 15f
            return FluidConfig(enabled, particleCount, velocityScale, primaryColor, accentColor, glowRadius)
        }
    }
}

enum class ChargingStatus {
    DISCHARGING,
    CHARGING,
    FAST_CHARGING
}

data class BatteryState(
    val status: ChargingStatus = ChargingStatus.DISCHARGING,
    val temperatureCelsius: Float = 28.0f,
    val levelPercent: Int = 50,
    val isOverheating: Boolean = false
)

data class FluidRenderParams(
    val particleCount: Int,
    val velocityScale: Float,
    val primaryColor: Int,
    val accentColor: Int,
    val glowRadius: Float
)

enum class DrainRating {
    MINIMAL,
    LOW,
    MODERATE,
    HIGH
}

data class BatteryImpactEstimate(
    val drainRating: DrainRating,
    val estimatedMahPerHour: Float,
    val activeEffects: List<String>
)
