package com.androgpt.yaser.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "generation_settings")

class GenerationPreferences(private val context: Context) {
    
    companion object {
        private val TEMPERATURE = floatPreferencesKey("temperature")
        private val MAX_TOKENS = intPreferencesKey("max_tokens")
        private val TOP_P = floatPreferencesKey("top_p")
        private val TOP_K = intPreferencesKey("top_k")
        private val REPEAT_PENALTY = floatPreferencesKey("repeat_penalty")
        private val REPEAT_LAST_N = intPreferencesKey("repeat_last_n")
        private val MIN_P = floatPreferencesKey("min_p")
        private val TFS_Z = floatPreferencesKey("tfs_z")
        private val TYPICAL_P = floatPreferencesKey("typical_p")
        private val MIROSTAT = intPreferencesKey("mirostat")
        private val MIROSTAT_TAU = floatPreferencesKey("mirostat_tau")
        private val MIROSTAT_ETA = floatPreferencesKey("mirostat_eta")
        
        // Defaults
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_MAX_TOKENS = 256  // Reduced from 512 to prevent rambling
        const val DEFAULT_TOP_P = 0.9f
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_REPEAT_PENALTY = 1.1f
        const val DEFAULT_REPEAT_LAST_N = 64
        const val DEFAULT_MIN_P = 0.05f
        const val DEFAULT_TFS_Z = 1.0f
        const val DEFAULT_TYPICAL_P = 1.0f
        const val DEFAULT_MIROSTAT = 0
        const val DEFAULT_MIROSTAT_TAU = 5.0f
        const val DEFAULT_MIROSTAT_ETA = 0.1f
    }
    
    data class GenerationSettings(
        val temperature: Float = DEFAULT_TEMPERATURE,
        val maxTokens: Int = DEFAULT_MAX_TOKENS,
        val topP: Float = DEFAULT_TOP_P,
        val topK: Int = DEFAULT_TOP_K,
        val repeatPenalty: Float = DEFAULT_REPEAT_PENALTY,
        val repeatLastN: Int = DEFAULT_REPEAT_LAST_N,
        val minP: Float = DEFAULT_MIN_P,
        val tfsZ: Float = DEFAULT_TFS_Z,
        val typicalP: Float = DEFAULT_TYPICAL_P,
        val mirostat: Int = DEFAULT_MIROSTAT,
        val mirostatTau: Float = DEFAULT_MIROSTAT_TAU,
        val mirostatEta: Float = DEFAULT_MIROSTAT_ETA
    )
    
    suspend fun saveSettings(settings: GenerationSettings) {
        context.settingsDataStore.edit { preferences ->
            preferences[TEMPERATURE] = settings.temperature
            preferences[MAX_TOKENS] = settings.maxTokens
            preferences[TOP_P] = settings.topP
            preferences[TOP_K] = settings.topK
            preferences[REPEAT_PENALTY] = settings.repeatPenalty
            preferences[REPEAT_LAST_N] = settings.repeatLastN
            preferences[MIN_P] = settings.minP
            preferences[TFS_Z] = settings.tfsZ
            preferences[TYPICAL_P] = settings.typicalP
            preferences[MIROSTAT] = settings.mirostat
            preferences[MIROSTAT_TAU] = settings.mirostatTau
            preferences[MIROSTAT_ETA] = settings.mirostatEta
        }
    }
    
    fun getSettings(): Flow<GenerationSettings> {
        return context.settingsDataStore.data.map { preferences ->
            GenerationSettings(
                temperature = preferences[TEMPERATURE] ?: DEFAULT_TEMPERATURE,
                maxTokens = preferences[MAX_TOKENS] ?: DEFAULT_MAX_TOKENS,
                topP = preferences[TOP_P] ?: DEFAULT_TOP_P,
                topK = preferences[TOP_K] ?: DEFAULT_TOP_K,
                repeatPenalty = preferences[REPEAT_PENALTY] ?: DEFAULT_REPEAT_PENALTY,
                repeatLastN = preferences[REPEAT_LAST_N] ?: DEFAULT_REPEAT_LAST_N,
                minP = preferences[MIN_P] ?: DEFAULT_MIN_P,
                tfsZ = preferences[TFS_Z] ?: DEFAULT_TFS_Z,
                typicalP = preferences[TYPICAL_P] ?: DEFAULT_TYPICAL_P,
                mirostat = preferences[MIROSTAT] ?: DEFAULT_MIROSTAT,
                mirostatTau = preferences[MIROSTAT_TAU] ?: DEFAULT_MIROSTAT_TAU,
                mirostatEta = preferences[MIROSTAT_ETA] ?: DEFAULT_MIROSTAT_ETA
            )
        }
    }
    
    suspend fun saveTemperature(value: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[TEMPERATURE] = value
        }
    }
    
    suspend fun saveMaxTokens(value: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[MAX_TOKENS] = value
        }
    }
    
    suspend fun saveTopP(value: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[TOP_P] = value
        }
    }
    
    suspend fun saveTopK(value: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[TOP_K] = value
        }
    }
    
    suspend fun saveRepeatPenalty(value: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[REPEAT_PENALTY] = value
        }
    }
    
    suspend fun saveRepeatLastN(value: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[REPEAT_LAST_N] = value
        }
    }
    
    suspend fun saveMinP(value: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[MIN_P] = value
        }
    }
    
    suspend fun saveTfsZ(value: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[TFS_Z] = value
        }
    }
    
    suspend fun saveTypicalP(value: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[TYPICAL_P] = value
        }
    }
    
    suspend fun saveMirostat(value: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[MIROSTAT] = value
        }
    }
    
    suspend fun saveMirostatTau(value: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[MIROSTAT_TAU] = value
        }
    }
    
    suspend fun saveMirostatEta(value: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[MIROSTAT_ETA] = value
        }
    }
}
