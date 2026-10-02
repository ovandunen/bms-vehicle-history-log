package com.fleet.shared.history

import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryLogWriterTest {
    @Test
    fun dayChangeCompressesPreviousUtcDay() {
        val dir = tempDir()
        val clock = mutableClock("2026-10-02T00:00:00Z")
        HistoryLogWriter(dir, "veh-01", clock, flushEveryRecords = 1).use { writer ->
            repeat(3) { index ->
                assertTrue(writer.append(sampleAt("2026-10-01T10:0$index:00.000Z", index)).isSuccess)
            }
            assertTrue(writer.append(sampleAt("2026-10-02T01:00:00.000Z", 3)).isSuccess)
        }
        val gz = File(dir, "veh-01_2026-10-01.ndjson.gz")
        val plainOld = File(dir, "veh-01_2026-10-01.ndjson")
        val plainNew = File(dir, "veh-01_2026-10-02.ndjson")
        assertTrue(gz.isFile)
        assertFalse(plainOld.exists())
        assertTrue(plainNew.isFile)
        val result = HistoryLogReader.read(gz)
        assertEquals(3, result.samples.size)
        assertEquals(1, HistoryLogReader.read(plainNew).samples.size)
    }

    @Test
    fun dayBoundaryUsesUtcIndependentOfDefaultTimezone() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("Africa/Dakar", "Europe/Zurich")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val dir = tempDir()
                HistoryLogWriter(dir, "veh-01", { Instant.parse("2026-10-01T22:00:00Z") }, flushEveryRecords = 1).use { writer ->
                    assertTrue(writer.append(sampleAt("2026-10-01T23:30:00.000Z", 0)).isSuccess)
                    assertTrue(writer.append(sampleAt("2026-10-02T00:30:00.000Z", 1)).isSuccess)
                }
                assertTrue(File(dir, "veh-01_2026-10-01.ndjson.gz").isFile)
                assertTrue(File(dir, "veh-01_2026-10-02.ndjson").isFile)
                assertFalse(File(dir, "veh-01_2026-10-02.ndjson.gz").exists())
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun recoverCompressesTruncatedPastDayAndReaderSkipsLastLine() {
        val dir = tempDir()
        val complete = HistoryJson.encodeToString(HistorySample.serializer(), sampleAt("2026-10-01T10:00:00.000Z", 0))
        val truncated = HistoryJson.encodeToString(HistorySample.serializer(), sampleAt("2026-10-01T10:01:00.000Z", 1)).dropLast(12)
        File(dir, "veh-01_2026-10-01.ndjson").writeText(complete + "\n" + truncated, StandardCharsets.UTF_8)
        HistoryLogWriter(dir, "veh-01", { Instant.parse("2026-10-02T12:00:00Z") }).close()
        val gz = File(dir, "veh-01_2026-10-01.ndjson.gz")
        assertTrue(gz.isFile)
        assertFalse(File(dir, "veh-01_2026-10-01.ndjson").exists())
        val result = HistoryLogReader.read(gz)
        assertEquals(1, result.samples.size)
        assertEquals(1, result.skippedLines)
    }

    @Test
    fun appendOnReadOnlyDirectoryReturnsFailureThenSucceedsWhenWritable() {
        val dir = tempDir()
        val writer = HistoryLogWriter(dir, "veh-01", { Instant.parse("2026-10-02T12:00:00Z") }, flushEveryRecords = 1)
        assertTrue(dir.setWritable(false, false))
        try {
            val failed = writer.append(sampleAt("2026-10-02T12:00:00.000Z", 0))
            assertTrue(failed.isFailure)
            assertTrue(failed.exceptionOrNull() is Exception)
            assertTrue(dir.setWritable(true, false))
            val ok = writer.append(sampleAt("2026-10-02T12:00:01.000Z", 1))
            assertTrue(ok.isSuccess, ok.exceptionOrNull()?.toString() ?: "success")
        } finally {
            dir.setWritable(true, false)
            writer.close()
        }
    }

    @Test
    fun deleteOlderThanRemovesOnlyOldGzipFiles() {
        val dir = tempDir()
        File(dir, "veh-01_2026-10-01.ndjson.gz").writeText("x")
        File(dir, "veh-01_2026-10-09.ndjson.gz").writeText("x")
        File(dir, "veh-01_2026-10-10.ndjson").writeText("plain")
        val deleted = HistoryLogFiles(dir).deleteOlderThan(3, Instant.parse("2026-10-10T12:00:00Z"))
        assertEquals(1, deleted)
        assertFalse(File(dir, "veh-01_2026-10-01.ndjson.gz").exists())
        assertTrue(File(dir, "veh-01_2026-10-09.ndjson.gz").isFile)
        assertTrue(File(dir, "veh-01_2026-10-10.ndjson").isFile)
    }

    @Test
    fun concurrentAppendWritesCompleteLines() {
        val dir = tempDir()
        val writer = HistoryLogWriter(dir, "veh-01", { Instant.parse("2026-10-02T12:00:00Z") }, flushEveryRecords = 50)
        val threads = 4
        val perThread = 1000
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) { thread ->
            pool.submit {
                start.await()
                repeat(perThread) { index ->
                    val n = thread * perThread + index
                    val result = writer.append(sampleAt("2026-10-02T12:00:00.000Z", n))
                    check(result.isSuccess) { result.exceptionOrNull()!!.stackTraceToString() }
                }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(60, TimeUnit.SECONDS))
        pool.shutdown()
        writer.close()
        val file = File(dir, "veh-01_2026-10-02.ndjson")
        val lines = file.readLines(StandardCharsets.UTF_8)
        assertEquals(4000, lines.size)
        lines.forEach { line ->
            HistoryJson.decodeFromString(HistorySample.serializer(), line)
        }
    }

    @Test
    fun vehicleIdWithSlashOrSpacesIsSanitisedInFileName() {
        val dir = tempDir()
        HistoryLogWriter(dir, "fleet/veh 01", { Instant.parse("2026-10-02T12:00:00Z") }, flushEveryRecords = 1).use { writer ->
            assertTrue(writer.append(sampleAt("2026-10-02T12:00:00.000Z", 0).copy(vehicleId = "fleet/veh 01")).isSuccess)
        }
        assertTrue(File(dir, "fleet_veh_01_2026-10-02.ndjson").isFile)
    }

    @Test
    fun listClosedSortsGzipByDate() {
        val dir = tempDir()
        File(dir, "veh-01_2026-10-02.ndjson.gz").writeText("b")
        File(dir, "veh-01_2026-10-01.ndjson.gz").writeText("a")
        File(dir, "veh-01_2026-10-02.ndjson").writeText("plain")
        val listed = HistoryLogFiles(dir).listClosed().map { it.name }
        assertEquals(listOf("veh-01_2026-10-01.ndjson.gz", "veh-01_2026-10-02.ndjson.gz"), listed)
    }

    @Test
    fun dummyBatteryIdPrefix() {
        assertEquals("DUMMY-veh-01", dummyBatteryId("veh-01"))
    }

    @Test
    fun gzipContainsExpectedRecordCount() {
        val dir = tempDir()
        HistoryLogWriter(dir, "veh-01", { Instant.parse("2026-10-02T00:00:00Z") }, flushEveryRecords = 1).use { writer ->
            writer.append(sampleAt("2026-10-01T10:00:00.000Z", 0))
            writer.append(sampleAt("2026-10-02T10:00:00.000Z", 1))
        }
        GZIPInputStream(File(dir, "veh-01_2026-10-01.ndjson.gz").inputStream()).use { gzip ->
            val text = gzip.readBytes().toString(StandardCharsets.UTF_8)
            assertEquals(1, text.trimEnd().lines().size)
        }
    }

    private fun sampleAt(ts: String, index: Int): HistorySample =
        HistoryFixtures.minimalSample.copy(ts = ts, tripId = "trip-$index", tripKm = index.toDouble())

    private fun mutableClock(iso: String): () -> Instant {
        val instant = Instant.parse(iso)
        return { instant }
    }

    private fun tempDir(): File = kotlin.io.path.createTempDirectory("history-log").toFile()
}
