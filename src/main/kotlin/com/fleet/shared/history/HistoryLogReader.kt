package com.fleet.shared.history

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

object HistoryLogReader {
    fun read(file: File): ReadResult {
        val samples = mutableListOf<HistorySample>()
        val errors = mutableListOf<String>()
        var skipped = 0
        try {
            readerFor(file).use { reader ->
                var lineNumber = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    lineNumber++
                    if (line.isEmpty()) {
                        skipped++
                        errors.add("empty line $lineNumber in ${file.name}")
                        continue
                    }
                    val parsed = runCatching {
                        HistoryJson.decodeFromString(HistorySample.serializer(), line)
                    }
                    val sample = parsed.getOrElse { error ->
                        skipped++
                        errors.add("malformed line $lineNumber in ${file.name}: ${error.message}")
                        null
                    }
                    if (sample == null) continue
                    if (sample.schema != 1) {
                        skipped++
                        errors.add("unsupported schema ${sample.schema} (expected 1) at line $lineNumber in ${file.name}")
                        continue
                    }
                    samples.add(sample)
                }
            }
        } catch (error: Exception) {
            errors.add("cannot read ${file.name}: ${error.message}")
        }
        return ReadResult(samples, skipped, errors)
    }

    private fun readerFor(file: File): BufferedReader {
        val raw = FileInputStream(file)
        val stream = if (file.name.endsWith(".gz")) GZIPInputStream(raw) else raw
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
    }
}
