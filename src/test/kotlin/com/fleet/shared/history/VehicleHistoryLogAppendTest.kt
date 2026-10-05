package com.fleet.shared.history

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class VehicleHistoryLogAppendTest {

    @Test
    fun append_threeSamplesSameDay_writesThreeFeatureLines() {
        withLog { root, log ->
            log.append(historySample())
            log.append(historySample(longitude = 13.406, latitude = 52.53, altitudeM = 36.0))
            log.append(historySample(longitude = 13.407, latitude = 52.54, altitudeM = 37.0))
            val file = root.resolve("WVWZZZ1JZXW000001/2026-10-05.geojsonl")
            val lines = Files.readAllLines(file)
            assertEquals(3, lines.size)
            assertEquals(FIRST_LINE, lines[0])
            lines.forEach { line ->
                val feature = Json.parseToJsonElement(line).jsonObject
                assertEquals("Feature", feature.getValue("type").jsonPrimitive.content)
                assertEquals(3, feature.getValue("geometry").jsonObject.getValue("coordinates").jsonArray.size)
                assertEquals(PROPERTY_NAMES, feature.getValue("properties").jsonObject.keys.toList())
            }
        }
    }

    @Test
    fun append_withoutAltitude_writesLonLatOnly() {
        withLog { root, log ->
            log.append(historySample(altitudeM = null))
            val line = Files.readAllLines(root.resolve("WVWZZZ1JZXW000001/2026-10-05.geojsonl")).single()
            val coordinates = Json.parseToJsonElement(line).jsonObject
                .getValue("geometry").jsonObject.getValue("coordinates").jsonArray
            assertEquals(2, coordinates.size)
            assertEquals("13.405", coordinates[0].jsonPrimitive.content)
            assertEquals("52.52", coordinates[1].jsonPrimitive.content)
        }
    }

    @Test
    fun append_whenUtcDayRolls_closesPreviousDay() {
        withLog { root, log ->
            log.append(historySample(time = Instant.parse("2026-10-05T23:59:55Z")))
            log.append(historySample(time = Instant.parse("2026-10-06T00:00:05Z"), altitudeM = null))
            val dir = root.resolve("WVWZZZ1JZXW000001")
            assertTrue(Files.isRegularFile(dir.resolve("2026-10-05.geojson")))
            assertFalse(Files.exists(dir.resolve("2026-10-05.geojsonl")))
            assertTrue(Files.isRegularFile(dir.resolve("2026-10-06.geojsonl")))
            val closed = Files.readString(dir.resolve("2026-10-05.geojson"))
            assertEquals(
                "{\"type\":\"FeatureCollection\",\"features\":[$ROLLOVER_LINE]}\n",
                closed,
            )
        }
    }

    @Test
    fun append_twoVins_writesSeparateFolders() {
        withLog { root, log ->
            log.append(historySample(vin = "VIN-A"))
            log.append(historySample(vin = "VIN-B"))
            assertTrue(Files.isRegularFile(root.resolve("VIN-A/2026-10-05.geojsonl")))
            assertTrue(Files.isRegularFile(root.resolve("VIN-B/2026-10-05.geojsonl")))
        }
    }

    @Test
    fun append_whenLatitudeInvalid_writesNothing() {
        withLog { root, log ->
            assertFailsWith<IllegalArgumentException> {
                log.append(historySample(latitude = 91.0))
            }
            assertEquals(emptyList(), Files.list(root).use { it.toList() })
        }
    }

    @Test
    fun append_whenVinBlank_writesNothing() {
        withLog { root, log ->
            assertFailsWith<IllegalArgumentException> {
                log.append(historySample(vin = ""))
            }
            assertEquals(emptyList(), Files.list(root).use { it.toList() })
        }
    }

    private companion object {
        val PROPERTY_NAMES = listOf(
            "time",
            "vin",
            "battery_id",
            "speed_kmh",
            "gps_distance_km",
            "odometer_km",
            "soc_percent",
            "pack_voltage_v",
            "pack_current_a",
            "consumption_kwh_per_100km",
            "outside_temp_c",
            "humidity_percent",
            "pm25",
            "pm10",
            "fix_quality",
            "satellites",
        )
        const val FIRST_LINE =
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[13.405,52.52,35.0]},"properties":{"time":"2026-10-05T09:14:20Z","vin":"WVWZZZ1JZXW000001","battery_id":"BAT-1","speed_kmh":null,"gps_distance_km":null,"odometer_km":null,"soc_percent":null,"pack_voltage_v":null,"pack_current_a":null,"consumption_kwh_per_100km":null,"outside_temp_c":null,"humidity_percent":null,"pm25":null,"pm10":null,"fix_quality":null,"satellites":null}}"""
        const val ROLLOVER_LINE =
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[13.405,52.52,35.0]},"properties":{"time":"2026-10-05T23:59:55Z","vin":"WVWZZZ1JZXW000001","battery_id":"BAT-1","speed_kmh":null,"gps_distance_km":null,"odometer_km":null,"soc_percent":null,"pack_voltage_v":null,"pack_current_a":null,"consumption_kwh_per_100km":null,"outside_temp_c":null,"humidity_percent":null,"pm25":null,"pm10":null,"fix_quality":null,"satellites":null}}"""
    }
}
