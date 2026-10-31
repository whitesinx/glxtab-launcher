# One UI quirks and in-car reliability

## How Tablet mode works, and its limits
- The tablet's default HOME app is **Head Unit**, not One UI Home (Routines can't change the default launcher).
  In Tablet mode, `MainActivity` starts translucent, launches One UI Home's home activity explicitly
  (`FLAG_ACTIVITY_NO_ANIMATION | EXCLUDE_FROM_RECENTS`) and finishes before drawing anything.
- Because Android only grants the system "home" role to the *default* launcher, One UI Home runs as a regular
  activity in Tablet mode. In practice:
  - Home screen, widgets, folders, the app drawer, wallpaper and swipe gestures all work as usual.
  - Recents is still drawn by One UI Home. One UI Home itself is kept out of the Recents list.
  - The very first HOME press after a reboot (cold process start) can take a few hundred ms longer.
    With battery optimization off the process normally stays warm.
  - One UI Home's "press Home again to go to the first page" may not fire, because every HOME press goes
    through Head Unit first.
- Gesture navigation (swipe up for Home, swipe and hold for Recents) works with a third-party default launcher
  on One UI 5.1. If a gesture misbehaves, switch to button navigation in Settings › Display › Navigation bar.

## Keeping the app alive in the car
One UI is aggressive about background apps. Set all of these:
1. Settings › Apps › Head Unit › Battery › **Unrestricted**.
2. Settings › Battery and device care › Battery › Background usage limits: add Head Unit to **Never sleeping apps**,
   and make sure it isn't under Sleeping / Deep sleeping apps.
3. Notification access for Head Unit stays enabled. Revoking it kills media control and banners.
4. While a head-unit mode is active, a foreground service (`HeadUnitService`, minimized notification) keeps GPS
   speed and the auto switching running, even while Maps or Spotify are in front. It stops in Tablet mode, so
   there is no battery cost at home. In Standby, GPS is paused.
5. **Auto optimize daily** / auto restart (Battery and device care › ⋮ › Automation) reboots the tablet at night.
   That's harmless: after a reboot the app comes up in Tablet mode unless the boot rule applies.
6. Optional: `adb shell appops set com.afdalah.headunit SYSTEM_ALERT_WINDOW allow`. Android 10+ blocks background
   activity starts. As the default HOME app, Head Unit is usually exempt, but this permission makes the boot
   restore and the Bluetooth trigger reliable.

## Heat and battery (the biggest risk in a dashboard)
- The Tab S7 starts limiting charging around 40–45 °C and dims the screen or throttles the SoC beyond that.
  A dashboard in direct sun easily exceeds 60 °C. The app warns at **≥ 45 °C** (sidebar badge + contextual card).
- Turn on **Protect battery** (Settings › Battery and device care › Battery › More battery settings › Protect battery).
  It caps charge at 85%, which greatly reduces swelling when the tablet sits hot and fully charged.
  Note that when it stops charging at 85%, One UI may report "not charging" although the cable is still in.
  The app checks *plugged in*, not *charging*, but a Samsung routine using *Charging status* may fire. See README §6.
- Don't leave the tablet in a parked car in summer. Mount it out of direct sun and, ideally, near a vent.
- Use a good 25 W USB-C PD supply. Many car USB-A ports supply 5 V / 0.5–1 A, which is less than the tablet
  uses with the screen at full brightness and LTE + GPS on, so the battery slowly drains while "charging".

## Display and touch
- **Edge panels** take touches from the right edge. Turn them off in the Drive routine (with "reverse when the
  routine ends"), or permanently under Settings › Display › Edge panels.
- Head-unit modes are immersive: the status and navigation bars are hidden and appear on an edge swipe. Home and
  Voice in the sidebar are excluded from the left-edge back gesture (Android allows at most 200dp per edge, so the
  whole sidebar can't be excluded).
- The Tab S7 is a 120 Hz LCD. The UI is static except for feedback animations, so it idles at low refresh.
- **Sun glare**: Parked › Settings › High contrast (opaque panels, bolder text). Light mode is the sun/moon
  button at the top of the sidebar (dark by default).
- Night Drive sets window brightness to 35% (`profiles.night.brightness`), which affects only this app's window.
  Brightness returns to normal when you open Maps or leave the head unit.

## Google Maps picture-in-picture
While navigating, Google Maps shrinks into a floating PiP window when you leave it, which covers the dashboard.
Turn it off (the dashboard mirrors the next turn and ETA instead): Settings › Apps › Google Maps ›
Picture-in-picture › off, or `adb shell appops set com.google.android.apps.maps PICTURE_IN_PICTURE ignore`
(`allow` to undo). This also applies in Tablet mode.

## Calls
- The SM-T875 supports voice calls only in some regions or with some carriers. If the Phone app can't place calls on
  your SIM, the call screen will never appear.
- Ringing state comes from telephony (READ_PHONE_STATE). The caller name comes from the dialer's call
  notification (needs notification access). Accept/Decline use TelecomManager (ANSWER_PHONE_CALLS), falling back
  to the notification's own buttons.
- Samsung's own incoming-call pop-up may appear at the same time. Either one works.

## Wireless debugging
- On Android 13 the port changes after every reboot and every time Wireless debugging is toggled. It also turns off
  when the tablet changes Wi-Fi network. `scripts/connect.sh` handles all of these cases.
- Wireless debugging needs Wi-Fi. In the car, use USB ADB or a phone hotspot.

## What can't be done without root
- Change the default launcher from a routine (worked around with internal modes).
- Disable Edge panels or system gestures programmatically (handled with routines and gesture exclusion).
- Block video apps that are already in front when the car starts moving. The app blocks *launching* them from
  the head unit while moving, but Android doesn't let a normal app close another app.
- Change the global screen brightness without WRITE_SETTINGS (the app uses window brightness, and routines set
  the system brightness).
