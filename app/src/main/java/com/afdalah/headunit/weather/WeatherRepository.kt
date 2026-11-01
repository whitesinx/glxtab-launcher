package com.afdalah.headunit.weather

import android.location.Location
import com.afdalah.headunit.BuildConfig
import com.afdalah.headunit.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Outside air temperature and air quality (US AQI) at the car's GPS position, from Open-Meteo (free, no API key).
 * Polled every 15 minutes, only while a head-unit mode runs ([start]/[stop]).
 */
class WeatherRepository(
    private val location: StateFlow<Location?>,
    private val scope: CoroutineScope,
) {
    private val _outsideC = MutableStateFlow<Float?>(null)
    val outsideC: StateFlow<Float?> = _outsideC.asStateFlow()

    /** US AQI (0-500) at the car's position. */
    private val _aqi = MutableStateFlow<Int?>(null)
    val aqi: StateFlow<Int?> = _aqi.asStateFlow()

    private var job: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val loc = location.filterNotNull().first()
                val temp = withContext(Dispatchers.IO) { fetchTemperature(loc.latitude, loc.longitude) }
                val aqi = withContext(Dispatchers.IO) { fetchAqi(loc.latitude, loc.longitude) }
                if (temp != null) _outsideC.value = temp
                if (aqi != null) _aqi.value = aqi
                delay(if (temp != null && aqi != null) REFRESH_MS else RETRY_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun fetchTemperature(lat: Double, lon: Double): Float? = runCatching {
        json.decodeFromString<WeatherResponse>(
            get("https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m", lat, lon),
        ).current.temperature
    }.onFailure { Log.w("Weather lookup failed: ${it.message}") }.getOrNull()

    private fun fetchAqi(lat: Double, lon: Double): Int? = runCatching {
        json.decodeFromString<AirResponse>(
            get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.3f&longitude=%.3f&current=us_aqi", lat, lon),
        ).current.usAqi?.roundToInt()
    }.onFailure { Log.w("Air quality lookup failed: ${it.message}") }.getOrNull()

    private fun get(format: String, lat: Double, lon: Double): String {
        val conn = URL(String.format(Locale.US, format, lat, lon)).openConnection() as HttpURLConnection
        conn.connectTimeout = 6_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", "HeadUnit/${BuildConfig.VERSION_NAME} (personal car launcher)")
        return try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    @Serializable private data class WeatherResponse(val current: WeatherCurrent)
    @Serializable private data class WeatherCurrent(@SerialName("temperature_2m") val temperature: Float)
    @Serializable private data class AirResponse(val current: AirCurrent)
    @Serializable private data class AirCurrent(@SerialName("us_aqi") val usAqi: Float? = null)

    private companion object {
        const val REFRESH_MS = 15 * 60_000L
        const val RETRY_MS = 60_000L
    }
}
