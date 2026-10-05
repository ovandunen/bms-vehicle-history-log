package com.fleet.shared.history

import java.time.Instant

interface VehicleHistoryLog {
    fun append(sample: HistorySample)

    /** Closes every open day before [now]'s UTC date. Returns how many invalid last lines were skipped. */
    fun closeFinishedDays(now: Instant): Int

    fun pendingUploads(): List<DayFile>

    fun confirmUploaded(dayFile: DayFile, sha256: String): Boolean
}
