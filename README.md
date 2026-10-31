# Head Unit: a CarPlay-style launcher for the Galaxy Tab S7 LTE

A HOME launcher that turns an SM-T875 (Android 13 / One UI 5.1) into a dashboard head unit.
**Out of the car it gets out of the way.** In Tablet mode (the default), every HOME press is
forwarded straight to Samsung One UI Home, so the tablet behaves like a normal Galaxy Tab.
Head-unit modes are switched on only by Samsung Modes and Routines, deep links, or a manual toggle.

| Mode | What you see | Activated by |
|---|---|---|
| **Tablet** (default) | One UI Home, untouched | install, every reboot (see boot rule), routine, "Exit to Tablet" |
| **Drive** | Dashboard: Now Playing + lyrics (left), live map with Google Maps turn-by-turn (right), trip bar with Ask Gemini; distraction rules | routine, deep link, "Head Unit" icon, auto (moving) |
| **Night Drive** | Drive with a darker palette, warm accent, 35% brightness | auto at sunset, routine/deep link |
| **Parked** | Dashboard + App Library, Settings, keyboard, video apps | auto (stopped for 2 min), manual |
| **Standby** | Dim clock, then the screen sleeps | routine (charging stops), auto (power lost) |

---

## 1. Project layout

```
app/src/main/java/com/afdalah/headunit/
  MainActivity.kt            HOME activity. Tablet: invisible pass-through. Head unit: Compose UI
  HeadUnitApp.kt             wires every singleton; starts/stops the service with the mode
  mode/                      Mode, ModeManager (rules), HomeForwarder (→ One UI Home),
                             ModeSwitchActivity (5 Routines targets + deep links),
                             AutoModeController (speed/sun/power), BootRestore (+ receivers)
  sensors/                   DrivingStateTracker (GPS speed, hysteresis), SunCalculator, StatusMonitor
  media/ notifications/      MediaSessionManager + NotificationListenerService, banners
  calls/                     incoming-call state, accept/decline
  apps/                      app catalog, categories, uniform icon rendering
  service/HeadUnitService    foreground service, only while a head-unit mode is active
  system/                    stay-on toggle, intents (assistant, Maps/Waze, settings)
  ui/theme/                  Color / Type / Shape / Motion / Theme: HIG token mapping
  ui/…                       sidebar, dashboard, library, settings, overlays
app/src/debug/               debug overlay + speed simulator (absent from release)
app/src/release/             no-op twins of the debug hooks
app/src/main/assets/config.json   example config (all five profiles)
scripts/connect.sh|.bat      wireless ADB pairing/connection
scripts/lucide2vector.py     add more Lucide icons as vector drawables
docs/DESIGN.md               CarPlay/HIG → token mapping
docs/ONE_UI_NOTES.md         One UI quirks, heat, battery, keeping the app alive
```

## 2. Build

Requirements: JDK 17 and the Android SDK (platform 34, build-tools 34). `local.properties` must point at the SDK:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # sun-time math + config parsing
./gradlew assembleRelease        # minified, debug code stripped, signed with the debug key
```

## 3. Connect over wireless ADB

1. Tablet: **Settings › About tablet › Software information**, then tap **Build number** 7×.
2. **Settings › Developer options › Wireless debugging**: turn it on (same Wi-Fi as your computer).
3. Run:

```bash
scripts/connect.sh          # macOS/Linux
scripts\connect.bat         # Windows
```

The script tries the saved port, then mDNS discovery. If both fail, it asks for the **pairing port + code**
(from *Pair device with pairing code*) and the **ADB port** (from *IP address & Port*) and saves them to
`debug.properties` (gitignored). Android 13 changes these after every reboot or Wireless-debugging toggle,
so expect to re-run it. `--pair` forces fresh pairing.

## 4. Install and make it the default launcher

```bash
./gradlew installAndLaunch      # install debug APK, start MainActivity (stays in Tablet mode)
./gradlew setDefaultHome        # cmd package set-home-activity com.afdalah.headunit/.MainActivity
```

Or on the tablet: **Settings › Apps › Choose default apps › Home app › Head Unit**.
Press HOME and you should still land in One UI Home. That is Tablet mode working.

Then grant permissions. Enter Drive once (tap the **Head Unit** icon) and accept the prompts
(location, phone, Bluetooth, notifications), or use **Parked › Settings › Permissions & reliability**:

| Permission | Why | How |
|---|---|---|
| Notification access | Now Playing controls, banners, caller name | Settings › Notifications › Advanced settings › Notification access (wording varies; search "Notification access") › **Head Unit** |
| Location (precise) | speed → distraction rules, sunrise/sunset | runtime prompt |
| Phone + Answer calls | incoming-call screen, accept/decline | runtime prompt |
| Nearby devices (Bluetooth) | car connection status, boot rule | runtime prompt |
| Battery: **Unrestricted** | keep the service alive while driving | Settings › Apps › Head Unit › Battery › Unrestricted |
| *Optional:* Appear on top | lets boot-restore open the dashboard from the background | `adb shell appops set com.afdalah.headunit SYSTEM_ALERT_WINDOW allow` |
| *Optional:* secure settings | stay-awake toggle limited to head-unit modes (below) | `./gradlew grantSecureSettings` |

## 5. Configure

Edit `app/src/main/assets/config.json` (or the live copy on the tablet) and replace the placeholders
`[HOME_ADDRESS]`, `[WORK_ADDRESS]`, `[CAR_BLUETOOTH_NAME]`. The car name must match the Bluetooth name
exactly as it appears in Settings › Connections › Bluetooth.

```bash
./gradlew pushConfig                         # pushes assets/config.json
./gradlew pushConfig -Pconfig=~/my-car.json  # or any file
```

The live file is `/sdcard/Android/data/com.afdalah.headunit/files/config.json`. **Parked › Settings › Reload**
re-reads it, and Import/Export use the system file picker. Home/Work addresses and the car name can also be
typed in Parked settings. Unknown keys are ignored and missing keys use defaults, so partial files are fine.

## 6. Samsung Modes and Routines

Samsung Routines cannot change the default launcher, so Head Unit **stays** the default HOME app and switches
modes internally. Five invisible activities do the switching. They appear in the app picker as
**HU · Tablet**, **HU · Drive**, **HU · Night Drive**, **HU · Parked**, **HU · Standby**. Each applies its
mode and returns to the right home screen.

Open **Settings › Modes and Routines › Routines › +**. Menu wording differs slightly between One UI builds;
use the search field in the condition/action pickers if a name doesn't match.

### Routine A: "Car connected" → Drive
1. **If**: *Bluetooth device* › choose **[CAR_BLUETOOTH_NAME]** › *Connected*.
2. **Then**, add in this order:
   - *Display › Brightness* › maximum
   - *Connections › Location* › On
   - *Display › Edge panels* › Off (stops the right-edge handle from stealing taps)
   - *Apps › Open an app* › **HU · Drive**
3. Leave *"Reverse actions when routine ends"* on, so brightness, Location and Edge panels are restored when you disconnect.
4. Name it "Car connected" › Save.

### Routine B: "Car power off" → Standby
1. **If**: *Charging status* › *Not charging*, **and** *Bluetooth device* › **[CAR_BLUETOOTH_NAME]** › *Connected*.
2. **Then**: *Apps › Open an app* › **HU · Standby**.
3. Save.

> The app also enters Standby by itself when external power is lost (`autoSwitch.standbyOnPowerLoss`), and
> goes back to your last mode when power returns. If you enable **Protect battery** (85% limit), One UI may
> report "Not charging" while still plugged in, which would fire Routine B too early. If that happens, delete
> Routine B and rely on the built-in detection, which checks *plugged in*, not *charging*.

### Routine C: "Car disconnected" → Tablet
1. **If**: *Bluetooth device* › **[CAR_BLUETOOTH_NAME]** › *Disconnected*.
2. **Then**: *Apps › Open an app* › **HU · Tablet**.
3. Save.

### Optional: Samsung "Driving" mode
**Modes › Driving** can start automatically when the car's Bluetooth connects. In the mode's settings,
add **HU · Drive** under the apps to open when the mode starts, and **HU · Tablet** for when it ends (if your
build offers an end action; otherwise keep Routine C). Use either Driving mode or Routine A, not both.

### After creating the routines
You can hide the five **HU ·** icons: long-press an empty spot on the home screen › **Settings** ›
**Hide apps on Home screen** (called *Hide apps on Home and Apps screens* when you use an Apps screen).
Hidden apps are still installed and launchable. If Routines' app picker doesn't list them, unhide them
while you create the routine, then hide them again. Keep the **Head Unit** icon visible: it enters Drive manually, and
long-press gives Drive / Night Drive / Parked / Exit to Tablet.

### Deep links (Tasker, adb)
```
launcher://mode/tablet   launcher://mode/drive   launcher://mode/night
launcher://mode/parked   launcher://mode/standby
```
```bash
./gradlew triggerMode -Pmode=drive     # = adb shell am start -a android.intent.action.VIEW -d launcher://mode/drive com.afdalah.headunit
```

## 7. Behaviour rules

**Tablet mode**
- The default on first install and after every reboot.
- HOME → One UI Home through the translucent, animation-free `MainActivity`. Nothing is drawn.
  If One UI Home is missing or disabled, you get a chooser of the other launchers (or Home settings).
- Auto switching (speed, sunset, power) runs only inside head-unit modes and can never leave or enter Tablet mode.
- **Boot rule:** after a reboot the app starts in Tablet mode. The last head-unit mode is restored only if the
  tablet is **plugged in** *and* connected to `carBluetoothName`. Bluetooth often connects a few seconds after
  boot, so the decision stays open for 5 minutes and is re-checked when the car connects.
- **Disable head unit** (Parked › Settings): forces Tablet mode. Routines and deep links are ignored. Tapping the
  Head Unit icon shows a prompt to turn it back on.

**Head-unit modes**
- Landscape only, immersive (swipe from an edge to see the system bars), screen kept on.
- Moving = GPS speed **> 8 km/h**. Stopped = ≤ 8 km/h for 5 s. Parked after 120 s stopped.
- While moving: App Library, Settings, the mode menu, text input and video apps show **"Available when parked"**.
  Banners show only icon, sender and short text; tapping dismisses them (it opens the app only when parked).
  The voice button (bottom of the sidebar) and **Ask Gemini** (trip bar) are always available; both open the Gemini app
  (Android has no public way to start Gemini listening, so tap its mic or Live button). Gemini can change a Google Maps route.
- Map: OpenStreetMap tiles (osmdroid), dark-styled in dark mode, always centered on the car. Tap it to open Google Maps.
  While Google Maps navigates, the real Google Maps is shown as a window placed exactly over the map card
  (One UI freeform window, opened with launch bounds; re-placed when you return to the dashboard). Starting
  navigation in full-screen Maps returns to the dashboard automatically (needs "display over other apps":
  `adb shell appops set com.afdalah.headunit SYSTEM_ALERT_WINDOW allow`). Its ETA also
  shows in the trip bar. Turn this off with `navigation.mapsInCard: false` in config.json.
- Lyrics: time-synced from LRCLIB (lrclib.net; only title/artist/album/duration are sent). Tap the lyrics card for
  karaoke mode (full-screen lyrics, sidebar stays); X, BACK or HOME returns to the dashboard. While Google Maps is
  in the map card, karaoke is drawn in a "display over other apps" window so it covers Maps as well; only the next
  turn stays visible, top right.
- Dark/light: the sun/moon button at the top of the sidebar (dark by default; Night Drive and Standby are always dark).
- Car Bluetooth (`carBluetoothName`): the app reacts to the car itself, routines are only a backup.
  Connected → screen wakes, Honda welcome, Drive (Night Drive after sunset). Disconnected → Google Maps navigation
  ends (its "Exit navigation" button) and its window closes, Honda goodbye screen, screen locks, Tablet mode
  underneath (next unlock shows a clean One UI Home). One-time setup over adb:
  `adb shell appops set com.afdalah.headunit SYSTEM_ALERT_WINDOW allow` and enable the lock service:
  `adb shell settings put secure enabled_accessibility_services com.afdalah.headunit/com.afdalah.headunit.system.LockScreenService`
  then `adb shell settings put secure accessibility_enabled 1` (or Settings › Accessibility › Installed apps ›
  Head Unit screen lock). A Samsung routine that opens "HU · Tablet" on disconnect is ignored while the goodbye
  screen runs; it's best disabled. Debug builds can simulate the car:
  `adb shell am broadcast -a com.afdalah.headunit.debug.SIMULATE_CAR -p com.afdalah.headunit --ez connected false`.
- Quick Settings tile "Head Unit": on = head unit (Drive, or Night Drive after sunset), off = Tablet. Works from
  the notification shade in both modes. Add it with the pencil in Quick Settings, or
  `adb shell cmd statusbar add-tile com.afdalah.headunit/.system.ModeTileService`.
- Welcome splash: when the head unit starts from Tablet mode (car connected, routine, deep link) it shows the Honda H mark and wordmark (from Wikimedia Commons; Honda trademarks, personal use) and "Welcome, Habibi" for ~2.6 s; tap to skip. Skipped while moving. Change or turn it off under `welcome` in config.json (`enabled`, `logo`: "honda"/"none", `title`, `durationMs`).
- Drive ↔ Night Drive at sunset/sunrise (from GPS position; 19:00–07:00 if no fix yet). A mode you pick by hand
  sticks until the next sunset or sunrise.
- Mode pill (sidebar): **long-press** opens the mode menu (parked only), including *Exit to Tablet mode*.
- Battery ≥ 45 °C: an orange badge in the sidebar and a warning in the contextual card.

**Keeping the screen on.** Head-unit modes keep the screen on with a window flag, which affects only this app.
`adb shell settings put global stay_on_while_plugged_in 3` (AC + USB) also works, but it is global: in Tablet
mode the tablet would never sleep on the charger at home either. Better: run `./gradlew grantSecureSettings` and
turn on **Parked › Settings › Stay awake while plugged in**. The app then sets the value to 3 only while a
head-unit mode is active and restores your previous value in Tablet mode. To undo the manual command:
`adb shell settings put global stay_on_while_plugged_in 0`.

## 8. Debugging at a desk

```bash
./gradlew triggerMode -Pmode=drive    # enter Drive without a routine
./gradlew simulateSpeed -Pkmh=50      # debug builds: pretend to drive (locks the UI)
./gradlew simulateSpeed -Pkmh=0       # stop (unlocks after 5 s; Parked after 120 s)
./gradlew simulateSpeed -Pkmh=-1      # back to real GPS
./gradlew logcat                      # tag HeadUnit
./gradlew triggerMode -Pmode=tablet
```

**Debug overlay** (debug builds only): tap the mode pill **5 times**. It shows the mode, last activation source
(routine / deep link / auto / boot / manual / default), speed (marked SIMULATED), moving/night flags, battery
temperature, network, Bluetooth, rendered FPS and memory. Release builds contain none of this: the debug source
set (overlay, simulator receiver) isn't compiled in, and `BuildConfig.DEBUG` guards let R8 remove the speed override.

## 9. Uninstall / restore One UI Home

```bash
./gradlew resetHome                                         # One UI Home becomes default again
adb shell settings put global stay_on_while_plugged_in 0    # only if you set it manually
adb uninstall com.afdalah.headunit
```
On the tablet: **Settings › Apps › Choose default apps › Home app › One UI Home**, then uninstall Head Unit.
Delete Routines A–C (and the Driving-mode app action) afterwards. If you somehow end up with no working
launcher, uninstalling the app over ADB always brings One UI Home back.
