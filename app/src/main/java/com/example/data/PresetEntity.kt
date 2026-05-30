package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isBundled: Boolean,
    val ambientConfigJson: String,
    val parallaxConfigJson: String,
    val fluidConfigJson: String,
    val createdAt: Long = System.currentTimeMillis()
)
