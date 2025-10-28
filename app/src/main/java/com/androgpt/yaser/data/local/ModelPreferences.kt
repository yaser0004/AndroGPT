package com.androgpt.yaser.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "model_preferences")

class ModelPreferences(private val context: Context) {
    
    companion object {
        private val MODEL_NAME = stringPreferencesKey("loaded_model_name")
        private val MODEL_PATH = stringPreferencesKey("loaded_model_path")
        private val MODEL_SIZE = longPreferencesKey("loaded_model_size")
    }
    
    data class LoadedModelInfo(
        val name: String,
        val filePath: String,
        val size: Long
    )
    
    suspend fun saveLoadedModel(name: String, filePath: String, size: Long) {
        context.dataStore.edit { preferences ->
            preferences[MODEL_NAME] = name
            preferences[MODEL_PATH] = filePath
            preferences[MODEL_SIZE] = size
        }
    }
    
    fun getLoadedModel(): Flow<LoadedModelInfo?> {
        return context.dataStore.data.map { preferences ->
            val name = preferences[MODEL_NAME]
            val path = preferences[MODEL_PATH]
            val size = preferences[MODEL_SIZE]
            
            if (name != null && path != null && size != null) {
                LoadedModelInfo(name, path, size)
            } else {
                null
            }
        }
    }
    
    suspend fun clearLoadedModel() {
        context.dataStore.edit { preferences ->
            preferences.remove(MODEL_NAME)
            preferences.remove(MODEL_PATH)
            preferences.remove(MODEL_SIZE)
        }
    }
}
