package com.fleet.shared.history

import java.time.Instant
import java.time.LocalDate

interface VehicleHistoryLog {
    fun append(sample: HistorySample)

    /** Closes every open day before [now]'s UTC date. Returns how many invalid last lines were skipped. */
    fun closeFinishedDays(now: Instant): Int

    fun pendingUploads(): List<DayFile>

    fun confirmUploaded(dayFile: DayFile, sha256: String): Boolean

    /**
     * gps_distance_km of the last valid line in the open day file.
     * Null when that file is missing, has no valid line, or the value is null.
     */
    fun lastGpsDistanceKm(vin: String, day: LocalDate): Double?
}
