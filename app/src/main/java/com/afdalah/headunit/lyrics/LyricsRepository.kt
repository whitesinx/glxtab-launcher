package com.afdalah.headunit.lyrics

import com.afdalah.headunit.BuildConfig
import com.afdalah.headunit.Log
import com.afdalah.headunit.media.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

sealed interface Lyrics {
    data object None : Lyrics
    data object Loading : Lyrics
    data object NotFound : Lyrics
    data object Instrumental : Lyrics
    /** LRCLIB unreachable or overloaded after retries. */
    data object Unavailable : Lyrics
    data class Ready(val lines: List<LyricLine>, val synced: Boolean) : Lyrics
}

/**
 * Lyrics for the current track from LRCLIB (lrclib.net: free, no account, time-synced LRC).
 * Only the title, artist, album and duration are sent. Results are cached per track for the process.
 */
private val RETRY_DELAYS_MS = listOf(3_000L, 8_000L, 20_000L, 45_000L)

class LyricsRepository(media: MediaRepository, scope: CoroutineScope) {

    private val _lyrics = MutableStateFlow<Lyrics>(Lyrics.None)
    val lyrics: StateFlow<Lyrics> = _lyrics.asStateFlow()

    private val cache = object : LinkedHashMap<String, Lyrics>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?) = size > 50
    }
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Record(
        val trackName: String? = null,
        val artistName: String? = null,
        val duration: Double? = null,
        val instrumental: Boolean = false,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )

    private data class Track(val title: String, val artist: String, val album: String, val durationMs: Long)

    init {
        scope.launch {
            media.nowPlaying
                .map { np -> np?.takeIf { it.title.isNotBlank() }?.let { Track(it.title, it.artist, it.album, it.durationMs) } }
                .distinctUntilChanged { a, b -> a?.title == b?.title && a?.artist == b?.artist }
                .collectLatest { t ->
                    if (t == null) { _lyrics.value = Lyrics.None; return@collectLatest }
                    val key = "${t.artist}|${t.title}"
                    cache[key]?.let { _lyrics.value = it; return@collectLatest }
                    _lyrics.value = Lyrics.Loading
                    // LRCLIB answers 503 "ServerOverloaded" at busy times: back off and retry while the song plays.
                    var result: Lyrics? = null
                    for (wait in RETRY_DELAYS_MS) {
                        result = withContext(Dispatchers.IO) { fetch(t) }
                        if (result != null) break
                        _lyrics.value = Lyrics.Unavailable
                        delay(wait)
                        _lyrics.value = Lyrics.Loading
                    }
                    if (result != null) cache[key] = result
                    _lyrics.value = result ?: Lyrics.Unavailable
                }
        }
    }

    /** null = network error (not cached, retried next time the track plays). */
    private fun fetch(t: Track): Lyrics? = runCatching {
        val secs = t.durationMs / 1000
        val title = Lrc.cleanTitle(t.title)
        val artist = Lrc.firstArtist(t.artist)
        // Each step may fail on its own (the exact-match endpoint returns 503 at times); only all failing is an error.
        var failures = 0
        fun <T> attempt(block: () -> T?): T? = runCatching(block).onFailure { failures++; Log.d("LRCLIB: ${it.message}") }.getOrNull()
        // Search (served from LRCLIB's database) first; the exact-match endpoint may query external sources.
        val candidates = buildList {
            attempt { get("/api/search?" + q("track_name" to title, "artist_name" to artist)) }?.let { addAll(json.decodeFromString<List<Record>>(it)) }
            if (none { it.syncedLyrics != null } && secs > 0) {
                attempt { get("/api/get?" + q("track_name" to t.title, "artist_name" to t.artist, "album_name" to t.album, "duration" to "$secs")) }
                    ?.let { add(json.decodeFromString<Record>(it)) }
            }
            if (isEmpty()) attempt { get("/api/search?" + q("q" to "$title $artist")) }?.let { addAll(json.decodeFromString<List<Record>>(it)) }
        }
        if (candidates.isEmpty() && failures > 0) error("$failures request(s) failed")
        // Prefer synced lyrics with a matching length (same recording).
        val best = candidates.sortedWith(
            compareBy<Record> { it.syncedLyrics == null }
                .thenBy { if (secs > 0 && it.duration != null) abs(it.duration - secs) else 0.0 },
        ).firstOrNull { it.syncedLyrics != null || it.plainLyrics != null || it.instrumental }
        when {
            best == null -> Lyrics.NotFound
            best.instrumental && best.syncedLyrics == null && best.plainLyrics == null -> Lyrics.Instrumental
            best.syncedLyrics != null -> Lyrics.Ready(Lrc.parse(best.syncedLyrics), synced = true)
            else -> Lyrics.Ready(Lrc.plain(best.plainLyrics!!), synced = false)
        }
    }.onFailure { Log.w("Lyrics lookup failed: ${it.message}") }.getOrNull()

    private fun q(vararg p: Pair<String, String>) =
        p.filter { it.second.isNotBlank() }.joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, "UTF-8")}" }

    /** Body on 200, null on 404, throws on network errors. */
    private fun get(path: String): String? {
        val conn = URL("https://lrclib.net$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 6_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", "HeadUnit/${BuildConfig.VERSION_NAME} (personal car launcher)")
        return try {
            when (conn.responseCode) {
                200 -> conn.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                else -> error("HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }
}
