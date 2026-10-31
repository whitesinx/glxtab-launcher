package com.afdalah.headunit

import com.afdalah.headunit.data.HeadUnitConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ConfigParseTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test fun shippedConfigParses() {
        val text = File("src/main/assets/config.json").readText()
        val cfg = json.decodeFromString(HeadUnitConfig.serializer(), text)
        assertEquals(6, cfg.dock.size)
        assertEquals(8f, cfg.autoSwitch.movingSpeedKmh)
        assertEquals("#FF9F0A", cfg.profiles.night.accentColor)
        assertEquals(0.02f, cfg.profiles.standby.brightness)
        assertFalse("placeholders must not count as configured", cfg.navigation.home.isConfigured)
    }

    @Test fun partialConfigFallsBackToDefaults() {
        val cfg = json.decodeFromString(HeadUnitConfig.serializer(), """{ "carBluetoothName": "MyCar", "unknown": 1 }""")
        assertEquals("MyCar", cfg.carBluetoothName)
        assertEquals(6, cfg.dock.size)
        assertEquals(120, cfg.autoSwitch.parkAfterStoppedSec)
    }
}
