package com.fleet.shared.history

import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VehicleHistoryLogLastDistanceTest {

    @Test
    fun lastGpsDistanceKm_threeLines_returnsLast() {
        withLog { _, log ->
            log.append(sample(1.0, "2026-10-05T12:00:00Z"))
            log.append(sample(2.5, "2026-10-05T12:00:10Z"))
            log.append(sample(4.2, "2026-10-05T12:00:20Z"))
            assertEquals(4.2, log.lastGpsDistanceKm(VIN, DAY)!!)
        }
    }

    @Test
    fun lastGpsDistanceKm_truncatedLastLine_returnsLineBefore() {
        withLog { root, log ->
            log.append(sample(1.0, "2026-10-05T12:00:00Z"))
            log.append(sample(2.5, "2026-10-05T12:00:10Z"))
            val open = root.resolve("$VIN/$DAY.geojsonl")
            Files.write(open, "{\"type\":\"Feature\"".toByteArray(), StandardOpenOption.APPEND)
            assertEquals(2.5, log.lastGpsDistanceKm(VIN, DAY)!!)
        }
    }

    @Test
    fun lastGpsDistanceKm_nullOnLastValidLine_returnsNull() {
        withLog { _, log ->
            log.append(sample(4.2, "2026-10-05T12:00:00Z"))
            log.append(sample(null, "2026-10-05T12:00:10Z"))
            assertNull(log.lastGpsDistanceKm(VIN, DAY))
        }
    }

    @Test
    fun lastGpsDistanceKm_missingOpenFileOrOtherVin_returnsNull() {
        withLog { _, log ->
            assertNull(log.lastGpsDistanceKm(VIN, DAY))
            log.append(sample(4.2, "2026-10-05T12:00:00Z", vin = "OTHERVIN000000001"))
            assertNull(log.lastGpsDistanceKm(VIN, DAY))
            assertNull(log.lastGpsDistanceKm("OTHERVIN000000001", LocalDate.parse("2026-10-04")))
        }
    }

    @Test
    fun lastGpsDistanceKm_closedGeoJsonOnly_returnsNull() {
        withLog { _, log ->
            log.append(sample(4.2, "2026-10-05T12:00:00Z"))
            log.closeFinishedDays(Instant.parse("2026-10-06T00:00:00Z"))
            assertNull(log.lastGpsDistanceKm(VIN, DAY))
        }
    }
}

private const val VIN = "WVWZZZ1JZXW000001"
private val DAY: LocalDate = LocalDate.parse("2026-10-05")

private fun sample(distanceKm: Double?, at: String, vin: String = VIN) =
    historySample(time = Instant.parse(at), vin = vin).copy(gpsDistanceKm = distanceKm)
