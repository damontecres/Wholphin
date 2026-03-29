package com.github.damontecres.wholphin.services

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gets/saves a serializable object by name
 */
@Singleton
class KeyValueService
    @Inject
    constructor(
        val dataStore: DataStore<Preferences>,
    ) {
        val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = false
            }

        inline fun <reified T> get(key: String): Flow<T?> =
            dataStore.data.map { preferences ->
                preferences[stringPreferencesKey(key)]?.let {
                    json.decodeFromString<T>(serializer<T>(), it)
                }
            }

        inline fun <reified T> get(
            key: String,
            defaultValue: T,
        ): Flow<T> =
            dataStore.data.map { preferences ->
                preferences[stringPreferencesKey(key)]?.let {
                    json.decodeFromString<T>(serializer<T>(), it)
                } ?: defaultValue
            }

        inline fun <reified T> get(
            userId: UUID,
            key: String,
            defaultValue: T,
        ): Flow<T> =
            dataStore.data.map { preferences ->
                preferences[stringPreferencesKey("${userId}_$key")]?.let {
                    json.decodeFromString<T>(serializer<T>(), it)
                } ?: defaultValue
            }

        suspend inline fun <reified T> save(
            key: String,
            value: T,
        ) {
            dataStore.updateData { preferences ->
                val valueStr = json.encodeToString(value)
                preferences.toMutablePreferences().apply {
                    set(stringPreferencesKey(key), valueStr)
                }
            }
        }

        suspend inline fun <reified T> save(
            userId: UUID,
            key: String,
            value: T,
        ) {
            dataStore.updateData { preferences ->
                val valueStr = json.encodeToString(value)
                preferences.toMutablePreferences().apply {
                    set(stringPreferencesKey("${userId}_$key"), valueStr)
                }
            }
        }
    }
