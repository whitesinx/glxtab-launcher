import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appId = "com.afdalah.headunit"

android {
    namespace = appId
    compileSdk = 34

    defaultConfig {
        applicationId = appId
        minSdk = 30
        targetSdk = 33
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sideload-only app: sign release with the debug key so it installs over ADB.
            // Replace with your own keystore if you ever distribute it.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.osmdroid)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}

// ---------------------------------------------------------------------------
// Wireless-ADB developer tasks. Values come from <root>/debug.properties
// (gitignored). Ports + pairing code change every session: run
// scripts/connect.sh first, it updates the file for you.
// ---------------------------------------------------------------------------

fun debugProps(): Properties {
    val file = rootProject.file("debug.properties")
    if (!file.exists()) throw GradleException("debug.properties missing. Copy debug.properties.example and run scripts/connect.sh")
    return Properties().apply { file.inputStream().use { load(it) } }
}

fun adbSerial(): String {
    val p = debugProps()
    val ip = p.getProperty("DEVICE_IP")
    val port = p.getProperty("ADB_PORT")
    if (ip.isNullOrBlank() || port.isNullOrBlank() || port.startsWith("[")) {
        throw GradleException("ADB_PORT not set in debug.properties. Run scripts/connect.sh (it asks for the current port).")
    }
    return "$ip:$port"
}

fun adbPath(): String {
    val local = rootProject.file("local.properties")
    if (local.exists()) {
        val sdk = Properties().apply { local.inputStream().use { load(it) } }.getProperty("sdk.dir")
        val adb = File(sdk ?: "", "platform-tools/adb")
        if (adb.exists()) return adb.absolutePath
    }
    return "adb"
}

fun Exec.adb(vararg args: String) {
    doFirst { commandLine(listOf(adbPath(), "-s", adbSerial()) + args) }
}

val adbGroup = "headunit adb"

tasks.register<Exec>("installDebugApk") {
    group = adbGroup
    description = "Install the debug APK on the tablet over wireless ADB"
    dependsOn("assembleDebug")
    adb("install", "-r", "-t", layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile.absolutePath)
}

tasks.register<Exec>("installAndLaunch") {
    group = adbGroup
    description = "Install the debug APK and start the app (it routes according to the current mode)"
    dependsOn("installDebugApk")
    adb("shell", "am", "start", "-n", "$appId/.MainActivity")
}

tasks.register<Exec>("setDefaultHome") {
    group = adbGroup
    description = "Make Head Unit the default HOME app"
    adb("shell", "cmd", "package", "set-home-activity", "$appId/.MainActivity")
}

tasks.register<Exec>("resetHome") {
    group = adbGroup
    description = "Restore Samsung One UI Home as the default HOME app"
    adb(
        "shell",
        "c=\$(cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME " +
            "| grep -o 'com.sec.android.app.launcher/[^ ]*' | head -1); " +
            "cmd package set-home-activity \${c:-com.sec.android.app.launcher/com.sec.android.app.launcher.activities.LauncherActivity}",
    )
}

tasks.register<Exec>("triggerMode") {
    group = adbGroup
    description = "Fire launcher://mode/<mode>. Usage: ./gradlew triggerMode -Pmode=drive"
    val mode = (project.findProperty("mode") as String?) ?: "drive"
    val valid = setOf("tablet", "drive", "night", "parked", "standby")
    doFirst { if (mode !in valid) throw GradleException("-Pmode must be one of $valid") }
    adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", "launcher://mode/$mode", appId)
}

tasks.register<Exec>("simulateSpeed") {
    group = adbGroup
    description = "Debug builds only: inject a fake GPS speed. Usage: -Pkmh=50 (use -Pkmh=-1 to return to real GPS)"
    val kmh = (project.findProperty("kmh") as String?) ?: "0"
    adb("shell", "am", "broadcast", "-a", "$appId.debug.SIMULATE_SPEED", "-p", appId, "--ef", "kmh", kmh)
}

tasks.register<Exec>("logcat") {
    group = adbGroup
    description = "Stream logcat filtered to the HeadUnit tag"
    adb("logcat", "-v", "color", "HeadUnit:V", "AndroidRuntime:E", "*:S")
}

tasks.register<Exec>("pushConfig") {
    group = adbGroup
    description = "Push a config.json to the tablet. Usage: -Pconfig=path/to/config.json (default: app/src/main/assets/config.json)"
    val src = (project.findProperty("config") as String?) ?: file("src/main/assets/config.json").absolutePath
    adb("push", src, "/sdcard/Android/data/$appId/files/config.json")
    doLast { println("Pushed. Open Parked settings → Reload config, or run ./gradlew triggerMode -Pmode=parked") }
}

tasks.register<Exec>("grantSecureSettings") {
    group = adbGroup
    description = "Optional: let the app toggle stay_on_while_plugged_in only while a head-unit mode is active"
    adb("shell", "pm", "grant", appId, "android.permission.WRITE_SECURE_SETTINGS")
}
