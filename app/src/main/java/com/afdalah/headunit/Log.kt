package com.afdalah.headunit

/** Single log tag so `./gradlew logcat` (HeadUnit:V) shows everything the app does. */
object Log {
    const val TAG = "HeadUnit"
    fun d(msg: String) = android.util.Log.d(TAG, msg)
    fun i(msg: String) = android.util.Log.i(TAG, msg)
    fun w(msg: String, t: Throwable? = null) = android.util.Log.w(TAG, msg, t)
}
