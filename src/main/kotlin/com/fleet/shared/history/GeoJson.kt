package com.fleet.shared.history

import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object GeoJson {
    private val json = Json

    fun featureLine(sample: HistorySample): String {
        fun decimal(value: Double?): JsonElement = value?.let { JsonPrimitive(it) } ?: JsonNull
        fun whole(value: Int?): JsonElement = value?.let { JsonPrimitive(it) } ?: JsonNull
        val coordinates = buildList {
            add(JsonPrimitive(sample.longitude))
            add(JsonPrimitive(sample.latitude))
            if (sample.altitudeM != null) add(JsonPrimitive(sample.altitudeM))
        }
        return buildJsonObject {
            put("type", "Feature")
            put(
                "geometry",
                buildJsonObject {
                    put("type", "Point")
                    put("coordinates", JsonArray(coordinates))
                },
            )
            put(
                "properties",
                buildJsonObject {
                    put("time", DateTimeFormatter.ISO_INSTANT.format(sample.time))
                    put("vin", sample.vin)
                    put("battery_id", sample.batteryId)
                    put("speed_kmh", decimal(sample.speedKmh))
                    put("gps_distance_km", decimal(sample.gpsDistanceKm))
                    put("odometer_km", decimal(sample.odometerKm))
                    put("soc_percent", decimal(sample.socPercent))
                    put("pack_voltage_v", decimal(sample.packVoltageV))
                    put("pack_current_a", decimal(sample.packCurrentA))
                    put("consumption_kwh_per_100km", decimal(sample.consumptionKwhPer100km))
                    put("outside_temp_c", decimal(sample.outsideTempC))
                    put("humidity_percent", decimal(sample.humidityPercent))
                    put("pm25", whole(sample.pm25))
                    put("pm10", whole(sample.pm10))
                    put("fix_quality", whole(sample.fixQuality))
                    put("satellites", whole(sample.satellites))
                },
            )
        }.toString()
    }

    fun featureCollection(featureLines: List<String>): String =
        "{\"type\":\"FeatureCollection\",\"features\":[${featureLines.joinToString(",")}]}"

    fun isJson(line: String): Boolean =
        try {
            json.parseToJsonElement(line)
            true
        } catch (_: Exception) {
            false
        }
}
