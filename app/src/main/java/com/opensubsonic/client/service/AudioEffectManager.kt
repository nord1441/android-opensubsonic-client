package com.opensubsonic.client.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerBand(
    val index: Int,
    val centerFreq: Int, // Hz
    val level: Int // millibels
)

data class AudioEffectState(
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 500, // 0-1000
    val equalizerEnabled: Boolean = false,
    val equalizerBands: List<EqualizerBand> = emptyList(),
    val equalizerMinLevel: Int = -1500,
    val equalizerMaxLevel: Int = 1500,
    val equalizerPreset: String? = null,
    val equalizerPresets: List<String> = emptyList(),
    val crossfeedEnabled: Boolean = false,
    val crossfeedLevel: Int = 50, // 0-100 percentage
    val surroundEnabled: Boolean = false,
    val surroundLevel: Int = 50 // 0-100 percentage
)

@Singleton
class AudioEffectManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(AudioEffectState())
    val state: StateFlow<AudioEffectState> = _state

    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null
    private var reverb: PresetReverb? = null
    private var currentAudioSessionId: Int = 0

    companion object {
        private val KEY_BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
        private val KEY_BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength")
        private val KEY_EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        private val KEY_EQ_PRESET = stringPreferencesKey("eq_preset")
        private val KEY_CROSSFEED_ENABLED = booleanPreferencesKey("crossfeed_enabled")
        private val KEY_CROSSFEED_LEVEL = intPreferencesKey("crossfeed_level")
        private val KEY_SURROUND_ENABLED = booleanPreferencesKey("surround_enabled")
        private val KEY_SURROUND_LEVEL = intPreferencesKey("surround_level")

        private fun eqBandKey(index: Int) = intPreferencesKey("eq_band_$index")
    }

    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId == currentAudioSessionId && audioSessionId != 0) return
        releaseEffects()
        currentAudioSessionId = audioSessionId
        if (audioSessionId == 0) return

        try {
            bassBoost = BassBoost(0, audioSessionId)
            equalizer = Equalizer(0, audioSessionId)
            reverb = PresetReverb(0, audioSessionId)
        } catch (_: Exception) {
            // Some devices don't support all effects
        }

        // Load saved state and apply
        scope.launch {
            loadSavedState()
            applyAllEffects()
        }
    }

    private suspend fun loadSavedState() {
        val prefs = dataStore.data.first()
        val eq = equalizer

        val bands = mutableListOf<EqualizerBand>()
        val presets = mutableListOf<String>()
        var minLevel = -1500
        var maxLevel = 1500

        if (eq != null) {
            minLevel = eq.bandLevelRange[0].toInt()
            maxLevel = eq.bandLevelRange[1].toInt()
            for (i in 0 until eq.numberOfBands) {
                val savedLevel = prefs[eqBandKey(i)]
                bands.add(
                    EqualizerBand(
                        index = i,
                        centerFreq = eq.getCenterFreq(i.toShort()) / 1000,
                        level = savedLevel ?: eq.getBandLevel(i.toShort()).toInt()
                    )
                )
            }
            for (i in 0 until eq.numberOfPresets) {
                presets.add(eq.getPresetName(i.toShort()))
            }
        }

        _state.value = AudioEffectState(
            bassBoostEnabled = prefs[KEY_BASS_BOOST_ENABLED] ?: false,
            bassBoostStrength = prefs[KEY_BASS_BOOST_STRENGTH] ?: 500,
            equalizerEnabled = prefs[KEY_EQ_ENABLED] ?: false,
            equalizerBands = bands,
            equalizerMinLevel = minLevel,
            equalizerMaxLevel = maxLevel,
            equalizerPreset = prefs[KEY_EQ_PRESET],
            equalizerPresets = presets,
            crossfeedEnabled = prefs[KEY_CROSSFEED_ENABLED] ?: false,
            crossfeedLevel = prefs[KEY_CROSSFEED_LEVEL] ?: 50,
            surroundEnabled = prefs[KEY_SURROUND_ENABLED] ?: false,
            surroundLevel = prefs[KEY_SURROUND_LEVEL] ?: 50
        )
    }

    private fun applyAllEffects() {
        val s = _state.value
        applyBassBoost(s.bassBoostEnabled, s.bassBoostStrength)
        applyEqualizer(s.equalizerEnabled, s.equalizerBands)
        applySurround(s.surroundEnabled, s.surroundLevel)
        // Crossfeed is applied as EQ-based stereo narrowing via the reverb
        applyCrossfeed(s.crossfeedEnabled, s.crossfeedLevel)
    }

    // --- Bass Boost ---

    fun setBassBoostEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(bassBoostEnabled = enabled)
        applyBassBoost(enabled, _state.value.bassBoostStrength)
        scope.launch { dataStore.edit { it[KEY_BASS_BOOST_ENABLED] = enabled } }
    }

    fun setBassBoostStrength(strength: Int) {
        _state.value = _state.value.copy(bassBoostStrength = strength)
        applyBassBoost(_state.value.bassBoostEnabled, strength)
        scope.launch { dataStore.edit { it[KEY_BASS_BOOST_STRENGTH] = strength } }
    }

    private fun applyBassBoost(enabled: Boolean, strength: Int) {
        try {
            bassBoost?.let {
                it.setStrength(strength.toShort())
                it.enabled = enabled
            }
        } catch (_: Exception) {}
    }

    // --- Equalizer ---

    fun setEqualizerEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(equalizerEnabled = enabled)
        applyEqualizer(enabled, _state.value.equalizerBands)
        scope.launch { dataStore.edit { it[KEY_EQ_ENABLED] = enabled } }
    }

    fun setEqualizerBandLevel(bandIndex: Int, level: Int) {
        val bands = _state.value.equalizerBands.toMutableList()
        val idx = bands.indexOfFirst { it.index == bandIndex }
        if (idx >= 0) {
            bands[idx] = bands[idx].copy(level = level)
            _state.value = _state.value.copy(equalizerBands = bands, equalizerPreset = null)
            applyEqualizer(_state.value.equalizerEnabled, bands)
            scope.launch {
                dataStore.edit {
                    it[eqBandKey(bandIndex)] = level
                    it.remove(KEY_EQ_PRESET)
                }
            }
        }
    }

    fun setEqualizerPreset(presetName: String) {
        val eq = equalizer ?: return
        val presetIndex = _state.value.equalizerPresets.indexOf(presetName)
        if (presetIndex < 0) return

        try {
            eq.usePreset(presetIndex.toShort())
        } catch (_: Exception) { return }

        // Read back the band levels from the preset
        val bands = mutableListOf<EqualizerBand>()
        for (i in 0 until eq.numberOfBands) {
            bands.add(
                EqualizerBand(
                    index = i,
                    centerFreq = eq.getCenterFreq(i.toShort()) / 1000,
                    level = eq.getBandLevel(i.toShort()).toInt()
                )
            )
        }
        _state.value = _state.value.copy(equalizerBands = bands, equalizerPreset = presetName)

        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_EQ_PRESET] = presetName
                bands.forEach { band ->
                    prefs[eqBandKey(band.index)] = band.level
                }
            }
        }
    }

    private fun applyEqualizer(enabled: Boolean, bands: List<EqualizerBand>) {
        try {
            equalizer?.let { eq ->
                bands.forEach { band ->
                    eq.setBandLevel(band.index.toShort(), band.level.toShort())
                }
                eq.enabled = enabled
            }
        } catch (_: Exception) {}
    }

    // --- Crossfeed ---
    // Simulated using a subtle reverb to blend stereo channels

    fun setCrossfeedEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(crossfeedEnabled = enabled)
        applyCrossfeed(enabled, _state.value.crossfeedLevel)
        scope.launch { dataStore.edit { it[KEY_CROSSFEED_ENABLED] = enabled } }
    }

    fun setCrossfeedLevel(level: Int) {
        _state.value = _state.value.copy(crossfeedLevel = level)
        applyCrossfeed(_state.value.crossfeedEnabled, level)
        scope.launch { dataStore.edit { it[KEY_CROSSFEED_LEVEL] = level } }
    }

    private fun applyCrossfeed(enabled: Boolean, level: Int) {
        try {
            reverb?.let {
                if (enabled && !_state.value.surroundEnabled) {
                    // Use a small room reverb to simulate crossfeed blending
                    val preset = when {
                        level < 33 -> PresetReverb.PRESET_SMALLROOM
                        level < 66 -> PresetReverb.PRESET_MEDIUMROOM
                        else -> PresetReverb.PRESET_LARGEROOM
                    }
                    it.preset = preset
                    it.enabled = true
                } else if (!_state.value.surroundEnabled) {
                    it.enabled = false
                }
            }
        } catch (_: Exception) {}
    }

    // --- Surround ---
    // Simulated using reverb presets for spatial effect

    fun setSurroundEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(surroundEnabled = enabled)
        applySurround(enabled, _state.value.surroundLevel)
        scope.launch { dataStore.edit { it[KEY_SURROUND_ENABLED] = enabled } }
    }

    fun setSurroundLevel(level: Int) {
        _state.value = _state.value.copy(surroundLevel = level)
        applySurround(_state.value.surroundEnabled, level)
        scope.launch { dataStore.edit { it[KEY_SURROUND_LEVEL] = level } }
    }

    private fun applySurround(enabled: Boolean, level: Int) {
        try {
            reverb?.let {
                if (enabled) {
                    val preset = when {
                        level < 25 -> PresetReverb.PRESET_MEDIUMHALL
                        level < 50 -> PresetReverb.PRESET_LARGEHALL
                        level < 75 -> PresetReverb.PRESET_PLATE
                        else -> PresetReverb.PRESET_LARGEHALL
                    }
                    it.preset = preset
                    it.enabled = true
                } else if (!_state.value.crossfeedEnabled) {
                    it.enabled = false
                } else {
                    // Re-apply crossfeed if it's enabled
                    applyCrossfeed(true, _state.value.crossfeedLevel)
                }
            }
        } catch (_: Exception) {}
    }

    private fun releaseEffects() {
        try { bassBoost?.release() } catch (_: Exception) {}
        try { equalizer?.release() } catch (_: Exception) {}
        try { reverb?.release() } catch (_: Exception) {}
        bassBoost = null
        equalizer = null
        reverb = null
    }

    fun release() {
        releaseEffects()
        currentAudioSessionId = 0
    }
}
