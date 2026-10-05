package com.fleet.shared.history

import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText

class FileVehicleHistoryLog(
    private val root: Path,
) : VehicleHistoryLog {

    override fun append(sample: HistorySample) {
        val day = sample.utcDay()
        openGeoJsonl()
            .filter { it.parent.name == sample.vin && dayOf(it).isBefore(day) }
            .sortedBy { dayOf(it) }
            .forEach { closeOpenFile(it) }
        appendLine(root.resolve(sample.vin).resolve("$day.geojsonl"), GeoJson.featureLine(sample))
    }

    override fun closeFinishedDays(now: Instant): Int {
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        return openGeoJsonl()
            .filter { dayOf(it).isBefore(today) }
            .sumOf { closeOpenFile(it) }
    }

    override fun pendingUploads(): List<DayFile> {
        if (!Files.isDirectory(root)) return emptyList()
        val vins = Files.list(root).use { it.filter { dir -> Files.isDirectory(dir) }.toList() }
        return vins.flatMap { vinDir ->
            Files.list(vinDir).use { files ->
                files.filter { isClosedDay(it.name) }.map { path ->
                    DayFile(
                        vin = vinDir.name,
                        day = dayOf(path),
                        path = path,
                        sha256 = fileSha256(path),
                    )
                }.toList()
            }
        }.sortedWith(compareBy({ it.vin }, { it.day }))
    }

    override fun confirmUploaded(dayFile: DayFile, sha256: String): Boolean {
        if (!dayFile.path.exists()) return false
        if (fileSha256(dayFile.path) != sha256) return false
        Files.delete(dayFile.path)
        return true
    }

    private fun closeOpenFile(open: Path): Int {
        val lines = open.readLines()
        val valid = ArrayList<String>(lines.size)
        var skipped = 0
        for ((index, line) in lines.withIndex()) {
            if (line.isEmpty()) continue
            if (GeoJson.isJson(line)) {
                valid += line
            } else if (index == lines.lastIndex) {
                skipped++
            } else {
                error("invalid history line in $open")
            }
        }
        val closed = open.resolveSibling(open.name.removeSuffix(".geojsonl") + ".geojson")
        val tmp = open.resolveSibling(closed.name + ".partial")
        Files.writeString(tmp, GeoJson.featureCollection(valid) + "\n")
        Files.move(tmp, closed, StandardCopyOption.ATOMIC_MOVE)
        Files.delete(open)
        return skipped
    }

    private fun appendLine(file: Path, line: String) {
        Files.createDirectories(file.parent)
        FileOutputStream(file.toFile(), true).channel.use { channel ->
            channel.write(ByteBuffer.wrap((line + "\n").toByteArray(Charsets.UTF_8)))
            channel.force(true)
        }
    }

    private fun openGeoJsonl(): List<Path> {
        if (!Files.isDirectory(root)) return emptyList()
        val vins = Files.list(root).use { it.filter { dir -> Files.isDirectory(dir) }.toList() }
        return vins.flatMap { vin ->
            Files.list(vin).use { files ->
                files.filter { it.name.endsWith(".geojsonl") }.toList()
            }
        }
    }
}

internal fun dayOf(path: Path): LocalDate =
    LocalDate.parse(path.name.removeSuffix(".geojsonl").removeSuffix(".geojson"))

private fun isClosedDay(name: String): Boolean =
    name.endsWith(".geojson") && !name.endsWith(".geojsonl")

internal fun fileSha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(path.readText().toByteArray(Charsets.UTF_8))
    return digest.digest().joinToString("") { "%02x".format(it) }
}
