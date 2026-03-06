@file:Suppress("DEPRECATION")

package com.opensubsonic.client.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opensubsonic.client.service.AudioEffectManager

@Composable
fun SoundEffectsScreen(
    audioEffectManager: AudioEffectManager,
    onBack: () -> Unit
) {
    val state by audioEffectManager.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "SOUND EFFECTS",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Bass Boost
        item {
            EffectSection(
                title = "BASS BOOST",
                enabled = state.bassBoostEnabled,
                onEnabledChange = { audioEffectManager.setBassBoostEnabled(it) }
            ) {
                Text(
                    text = "Strength",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = state.bassBoostStrength / 1000f,
                    onValueChange = { audioEffectManager.setBassBoostStrength((it * 1000).toInt()) },
                    enabled = state.bassBoostEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Light", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Heavy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Equalizer
        item {
            EffectSection(
                title = "EQUALIZER",
                enabled = state.equalizerEnabled,
                onEnabledChange = { audioEffectManager.setEqualizerEnabled(it) }
            ) {
                // Preset selector
                if (state.equalizerPresets.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            enabled = state.equalizerEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(state.equalizerPreset ?: "Custom")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            state.equalizerPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset) },
                                    onClick = {
                                        audioEffectManager.setEqualizerPreset(preset)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Band sliders
                if (state.equalizerBands.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        state.equalizerBands.forEach { band ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            ) {
                                Text(
                                    text = "${band.level / 100}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                // Vertical slider simulated with rotated slider
                                VerticalSlider(
                                    value = (band.level - state.equalizerMinLevel).toFloat() /
                                            (state.equalizerMaxLevel - state.equalizerMinLevel).toFloat(),
                                    onValueChange = { fraction ->
                                        val level = (state.equalizerMinLevel +
                                                (fraction * (state.equalizerMaxLevel - state.equalizerMinLevel))).toInt()
                                        audioEffectManager.setEqualizerBandLevel(band.index, level)
                                    },
                                    enabled = state.equalizerEnabled,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = formatFreq(band.centerFreq),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${state.equalizerMinLevel / 100} dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "+${state.equalizerMaxLevel / 100} dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Crossfeed
        item {
            EffectSection(
                title = "CROSSFEED",
                subtitle = "Blend stereo channels for a more natural sound",
                enabled = state.crossfeedEnabled,
                onEnabledChange = { audioEffectManager.setCrossfeedEnabled(it) }
            ) {
                Text(
                    text = "Level",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = state.crossfeedLevel / 100f,
                    onValueChange = { audioEffectManager.setCrossfeedLevel((it * 100).toInt()) },
                    enabled = state.crossfeedEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subtle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Strong", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Surround
        item {
            EffectSection(
                title = "SURROUND",
                subtitle = "Add spatial reverb for a wider soundstage",
                enabled = state.surroundEnabled,
                onEnabledChange = { audioEffectManager.setSurroundEnabled(it) }
            ) {
                Text(
                    text = "Level",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = state.surroundLevel / 100f,
                    onValueChange = { audioEffectManager.setSurroundLevel((it * 100).toInt()) },
                    enabled = state.surroundEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Subtle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Strong", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EffectSection(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            if (enabled) {
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Use a regular Slider rotated for vertical layout
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .width(140.dp)
                .graphicsLayer {
                    rotationZ = -90f
                }
        )
    }
}

private fun formatFreq(hz: Int): String {
    return if (hz >= 1000) "${hz / 1000}k" else "${hz}"
}
