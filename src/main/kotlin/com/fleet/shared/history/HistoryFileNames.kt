package com.fleet.shared.history

import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

internal object HistoryFileNames {
    private val allowed = Regex("[A-Za-z0-9._-]")
    private val dated = Pattern.compile("^(.*)_(\\d{4}-\\d{2}-\\d{2})\\.ndjson(\\.gz)?$")
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun sanitizeVehicleId(vehicleId: String): String =
        vehicleId.map { ch -> if (allowed.matches(ch.toString())) ch else '_' }.joinToString("")

    fun utcDate(instant: Instant): LocalDate = instant.atZone(ZoneOffset.UTC).toLocalDate()

    fun utcDateOfTimestamp(ts: String): LocalDate = utcDate(Instant.parse(ts))

    fun plainName(sanitizedVehicleId: String, date: LocalDate): String =
        "${sanitizedVehicleId}_${date.format(dateFmt)}.ndjson"

    fun gzipName(sanitizedVehicleId: String, date: LocalDate): String =
        plainName(sanitizedVehicleId, date) + ".gz"

    data class DatedFile(val file: File, val vehicleKey: String, val date: LocalDate, val gzip: Boolean)

    fun parse(file: File): DatedFile? {
        val matcher = dated.matcher(file.name)
        if (!matcher.matches()) return null
        val date = LocalDate.parse(matcher.group(2), dateFmt)
        val gzip = matcher.group(3) != null
        return DatedFile(file, matcher.group(1), date, gzip)
    }
}
