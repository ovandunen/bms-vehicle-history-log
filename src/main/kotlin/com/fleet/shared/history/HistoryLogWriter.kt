package com.fleet.shared.history

import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.withLock

class HistoryLogWriter(
    private val directory: File,
    vehicleId: String,
    private val clock: () -> Instant,
    private val flushEveryRecords: Int = 10,
    private val flushEveryMillis: Long = 10_000,
) : AutoCloseable {
    private val sanitizedVehicleId = HistoryFileNames.sanitizeVehicleId(vehicleId)
    private val lock = ReentrantLock()
    private var writer: BufferedWriter? = null
    private var openDate: LocalDate? = null
    private var openFile: File? = null
    private var recordsSinceFlush: Int = 0
    private var lastFlushAt: Instant = clock()

    init {
        recover()
    }

    fun append(sample: HistorySample): Result<Unit> = lock.withLock {
        runCatching {
            val date = HistoryFileNames.utcDateOfTimestamp(sample.ts)
            rotateIfNeeded(date)
            val out = openFor(date)
            out.write(HistoryJson.encodeToString(HistorySample.serializer(), sample))
            out.write("\n")
            recordsSinceFlush++
            val now = clock()
            if (recordsSinceFlush >= flushEveryRecords ||
                now.toEpochMilli() - lastFlushAt.toEpochMilli() >= flushEveryMillis
            ) {
                flushLocked()
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error ->
                resetStream()
                Result.failure(error)
            },
        )
    }

    fun recover() = lock.withLock {
        recoverLocked()
    }

    override fun close() = lock.withLock {
        flushLocked()
        resetStream()
    }

    private fun rotateIfNeeded(date: LocalDate) {
        val current = openDate ?: return
        if (current == date) return
        flushLocked()
        val previous = openFile
        resetStream()
        if (previous != null) {
            compressPlain(previous)
        }
    }

    private fun openFor(date: LocalDate): BufferedWriter {
        val existing = writer
        if (existing != null && openDate == date) return existing
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("cannot create log directory ${directory.path}")
        }
        val file = File(directory, HistoryFileNames.plainName(sanitizedVehicleId, date))
        val stream = FileOutputStream(file, true)
        val created = BufferedWriter(OutputStreamWriter(stream, StandardCharsets.UTF_8))
        writer = created
        openDate = date
        openFile = file
        recordsSinceFlush = 0
        lastFlushAt = clock()
        return created
    }

    private fun flushLocked() {
        writer?.flush()
        recordsSinceFlush = 0
        lastFlushAt = clock()
    }

    private fun resetStream() {
        try {
            writer?.close()
        } catch (_: IOException) {
            // Next append reopens.
        }
        writer = null
        openDate = null
        openFile = null
        recordsSinceFlush = 0
    }

    private fun recoverLocked() {
        val today = HistoryFileNames.utcDate(clock())
        val files = directory.listFiles() ?: return
        for (file in files) {
            val parsed = HistoryFileNames.parse(file) ?: continue
            if (parsed.gzip) continue
            if (parsed.vehicleKey != sanitizedVehicleId) continue
            if (!parsed.date.isBefore(today)) continue
            compressPlain(file)
        }
    }

    private fun compressPlain(plain: File) {
        if (!plain.isFile) return
        val gz = File(plain.parentFile, plain.name + ".gz")
        val tmp = File(plain.parentFile, plain.name + ".gz.tmp")
        try {
            FileOutputStream(tmp).use { fos ->
                GZIPOutputStream(UnclosingOutputStream(fos)).use { gzip ->
                    FileInputStream(plain).use { input -> input.copyTo(gzip) }
                }
                fos.fd.sync()
            }
            try {
                Files.move(
                    tmp.toPath(),
                    gz.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), gz.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            if (!plain.delete() && plain.exists()) {
                gz.delete()
            }
        } catch (_: Exception) {
            tmp.delete()
        }
    }

    private class UnclosingOutputStream(inner: FileOutputStream) : FilterOutputStream(inner) {
        override fun close() {
            flush()
        }
    }
}
