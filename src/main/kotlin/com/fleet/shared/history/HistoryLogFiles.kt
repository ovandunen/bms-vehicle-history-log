package com.fleet.shared.history

import java.io.File
import java.time.Instant

class HistoryLogFiles(private val directory: File) {
    fun listClosed(): List<File> {
        val files = directory.listFiles() ?: return emptyList()
        return files
            .mapNotNull { file -> HistoryFileNames.parse(file)?.takeIf { it.gzip } }
            .sortedWith(compareBy({ it.date }, { it.file.name }))
            .map { it.file }
    }

    fun deleteOlderThan(days: Int, now: Instant): Int {
        val cutoff = HistoryFileNames.utcDate(now).minusDays(days.toLong())
        var deleted = 0
        for (file in listClosed()) {
            val parsed = HistoryFileNames.parse(file) ?: continue
            if (parsed.date.isBefore(cutoff) && file.delete()) {
                deleted++
            }
        }
        return deleted
    }
}
