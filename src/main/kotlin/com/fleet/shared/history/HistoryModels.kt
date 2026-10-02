package com.fleet.shared.history

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BatteryIdSource {
    @SerialName("dummy")
    DUMMY,

    @SerialName("vcu")
    VCU,

    @SerialName("csms")
    CSMS,

    @SerialName("manual")
    MANUAL,
}

fun dummyBatteryId(vehicleId: String): String = "DUMMY-$vehicleId"

@Serializable
data class HistorySample(
    val schema: Int = 1,
    val ts: String,
    val vehicleId: String,
    val batteryId: String,
    val batteryIdSource: BatteryIdSource,
    val tripId: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val altM: Double? = null,
    val speedKmh: Double? = null,
    val headingDeg: Double? = null,
    val gpsAccuracyM: Double? = null,
    val odometerKm: Double,
    val tripKm: Double,
    val soc: Double? = null,
    val packVoltageV: Double? = null,
    val packCurrentA: Double? = null,
    val packTempMinC: Double? = null,
    val packTempMaxC: Double? = null,
    val energyConsumedKwh: Double,
    val energyRegenKwh: Double,
    val batteryDataStale: Boolean,
    val ambientTempC: Double? = null,
    val humidityPct: Double? = null,
    val pm25: Int? = null,
    val pm10: Int? = null,
    val sensorError: Boolean? = null,
)

@Serializable
data class TripSummary(
    val schema: Int = 1,
    val tripId: String,
    val vehicleId: String,
    val batteryId: String,
    val batteryIdSource: BatteryIdSource,
    val startedAt: String,
    val endedAt: String,
    val odometerStartKm: Double,
    val odometerEndKm: Double,
    val distanceKm: Double,
    val energyConsumedKwh: Double,
    val energyRegenKwh: Double,
    val energyNetKwh: Double,
    val consumptionKwhPerKm: Double? = null,
    val socStart: Double? = null,
    val socEnd: Double? = null,
    val startLat: Double? = null,
    val startLon: Double? = null,
    val startAltM: Double? = null,
    val endLat: Double? = null,
    val endLon: Double? = null,
    val endAltM: Double? = null,
    val co2SavingKg: Double? = null,
)

data class ReadResult(
    val samples: List<HistorySample>,
    val skippedLines: Int,
    val errors: List<String>,
)
