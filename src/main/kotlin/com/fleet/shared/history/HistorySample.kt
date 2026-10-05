package com.fleet.shared.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class HistorySample(
    val time: Instant,
    val vin: String,
    val batteryId: String,
    val longitude: Double,
    val latitude: Double,
    val altitudeM: Double? = null,
    val speedKmh: Double? = null,
    val gpsDistanceKm: Double? = null,
    val odometerKm: Double? = null,
    val socPercent: Double? = null,
    val packVoltageV: Double? = null,
    val packCurrentA: Double? = null,
    val consumptionKwhPer100km: Double? = null,
    val outsideTempC: Double? = null,
    val humidityPercent: Double? = null,
    val pm25: Int? = null,
    val pm10: Int? = null,
    val fixQuality: Int? = null,
    val satellites: Int? = null,
) {
    init {
        require(longitude in -180.0..180.0) { "longitude out of range" }
        require(latitude in -90.0..90.0) { "latitude out of range" }
        require(vin.isNotBlank()) { "vin blank" }
        require(batteryId.isNotBlank()) { "battery_id blank" }
    }

    fun utcDay(): LocalDate = time.atZone(ZoneOffset.UTC).toLocalDate()
}

data class DayKey(
    val vin: String,
    val day: LocalDate,
)
