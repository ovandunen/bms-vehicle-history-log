package com.fleet.shared.history

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

internal fun withLog(block: (Path, FileVehicleHistoryLog) -> Unit) {
    val root = Files.createTempDirectory("vehicle-history")
    try {
        block(root, FileVehicleHistoryLog(root))
    } finally {
        root.toFile().deleteRecursively()
    }
}

internal fun historySample(
    time: Instant = Instant.parse("2026-10-05T09:14:20Z"),
    vin: String = "WVWZZZ1JZXW000001",
    batteryId: String = "BAT-1",
    longitude: Double = 13.405,
    latitude: Double = 52.52,
    altitudeM: Double? = 35.0,
): HistorySample = HistorySample(
    time = time,
    vin = vin,
    batteryId = batteryId,
    longitude = longitude,
    latitude = latitude,
    altitudeM = altitudeM,
)

internal fun sha256Hex(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(Files.readAllBytes(path))
    return digest.digest().joinToString("") { "%02x".format(it) }
}
