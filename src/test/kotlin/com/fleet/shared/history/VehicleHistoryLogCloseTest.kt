package com.fleet.shared.history

import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class VehicleHistoryLogCloseTest {

    @Test
    fun closeFinishedDays_closesYesterday_leavesTodayOpen() {
        withLog { root, log ->
            log.append(historySample(time = Instant.parse("2026-10-04T12:00:00Z")))
            log.append(historySample(time = Instant.parse("2026-10-05T12:00:00Z")))
            val skipped = log.closeFinishedDays(Instant.parse("2026-10-05T08:00:00Z"))
            val dir = root.resolve("WVWZZZ1JZXW000001")
            assertEquals(0, skipped)
            assertTrue(Files.isRegularFile(dir.resolve("2026-10-04.geojson")))
            assertFalse(Files.exists(dir.resolve("2026-10-04.geojsonl")))
            assertTrue(Files.isRegularFile(dir.resolve("2026-10-05.geojsonl")))
            assertFalse(Files.exists(dir.resolve("2026-10-05.geojson")))
        }
    }

    @Test
    fun close_truncatedLastLine_keepsValidFeaturesAndSkipsOne() {
        withLog { root, log ->
            log.append(historySample(time = Instant.parse("2026-10-04T12:00:00Z")))
            log.append(historySample(time = Instant.parse("2026-10-04T12:00:10Z")))
            val open = root.resolve("WVWZZZ1JZXW000001/2026-10-04.geojsonl")
            Files.write(open, "{\"type\":\"Feature\"".toByteArray(), StandardOpenOption.APPEND)
            val skipped = log.closeFinishedDays(Instant.parse("2026-10-05T00:00:00Z"))
            assertEquals(1, skipped)
            val closed = Files.readString(root.resolve("WVWZZZ1JZXW000001/2026-10-04.geojson")).trim()
            val features = Json.parseToJsonElement(closed).jsonObject.getValue("features").jsonArray
            assertEquals(2, features.size)
            assertFalse(Files.exists(open))
        }
    }

    @Test
    fun pendingUploads_listsOnlyClosedFilesWithSha256() {
        withLog { root, log ->
            log.append(historySample(time = Instant.parse("2026-10-04T12:00:00Z"), vin = "VIN-B"))
            log.append(historySample(time = Instant.parse("2026-10-04T12:00:00Z"), vin = "VIN-A"))
            log.append(historySample(time = Instant.parse("2026-10-05T12:00:00Z"), vin = "VIN-A"))
            log.closeFinishedDays(Instant.parse("2026-10-05T00:00:00Z"))
            val pending = log.pendingUploads()
            assertEquals(listOf("VIN-A", "VIN-B"), pending.map { it.vin })
            pending.forEach { file ->
                assertTrue(file.path.fileName.toString().endsWith(".geojson"))
                assertFalse(file.path.fileName.toString().endsWith(".geojsonl"))
                assertEquals(sha256Hex(file.path), file.sha256)
            }
            assertTrue(Files.isRegularFile(root.resolve("VIN-A/2026-10-05.geojsonl")))
            assertEquals(2, pending.size)
        }
    }

    @Test
    fun confirmUploaded_matchingSha256_deletesFile() {
        withLog { root, log ->
            log.append(historySample(time = Instant.parse("2026-10-04T12:00:00Z")))
            log.closeFinishedDays(Instant.parse("2026-10-05T00:00:00Z"))
            val file = log.pendingUploads().single()
            assertTrue(log.confirmUploaded(file, file.sha256))
            assertFalse(Files.exists(file.path))
            assertEquals(emptyList(), log.pendingUploads())
        }
    }

    @Test
    fun confirmUploaded_wrongSha256_keepsFile() {
        withLog { _, log ->
            log.append(historySample(time = Instant.parse("2026-10-04T12:00:00Z")))
            log.closeFinishedDays(Instant.parse("2026-10-05T00:00:00Z"))
            val file = log.pendingUploads().single()
            assertFalse(log.confirmUploaded(file, "00"))
            assertTrue(Files.isRegularFile(file.path))
        }
    }
}
