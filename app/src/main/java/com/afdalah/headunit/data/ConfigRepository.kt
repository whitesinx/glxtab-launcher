package com.afdalah.headunit.data

import android.content.Context
import android.net.Uri
import com.afdalah.headunit.Log
import com.afdalah.headunit.mode.Mode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads config.json from the app's external files dir
 * (/sdcard/Android/data/<pkg>/files/config.json) so it can be edited and pushed over ADB.
 * Seeds it from assets/config.json on first run.
 */
class ConfigRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val _config = MutableStateFlow(HeadUnitConfig())
    val config: StateFlow<HeadUnitConfig> = _config.asStateFlow()

    /** Last parse error, shown in Parked settings. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val file: File get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "config.json")

    fun load() {
        val f = file
        if (!f.exists()) {
            runCatching {
                context.assets.open("config.json").use { input -> f.outputStream().use { input.copyTo(it) } }
            }.onFailure { Log.w("Could not seed config.json", it) }
        }
        val text = runCatching { f.readText() }.getOrNull()
        if (text == null) {
            _config.value = HeadUnitConfig()
            return
        }
        parse(text)
            .onSuccess { _config.value = it; _error.value = null }
            .onFailure { _error.value = "config.json invalid, using previous values: ${it.message}" }
    }

    fun parse(text: String): Result<HeadUnitConfig> = runCatching { json.decodeFromString(HeadUnitConfig.serializer(), text) }

    fun save(config: HeadUnitConfig) {
        _config.value = config
        runCatching { file.writeText(json.encodeToString(HeadUnitConfig.serializer(), config)) }
            .onFailure { Log.w("Could not save config.json", it) }
    }

    fun update(transform: (HeadUnitConfig) -> HeadUnitConfig) = save(transform(_config.value))

    fun import(uri: Uri): Result<Unit> = runCatching {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        save(parse(text).getOrThrow())
        _error.value = null
    }

    fun export(uri: Uri): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
            it.write(json.encodeToString(HeadUnitConfig.serializer(), _config.value))
        }
    }

    fun profileFor(mode: Mode): ModeProfile = with(_config.value.profiles) {
        when (mode) {
            Mode.TABLET -> tablet
            Mode.DRIVE -> drive
            Mode.NIGHT -> night
            Mode.PARKED -> parked
            Mode.STANDBY -> standby
        }
    }
}
