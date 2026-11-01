package com.afdalah.headunit

import android.app.Application
import android.content.Context
import com.afdalah.headunit.apps.AppRepository
import com.afdalah.headunit.calls.CallMonitor
import com.afdalah.headunit.data.ConfigRepository
import com.afdalah.headunit.data.SettingsStore
import com.afdalah.headunit.debug.DebugHooks
import com.afdalah.headunit.lyrics.LyricsRepository
import com.afdalah.headunit.media.MediaRepository
import com.afdalah.headunit.mode.AutoModeController
import com.afdalah.headunit.mode.BootRestore
import com.afdalah.headunit.mode.CarSession
import com.afdalah.headunit.mode.ModeManager
import com.afdalah.headunit.notifications.NotificationHub
import com.afdalah.headunit.sensors.DrivingStateTracker
import com.afdalah.headunit.sensors.StatusMonitor
import com.afdalah.headunit.service.HeadUnitService
import com.afdalah.headunit.system.AppCloser
import com.afdalah.headunit.system.StayOnController
import com.afdalah.headunit.weather.WeatherRepository
import com.afdalah.headunit.system.SystemActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Manual service locator: everything is a process-wide singleton owned by the Application. */
class HeadUnitApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settingsStore: SettingsStore private set
    lateinit var configRepository: ConfigRepository private set
    lateinit var modeManager: ModeManager private set
    lateinit var stayOn: StayOnController private set
    lateinit var driving: DrivingStateTracker private set
    lateinit var status: StatusMonitor private set
    lateinit var calls: CallMonitor private set
    lateinit var media: MediaRepository private set
    lateinit var lyrics: LyricsRepository private set
    lateinit var notificationHub: NotificationHub private set
    lateinit var apps: AppRepository private set
    lateinit var autoMode: AutoModeController private set
    lateinit var carSession: CarSession private set
    lateinit var weather: WeatherRepository private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        configRepository = ConfigRepository(this).also { it.load() }
        modeManager = ModeManager(this, settingsStore, scope).also { it.init() }
        stayOn = StayOnController(this, modeManager)
        driving = DrivingStateTracker(this, configRepository, scope)
        status = StatusMonitor(this)
        calls = CallMonitor(this)
        media = MediaRepository(this, scope)
        lyrics = LyricsRepository(media, scope)
        notificationHub = NotificationHub(this, configRepository, modeManager, calls, media, scope)
        apps = AppRepository(this, configRepository, scope)
        autoMode = AutoModeController(modeManager, driving, status, configRepository)
        carSession = CarSession(this)
        weather = WeatherRepository(driving.location, scope)

        scope.launch {
            var wasActive: Boolean? = null
            modeManager.state.map { it.mode.isHeadUnit }.distinctUntilChanged().collectLatest { active ->
                stayOn.apply(active)
                if (active) HeadUnitService.start(this@HeadUnitApp) else HeadUnitService.stop(this@HeadUnitApp)
                if (active) weather.start() else weather.stop()
                val leaving = wasActive == true && !active
                wasActive = active
                // Only on an actual head unit -> Tablet switch, never at process start. Cancelled if the
                // head unit comes straight back (collectLatest).
                if (leaving) AppCloser.closeAll(this@HeadUnitApp)
            }
        }
        // Navigation just started in Google Maps (full screen): come back to the dashboard, where the
        // map card then shows Maps. Needs the "display over other apps" permission to start from the background.
        scope.launch {
            var wasNavigating = false
            notificationHub.guidance.map { it != null }.distinctUntilChanged().collect { navigating ->
                if (navigating && !wasNavigating && modeManager.state.value.mode.isHeadUnit &&
                    configRepository.config.value.navigation.mapsInCard
                ) {
                    delay(1_200)
                    SystemActions.showDashboard(this@HeadUnitApp)
                }
                wasNavigating = navigating
            }
        }
        // osmdroid: identify ourselves to the OSM tile servers and keep the tile cache in app storage.
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = filesDir.resolve("osmdroid")
            osmdroidTileCache = cacheDir.resolve("osm-tiles")
        }
        DebugHooks.install(this)
        BootRestore.tryRestore(this, "process-start")
    }
}

val Context.app: HeadUnitApp get() = applicationContext as HeadUnitApp
