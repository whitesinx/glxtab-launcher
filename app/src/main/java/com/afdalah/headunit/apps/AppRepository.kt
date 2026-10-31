package com.afdalah.headunit.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.afdalah.headunit.Log
import com.afdalah.headunit.data.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppCategory(val label: String) {
    NAVIGATION("Navigation"),
    AUDIO("Music & Audio"),
    COMMUNICATION("Communication"),
    VIDEO("Video"),
    PHOTOS("Photos & Camera"),
    PRODUCTIVITY("Productivity"),
    NEWS("News"),
    GAMES("Games"),
    OTHER("Other"),
}

data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String,
    val category: AppCategory,
) {
    val component get() = ComponentName(packageName, className)
}

class AppRepository(
    private val context: Context,
    private val config: ConfigRepository,
    private val scope: CoroutineScope,
) {
    private val pm = context.packageManager
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val iconCache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            i.data?.schemeSpecificPart?.let { pkg -> iconCache.snapshot().keys.filter { it.startsWith(pkg) }.forEach(iconCache::remove) }
            refresh()
        }
    }

    init {
        context.registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        refresh()
    }

    fun refresh() {
        scope.launch {
            _apps.value = withContext(Dispatchers.IO) {
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(launcher, 0)
                    .filter { it.activityInfo.packageName != context.packageName }
                    .map { ri ->
                        AppEntry(
                            label = ri.loadLabel(pm).toString(),
                            packageName = ri.activityInfo.packageName,
                            className = ri.activityInfo.name,
                            category = categorize(ri.activityInfo.applicationInfo),
                        )
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
        }
    }

    fun isInstalled(pkg: String) = pm.getLaunchIntentForPackage(pkg) != null

    fun isVideo(pkg: String): Boolean =
        pkg in config.config.value.videoPackages ||
            runCatching { pm.getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_VIDEO }.getOrDefault(false)

    private fun categorize(ai: ApplicationInfo): AppCategory {
        val pkg = ai.packageName
        if (pkg in config.config.value.videoPackages) return AppCategory.VIDEO
        return when (ai.category) {
            ApplicationInfo.CATEGORY_MAPS -> AppCategory.NAVIGATION
            ApplicationInfo.CATEGORY_AUDIO -> AppCategory.AUDIO
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.COMMUNICATION
            ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
            ApplicationInfo.CATEGORY_IMAGE -> AppCategory.PHOTOS
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            else -> when {
                listOf("dialer", "phone", "messag", "contacts", "mail", "whatsapp", "telegram", "signal").any { pkg.contains(it) } -> AppCategory.COMMUNICATION
                listOf("maps", "waze", "navi").any { pkg.contains(it) } -> AppCategory.NAVIGATION
                listOf("music", "spotify", "podcast", "radio", "audible").any { pkg.contains(it) } -> AppCategory.AUDIO
                listOf("camera", "gallery", "photos").any { pkg.contains(it) } -> AppCategory.PHOTOS
                else -> AppCategory.OTHER
            }
        }
    }

    /** Launch an app. Returns false if it isn't installed. */
    fun launch(pkg: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }.onFailure { Log.w("Launch $pkg failed", it) }.isSuccess
    }

    /**
     * App icon rendered full-bleed and later clipped to our continuous-corner mask, so every icon
     * shares one uniform rounded-square shape regardless of the OEM icon mask.
     */
    suspend fun icon(pkg: String, sizePx: Int): ImageBitmap? {
        val key = "$pkg@$sizePx"
        iconCache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val d = pm.getApplicationIcon(pkg)
                renderFullBleed(d, sizePx).asImageBitmap().also { iconCache.put(key, it) }
            }.getOrNull()
        }
    }

    private fun renderFullBleed(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (d is AdaptiveIconDrawable) {
            // Adaptive layers are 108dp with the visible 72dp in the middle: expand by 1/4 on each side.
            val inset = size / 4
            listOfNotNull(d.background, d.foreground).forEach { layer ->
                layer.setBounds(-inset, -inset, size + inset, size + inset)
                layer.draw(canvas)
            }
        } else {
            canvas.drawColor(0xFF2C2C2E.toInt())
            val pad = size / 8
            d.setBounds(pad, pad, size - pad, size - pad)
            d.draw(canvas)
        }
        return bmp
    }
}
