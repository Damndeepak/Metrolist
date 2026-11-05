// DataStore.kt

package com.mochyy.mochymusic.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.mochyy.mochymusic.deeplist.DeeplistSyncEnabledKey
import com.mochyy.mochymusic.deeplist.DeeplistWebsiteKey
import com.mochyy.mochymusic.deeplist.DeeplistTokenKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.mochyy.mochymusic.extensions.toEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.properties.ReadOnlyProperty

internal const val NativeSettingsName = "mochy-settings"
internal const val NativeSettingsFileName = "mochy-settings.preferences_pb"

private val Context.legacyDeeplistSettings: DataStore<Preferences> by preferencesDataStore(name = "settings")

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = NativeSettingsName,
    produceMigrations = { context -> listOf(LegacyDeeplistPairingMigration(context.applicationContext)) },
)

internal val LegacyPairingMigratedKey = booleanPreferencesKey("deeplistLegacyPairingMigrated")

internal fun needsLegacyPairingMigration(current: Preferences, databaseVersion: Int?): Boolean =
    current[LegacyPairingMigratedKey] != true && databaseVersion == 38

internal fun copyLegacyDeeplistPairing(current: Preferences, legacy: Preferences): Preferences {
    if (current[LegacyPairingMigratedKey] == true) return current
    val result = current.toMutablePreferences()
    // Keep new Mochy choices intact; migrate only the separate website connection, never old visuals.
    if (listOf(DeeplistSyncEnabledKey, DeeplistWebsiteKey, DeeplistTokenKey).none { current.asMap().containsKey(it) }) {
        val website = legacy.asMap()[DeeplistWebsiteKey] as? String
        val token = legacy.asMap()[DeeplistTokenKey] as? String
        if (website?.trimEnd('/') == "https://world.damndeepak.workers.dev" &&
            token?.matches(Regex("[a-f0-9]{64}")) == true) {
            result[DeeplistWebsiteKey] = "https://world.damndeepak.workers.dev"
            result[DeeplistTokenKey] = token
            result[DeeplistSyncEnabledKey] = legacy.asMap()[DeeplistSyncEnabledKey] as? Boolean ?: false
        }
    }
    result[LegacyPairingMigratedKey] = true
    return result
}

private class LegacyDeeplistPairingMigration(private val context: Context) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        if (currentData[LegacyPairingMigratedKey] == true) return false
        val source = context.getDatabasePath("song.db")
        val version = if (source.isFile) SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version } else null
        return needsLegacyPairingMigration(currentData, version)
    }

    override suspend fun migrate(currentData: Preferences): Preferences =
        copyLegacyDeeplistPairing(currentData, context.legacyDeeplistSettings.data.first())

    // The old database and settings are retained for recovery and manual export.
    override suspend fun cleanUp() = Unit
}

object PreferencesCache {

    private val snapshotState = mutableStateOf(emptyPreferences())

    @Volatile
    private var plain: Preferences = emptyPreferences()

    @Volatile
    private var warm: Boolean = false

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val snapshot: State<Preferences> get() = snapshotState

    fun start(context: Context, scope: CoroutineScope) {
        val store = context.applicationContext.dataStore
        scope.launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            store.data.collect { prefs ->
                plain = prefs
                warm = true
                snapshotState.value = prefs
            }
        }
    }

    fun current(store: DataStore<Preferences>): Preferences {
        if (warm) return plain
        val prefs = runBlocking(Dispatchers.IO) { store.data.first() }
        plain = prefs
        warm = true
        snapshotState.value = prefs
        return prefs
    }

    fun <T> set(store: DataStore<Preferences>, key: Preferences.Key<T>, value: T) {

        writeScope.launch {
            store.edit { it[key] = value }
        }
    }
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    PreferencesCache.current(this)[key]

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = PreferencesCache.current(this)[key] ?: defaultValue

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val store = LocalContext.current.applicationContext.dataStore

    PreferencesCache.current(store)

    val valueState = remember(key, defaultValue) {
        derivedStateOf { PreferencesCache.snapshot.value[key] ?: defaultValue }
    }

    return remember(key, store) {
        object : MutableState<T> {
            override var value: T
                get() = valueState.value
                set(value) = PreferencesCache.set(store, key, value)

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val store = LocalContext.current.applicationContext.dataStore
    PreferencesCache.current(store)

    val valueState = remember(key, defaultValue) {
        derivedStateOf { PreferencesCache.snapshot.value[key].toEnum(defaultValue) }
    }

    return remember(key, store) {
        object : MutableState<T> {
            override var value: T
                get() = valueState.value
                set(value) = PreferencesCache.set(store, key, value.name)

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
