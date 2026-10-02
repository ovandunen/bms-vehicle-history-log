package com.fleet.shared.history

import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals

class GoldenSerializationTest {
    @Test
    fun sampleFullRoundTripMatchesGoldenBytes() {
        assertGolden("golden/sample_v1_full.ndjson", HistoryJson.encodeToString(HistorySample.serializer(), HistoryFixtures.fullSample) + "\n")
        assertEquals(HistoryFixtures.fullSample, HistoryJson.decodeFromString(HistorySample.serializer(), goldenText("golden/sample_v1_full.ndjson").trimEnd('\n')))
    }

    @Test
    fun sampleMinimalOmitsNullsAndMatchesGoldenBytes() {
        assertGolden("golden/sample_v1_minimal.ndjson", HistoryJson.encodeToString(HistorySample.serializer(), HistoryFixtures.minimalSample) + "\n")
        assertEquals(HistoryFixtures.minimalSample, HistoryJson.decodeFromString(HistorySample.serializer(), goldenText("golden/sample_v1_minimal.ndjson").trimEnd('\n')))
    }

    @Test
    fun tripFullRoundTripMatchesGoldenBytes() {
        assertGolden("golden/trip_v1_full.json", TripSummaryCodec.encode(HistoryFixtures.fullTrip) + "\n")
        assertEquals(HistoryFixtures.fullTrip, TripSummaryCodec.decode(goldenText("golden/trip_v1_full.json").trimEnd('\n')).getOrThrow())
    }

    @Test
    fun tripMinimalOmitsNullsAndMatchesGoldenBytes() {
        assertGolden("golden/trip_v1_minimal.json", TripSummaryCodec.encode(HistoryFixtures.minimalTrip) + "\n")
        assertEquals(HistoryFixtures.minimalTrip, TripSummaryCodec.decode(goldenText("golden/trip_v1_minimal.json").trimEnd('\n')).getOrThrow())
    }

    @Test
    fun packCurrentNegativeRoundTripsUnchanged() {
        val encoded = HistoryJson.encodeToString(HistorySample.serializer(), HistoryFixtures.fullSample)
        val decoded = HistoryJson.decodeFromString(HistorySample.serializer(), encoded)
        assertEquals(-15.25, decoded.packCurrentA)
    }

    @Test
    fun unknownExtraFieldIsIgnored() {
        val json = goldenText("golden/sample_v1_minimal.ndjson").trimEnd('\n').replace("}", ",\"extraField\":true}")
        val decoded = HistoryJson.decodeFromString(HistorySample.serializer(), json)
        assertEquals(HistoryFixtures.minimalSample, decoded)
    }

    @Test
    fun schema2LineIsRejectedWithErrorEntry() {
        val line = HistoryJson.encodeToString(HistorySample.serializer(), HistoryFixtures.minimalSample.copy(schema = 2))
        val file = kotlin.io.path.createTempFile(suffix = ".ndjson").toFile()
        file.writeText(line + "\n", StandardCharsets.UTF_8)
        val result = HistoryLogReader.read(file)
        assertEquals(0, result.samples.size)
        assertEquals(1, result.skippedLines)
        assertEquals(true, result.errors.any { it.contains("unsupported schema 2") })
    }

    private fun assertGolden(resource: String, encoded: String) {
        assertEquals(goldenText(resource), encoded)
    }

    private fun goldenText(resource: String): String {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream(resource)) { "missing $resource" }
        return stream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
    }
}
