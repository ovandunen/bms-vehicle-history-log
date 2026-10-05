package com.fleet.shared.history

import java.nio.file.Path
import java.time.LocalDate

data class DayFile(
    val vin: String,
    val day: LocalDate,
    val path: Path,
    val sha256: String,
)
