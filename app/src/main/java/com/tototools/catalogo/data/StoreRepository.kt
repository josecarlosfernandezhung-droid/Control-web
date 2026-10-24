package com.tototools.catalogo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "catalogo")

class StoreRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val KEY_STATE = stringPreferencesKey("app_state")

    val stateFlow: Flow<AppState> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_STATE]
        if (raw.isNullOrBlank()) {
            val st = Store(nombre = "Mi tienda")
            AppState(stores = listOf(st), currentStoreId = st.id)
        } else {
            try {
                json.decodeFromString<AppState>(raw)
            } catch (e: Exception) {
                val st = Store(nombre = "Mi tienda")
                AppState(stores = listOf(st), currentStoreId = st.id)
            }
        }
    }

    suspend fun getState(): AppState = stateFlow.first()

    suspend fun save(state: AppState) {
        context.dataStore.edit { prefs ->
            prefs[KEY_STATE] = json.encodeToString(state)
        }
    }

    suspend fun update(block: (AppState) -> AppState) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_STATE]?.let {
                try { json.decodeFromString<AppState>(it) } catch (_: Exception) { null }
            } ?: run {
                val st = Store(nombre = "Mi tienda")
                AppState(stores = listOf(st), currentStoreId = st.id)
            }
            prefs[KEY_STATE] = json.encodeToString(block(current))
        }
    }

    suspend fun updateCurrentStore(block: (Store) -> Store) {
        update { state ->
            val id = state.currentStoreId ?: state.stores.firstOrNull()?.id
            state.copy(stores = state.stores.map { if (it.id == id) block(it) else it })
        }
    }
}
