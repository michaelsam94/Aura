package com.example.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.AuraWallpaperService
import com.example.data.PreferencesManager
import com.example.data.PresetEntity
import com.example.model.BatteryState
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.draw.scale
import com.example.model.AmbientConfig
import com.example.model.BatteryImpactEstimate
import com.example.model.ChargingStatus
import com.example.model.DrainRating
import com.example.model.FluidConfig
import com.example.model.ParallaxConfig
import com.example.render.WallpaperRenderer
import com.example.viewmodel.UiState
import com.example.viewmodel.WallpaperViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainLayout(viewModel: WallpaperViewModel) {
    var currentTab by remember { mutableStateOf("home") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var savePresetName by remember { mutableStateOf("") }

    val context = LocalContext.current
    val ecoMode by viewModel.ecoMode.collectAsState()
    val activePresetId by viewModel.activePresetId.collectAsState()
    val batteryEstimate by viewModel.batteryImpactEstimate.collectAsState()

    if (currentTab == "preview") {
        PreviewScreen(
            viewModel = viewModel,
            onBack = { currentTab = "home" }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ) {
                    NavigationBarItem(
                        selected = currentTab == "home",
                        onClick = { currentTab = "home" },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home Tab") },
                        label = { Text("Aura") },
                        modifier = Modifier.testTag("nav_home")
                    )
                    NavigationBarItem(
                        selected = currentTab == "presets",
                        onClick = { currentTab = "presets" },
                        icon = { Icon(Icons.Default.Style, contentDescription = "Presets List Tab") },
                        label = { Text("Presets") },
                        modifier = Modifier.testTag("nav_presets")
                    )
                    NavigationBarItem(
                        selected = currentTab == "settings",
                        onClick = { currentTab = "settings" },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings Tab") },
                        label = { Text("Customize") },
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        )
                    )
            ) {
                when (currentTab) {
                    "home" -> HomeScreen(
                        viewModel = viewModel,
                        ecoMode = ecoMode,
                        activePresetId = activePresetId,
                        batteryEstimate = batteryEstimate,
                        onNavigatePresets = { currentTab = "presets" },
                        onNavigateSettings = { currentTab = "settings" },
                        onNavigatePreview = { currentTab = "preview" }
                    )
                    "presets" -> PresetsScreen(
                        viewModel = viewModel,
                        activePresetId = activePresetId,
                        onOpenSaveDialog = {
                            savePresetName = ""
                            showSaveDialog = true
                        }
                    )
                    "settings" -> SettingsScreen(
                        viewModel = viewModel,
                        ecoMode = ecoMode,
                        batteryEstimate = batteryEstimate
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Create Custom Preset") },
            text = {
                Column {
                    Text("Save your current tuning values as a secure local preset theme.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = savePresetName,
                        onValueChange = { savePresetName = it },
                        label = { Text("Preset Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preset_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (savePresetName.isNotBlank()) {
                            viewModel.saveCustomPreset(savePresetName)
                            showSaveDialog = false
                            Toast.makeText(context, "Preset '$savePresetName' saved!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("confirm_save_preset")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ────────────────────────────────────────────────────────────
// HOME SCREEN
// ────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    viewModel: WallpaperViewModel,
    ecoMode: Boolean,
    activePresetId: Int,
    batteryEstimate: BatteryImpactEstimate,
    onNavigatePresets: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigatePreview: () -> Unit
) {
    val context = LocalContext.current
    val ambientConfig by viewModel.ambientConfig.collectAsState()
    val parallaxConfig by viewModel.parallaxConfig.collectAsState()
    val fluidConfig by viewModel.fluidConfig.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Elegant Aura Brand Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AURA",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Offline Sensor Live Wallpaper",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            // Main Central Interactive Preview Launcher Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable { onNavigatePreview() }
                    .shadow(8.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Aesthetic backdrop drawing representations
                    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color(0xFFA6E3E9),
                            radius = 180f,
                            center = androidx.compose.ui.geometry.Offset(size.width - 100f, size.height / 2f)
                        )
                        drawCircle(
                            color = Color(0xFF222831),
                            radius = 290f,
                            center = androidx.compose.ui.geometry.Offset(-80f, size.height + 40f)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Surface(
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(30.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Sensors,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "LIVE SENSOR STACKS ACTIVE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Text(
                                text = "Test-Drive Sensor Aura",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Open full render canvas sandbox. Tilt and charge yours.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        Button(
                            onClick = onNavigatePreview,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimaryContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("launch_test_preview")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Interactive Sandbox", color = MaterialTheme.colorScheme.primaryContainer)
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            // Live Status overview bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusIndicator(
                        icon = Icons.Default.LightMode,
                        label = "Light-Shift",
                        active = ambientConfig.enabled,
                        color = Color(0xFFFFB74D)
                    )
                    Divider(modifier = Modifier.height(24.dp).width(1.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    StatusIndicator(
                        icon = Icons.Default.ScreenRotation,
                        label = "Parallax",
                        active = parallaxConfig.enabled,
                        color = Color(0xFF81D4FA)
                    )
                    Divider(modifier = Modifier.height(24.dp).width(1.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    StatusIndicator(
                        icon = Icons.Default.WaterDrop,
                        label = "Liquids",
                        active = fluidConfig.enabled,
                        color = Color(0xFF26A69A)
                    )
                }
            }
        }

        item {
            // Quick set wallpaper action card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Set System Live Wallpaper",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Register Aura in Android engine to apply to your lock or home screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                    putExtra(
                                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                        ComponentName(context, AuraWallpaperService::class.java)
                                    )
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open system live wallpaper intent.", Toast.LENGTH_LONG).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }

        item {
            // Diagnostic eco mode controller card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = if (ecoMode) Icons.Default.Eco else Icons.Outlined.Eco,
                                contentDescription = null,
                                tint = if (ecoMode) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    text = "Eco Save Power Mode",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (ecoMode) "30 FPS locked, sparse particles." else "Max buttery 60 FPS, high particle density.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = ecoMode,
                            onCheckedChange = { viewModel.toggleEcoMode(it) },
                            modifier = Modifier.testTag("eco_mode_toggle")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Expected Power Consumption", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            color = when (batteryEstimate.drainRating) {
                                DrainRating.MINIMAL -> Color(0xFFE8F5E9)
                                DrainRating.LOW -> Color(0xFFE8F5E9)
                                DrainRating.MODERATE -> Color(0xFFFFF3E0)
                                DrainRating.HIGH -> Color(0xFFFFEBEE)
                            },
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${batteryEstimate.drainRating} (${batteryEstimate.estimatedMahPerHour} mA)",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = when (batteryEstimate.drainRating) {
                                    DrainRating.MINIMAL -> Color(0xFF2E7D32)
                                    DrainRating.LOW -> Color(0xFF2E7D32)
                                    DrainRating.MODERATE -> Color(0xFFEF6C00)
                                    DrainRating.HIGH -> Color(0xFFC62828)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(icon: ImageVector, label: String, active: Boolean, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (active) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ────────────────────────────────────────────────────────────
// PRESETS SCREEN
// ────────────────────────────────────────────────────────────
@Composable
fun PresetsScreen(
    viewModel: WallpaperViewModel,
    activePresetId: Int,
    onOpenSaveDialog: () -> Unit
) {
    val presetsState by viewModel.presets.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Theme Store Library",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Swap combinations or persist custom templates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onOpenSaveDialog,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .size(44.dp)
                    .shadow(2.dp, RoundedCornerShape(12.dp))
                    .testTag("save_preset_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add custom preset")
            }
        }

        when (val state = presetsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Empty -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No theme presets loaded. Tap Add to record custom specs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is UiState.Success -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.data, key = { it.id }) { preset ->
                        PresetItem(
                            preset = preset,
                            isActive = preset.id == activePresetId,
                            onApply = { viewModel.applyPreset(preset) },
                            onDelete = { viewModel.deletePreset(preset) }
                        )
                    }
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Error querying local Room DB: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun PresetItem(
    preset: PresetEntity,
    isActive: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    val outlineColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onApply() }
            .shadow(if (isActive) 4.dp else 1.dp, RoundedCornerShape(16.dp))
            .let { if (isActive) it.shadow(8.dp, RoundedCornerShape(16.dp)) else it },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (preset.isBundled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (preset.isBundled) Icons.Default.Palette else Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (preset.isBundled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (preset.isBundled) {
                            Surface(
                                shape = RoundedCornerShape(30.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Bundled",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (preset.isBundled) "Curated and optimized out-of-the-box system configurations." else "Configured custom user theme parameters layout.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Active Preset Marker",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                if (!preset.isBundled) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_preset_${preset.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete custom preset theme",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
// CUSTOMIZE SETTINGS SCREEN
// ────────────────────────────────────────────────────────────
@Composable
fun SettingsScreen(
    viewModel: WallpaperViewModel,
    ecoMode: Boolean,
    batteryEstimate: BatteryImpactEstimate
) {
    val ambientConfig by viewModel.ambientConfig.collectAsState()
    val parallaxConfig by viewModel.parallaxConfig.collectAsState()
    val fluidConfig by viewModel.fluidConfig.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = "Aura Fine Tuning",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Tread adjustments on specific sensor simulation algorithms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Feature 1: Daylight Ambient Shifting Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Brightness6, contentDescription = null, tint = Color(0xFFFFB74D))
                            Column {
                                Text("Daylight Ambient Shifting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Map lux variations dynamically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = ambientConfig.enabled,
                            onCheckedChange = { viewModel.updateAmbientConfig(ambientConfig.copy(enabled = it)) },
                            modifier = Modifier.testTag("ambient_toggle")
                        )
                    }

                    if (ambientConfig.enabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Palette Selector
                        Text("Color Scheme Fallbacks", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Neutral", "Warm", "Cool", "Arctic").forEach { scheme ->
                                val selected = ambientConfig.baseSchemeId == scheme
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateAmbientConfig(ambientConfig.copy(baseSchemeId = scheme)) },
                                    label = { Text(scheme, fontSize = 11.sp) },
                                    modifier = Modifier.testTag("chip_scheme_$scheme")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Sensitivity
                        Text("Sensor Response Sensitivity (${Math.round(ambientConfig.sensitivity * 100)}%)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = ambientConfig.sensitivity,
                            onValueChange = { viewModel.updateAmbientConfig(ambientConfig.copy(sensitivity = it)) },
                            modifier = Modifier.testTag("ambient_sensitivity_slider")
                        )
                    }
                }
            }
        }

        // Feature 2: Parallax Gyro Canvas Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color(0xFF81D4FA))
                            Column {
                                Text("Parallax Gyro Canvas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("3-D dynamic tilt translations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = parallaxConfig.enabled,
                            onCheckedChange = { viewModel.updateParallaxConfig(parallaxConfig.copy(enabled = it)) },
                            modifier = Modifier.testTag("parallax_toggle")
                        )
                    }

                    if (parallaxConfig.enabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Style Selector
                        Text("Vector Geometric Style", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("GRID", "HEXAGON", "CIRCUIT", "DOTS").forEach { style ->
                                val selected = parallaxConfig.patternStyle == style
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateParallaxConfig(parallaxConfig.copy(patternStyle = style)) },
                                    label = { Text(style, fontSize = 11.sp) },
                                    modifier = Modifier.testTag("chip_style_$style")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Parallax Depth
                        Text("Tilt Offset Parallax Depth (${Math.round(parallaxConfig.depth * 100)}%)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = parallaxConfig.depth,
                            onValueChange = { viewModel.updateParallaxConfig(parallaxConfig.copy(depth = it)) },
                            modifier = Modifier.testTag("parallax_depth_slider")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Line stroke width slider
                        Text("Line Thickness (${parallaxConfig.lineWidthDp} dp)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = parallaxConfig.lineWidthDp,
                            onValueChange = { viewModel.updateParallaxConfig(parallaxConfig.copy(lineWidthDp = it)) },
                            valueRange = 0.5f..4.0f,
                            modifier = Modifier.testTag("parallax_width_slider")
                        )
                    }
                }
            }
        }

        // Feature 3: Charger Fluid Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Water, contentDescription = null, tint = Color(0xFF26A69A))
                            Column {
                                Text("Liquid Charge Current", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Advection simulation for charging", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = fluidConfig.enabled,
                            onCheckedChange = { viewModel.updateFluidConfig(fluidConfig.copy(enabled = it)) },
                            modifier = Modifier.testTag("fluid_toggle")
                        )
                    }

                    if (fluidConfig.enabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Particle Limit
                        Text("Active Energy Particle Count ($fluidConfig.particleCount)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = fluidConfig.particleCount.toFloat(),
                            onValueChange = { viewModel.updateFluidConfig(fluidConfig.copy(particleCount = it.toInt())) },
                            valueRange = 30f..200f,
                            modifier = Modifier.testTag("fluid_particle_slider")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Velocity
                        Text("Liquids Movement Speed multiplier (${Math.round(fluidConfig.velocityScale * 100)}%)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = fluidConfig.velocityScale,
                            onValueChange = { viewModel.updateFluidConfig(fluidConfig.copy(velocityScale = it)) },
                            valueRange = 0.2f..2.0f,
                            modifier = Modifier.testTag("fluid_velocity_slider")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Glow radius
                        Text("Emitter Sprite Glow Radius (${Math.round(fluidConfig.glowRadius)} px)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = fluidConfig.glowRadius,
                            onValueChange = { viewModel.updateFluidConfig(fluidConfig.copy(glowRadius = it)) },
                            valueRange = 5f..50f,
                            modifier = Modifier.testTag("fluid_glow_slider")
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
// INTERACTIVE LIVE SANDBOX PREVIEW SCREEN
// ────────────────────────────────────────────────────────────
@Composable
fun PreviewScreen(
    viewModel: WallpaperViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val testRenderer = remember { WallpaperRenderer(context) }
    val scope = rememberCoroutineScope()

    var isLiveChargingState by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("preview_canvas_wrapper")
    ) {
        // Dedicated Hardware Accelerated SurfaceView wrapper
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        private var renderJob: Job? = null
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            renderJob = scope.launch(Dispatchers.Default) {
                                testRenderer.start(scope)
                                // Keep simulation running as long as surface stays valid
                                val prefs = PreferencesManager.getInstance(ctx)
                                val baseDelay = if (prefs.getEcoMode()) 33L else 16L
                                while (holder.surface.isValid) {
                                    val start = System.currentTimeMillis()
                                    testRenderer.tickPhysics(width, height)
                                    var canvas: Canvas? = null
                                    try {
                                        canvas = holder.lockCanvas()
                                        if (canvas != null) {
                                            testRenderer.drawFrame(canvas, width, height)
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
                                    val elapsed = System.currentTimeMillis() - start
                                    delay((baseDelay - elapsed).coerceAtLeast(1L))
                                }
                            }
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            renderJob?.cancel()
                            renderJob = null
                            testRenderer.stop()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Floating overlay toolbar at bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Interactive sensor emulator buttons to force simulator triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive trigger mock buttons to force testing vectors
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.shadow(4.dp, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Mock Charging State:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(
                            checked = isLiveChargingState,
                            onCheckedChange = {
                                isLiveChargingState = it
                                // Manually mock the rendering parameters
                                val field = testRenderer.javaClass.getDeclaredField("_batteryState").apply {
                                    isAccessible = true
                                }
                                @Suppress("UNCHECKED_CAST")
                                val stateFlow = field.get(testRenderer) as MutableStateFlow<BatteryState>
                                stateFlow.value = BatteryState(
                                    status = if (it) ChargingStatus.FAST_CHARGING else ChargingStatus.DISCHARGING,
                                    isOverheating = false
                                )
                            },
                            modifier = Modifier.scale(0.7f).testTag("sandbox_charging_toggle")
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_from_preview")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back to navigation dashboard")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sensory Render Sandbox", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Tilt and rotate your device.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = {
                            try {
                                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                    putExtra(
                                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                        ComponentName(context, AuraWallpaperService::class.java)
                                    )
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open system live wallpaper setting launcher.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply Live")
                    }
                }
            }
        }
    }
}
