package com.afdalah.headunit.media

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.afdalah.headunit.Log
import com.afdalah.headunit.notifications.HeadUnitNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val isPlaying: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val sessionActivity: PendingIntent?,
    val album: String = "",
    val durationMs: Long = 0L,
    /** Playback position reported at [positionAtElapsed] (SystemClock.elapsedRealtime). */
    val positionMs: Long = 0L,
    val positionAtElapsed: Long = 0L,
    val playbackSpeed: Float = 1f,
) {
    /** Extrapolated position, for lyrics sync. */
    fun positionNow(nowElapsed: Long = SystemClock.elapsedRealtime()): Long =
        if (!isPlaying || positionAtElapsed == 0L) positionMs
        else positionMs + ((nowElapsed - positionAtElapsed) * playbackSpeed).toLong()
}

/**
 * Controls whichever media app is playing (Spotify, YouTube Music, …) through MediaSessionManager.
 * Requires notification access for [HeadUnitNotificationListener].
 */
class MediaRepository(private val context: Context, private val scope: CoroutineScope) {

    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, HeadUnitNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private var controller: MediaController? = null
    private var started = false
    private var artKey: String? = null
    private var art: ImageBitmap? = null
    /** Large icon of each app's media notification: Spotify puts album art only there. */
    private val notificationArt = HashMap<String, Bitmap>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() {
            refresh()
        }
    }

    /** Safe to call repeatedly (e.g. when notification access gets granted). */
    fun start() {
        try {
            if (!started) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
                started = true
            }
            refresh()
        } catch (e: SecurityException) {
            Log.w("Media sessions need notification access")
        }
    }

    fun stop() {
        if (started) runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
        started = false
        attach(null)
    }

    fun refresh() {
        runCatching { pick(msm.getActiveSessions(listenerComponent)) }
    }

    private fun pick(sessions: List<MediaController>) {
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.packageName == controller?.packageName }
            ?: sessions.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) attach(best) else publish()
    }

    private fun attach(c: MediaController?) {
        controller?.unregisterCallback(callback)
        controller = c
        c?.registerCallback(callback, handler)
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _nowPlaying.value = null
            return
        }
        val md = c.metadata
        val state = c.playbackState
        val actions = state?.actions ?: 0L
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        _nowPlaying.value = NowPlaying(
            packageName = c.packageName,
            appLabel = appLabel(c.packageName),
            title = title,
            artist = artist,
            art = artwork(md, "${c.packageName}|$title|$artist"),
            isPlaying = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L || state == null,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L || state == null,
            sessionActivity = c.sessionActivity,
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionMs = state?.position?.coerceAtLeast(0L) ?: 0L,
            positionAtElapsed = state?.lastPositionUpdateTime ?: 0L,
            playbackSpeed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
        )
    }

    /**
     * Decoded once per track and downscaled (album art can be multi-megapixel).
     * Order: metadata bitmap → metadata art URI (loaded off the main thread) → media notification icon.
     */
    private fun artwork(md: MediaMetadata?, key: String): ImageBitmap? {
        if (key == artKey && art != null) return art
        artKey = key
        val pkg = key.substringBefore('|')
        val bmp = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bmp != null) {
            art = scaleDown(bmp, 640).asImageBitmap()
            return art
        }
        val uri = listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            .firstNotNullOfOrNull { k -> md?.getString(k)?.takeIf { it.isNotBlank() } }
        if (uri != null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) { decodeUri(uri) }
                if (loaded != null && artKey == key) {
                    art = loaded.asImageBitmap()
                    publish()
                }
            }
        }
        art = notificationArt[pkg]?.asImageBitmap()
        return art
    }

    private fun decodeUri(uri: String): Bitmap? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }?.let { scaleDown(it, 640) }
    }.onFailure { Log.d("Album art URI not readable: ${it.message}") }.getOrNull()

    /** Called by the notification listener for media (transport) notifications. */
    fun onMediaNotificationArt(pkg: String, bitmap: Bitmap?) {
        if (bitmap == null) return
        notificationArt[pkg] = scaleDown(bitmap, 640)
        if (controller?.packageName == pkg && art == null) {
            art = notificationArt[pkg]?.asImageBitmap()
            publish()
        }
    }

    private fun scaleDown(b: Bitmap, max: Int): Bitmap {
        val big = maxOf(b.width, b.height)
        if (big <= max) return b
        val f = max.toFloat() / big
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
    }

    private fun appLabel(pkg: String) = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    fun openApp() {
        val np = _nowPlaying.value ?: return
        runCatching { np.sessionActivity?.send() ?: launch(np.packageName) }.onFailure { launch(np.packageName) }
    }

    private fun launch(pkg: String) {
        context.packageManager.getLaunchIntentForPackage(pkg)?.let {
            context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
