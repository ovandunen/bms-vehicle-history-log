# bms-vehicle-history-log

Pure Kotlin/JVM library for vehicle history: schema v1 records, daily NDJSON files on the tablet, and a reader for a later backend importer. No Android dependency.

**Coordinates:** `com.fleet.shared:bms-vehicle-history-log:1.0.0-SNAPSHOT`  
**Package:** `com.fleet.shared.history`

## Consume from the BMS app (composite build)

In the app `settings.gradle.kts`:

```kotlin
includeBuild("../bms-vehicle-history-log")
```

In the app module:

```kotlin
implementation("com.fleet.shared:bms-vehicle-history-log")
```

Sampling (1 s / 30 s), HTTPS upload, and Android wiring belong in `bms-monitoring-app`, not this library.

## JSON rules

Shared instance: `HistoryJson`.

- `encodeDefaults = true` so `schema` is always written
- `explicitNulls = false` so null fields are omitted (never written as `0`)
- decode `ignoreUnknownKeys = true` (forward compatible)
- no pretty printing; NDJSON is one record per line, terminated by `\n`
- a line with `schema != 1` is skipped by `HistoryLogReader` with an error entry; it does not throw
- pack current: **negative = discharging**

## Dummy battery id

Until a real battery identity source exists, the writer app should set:

- `batteryId = dummyBatteryId(vehicleId)` → `DUMMY-{vehicleId}`
- `batteryIdSource = dummy`

## Schema v1 — `HistorySample` (one NDJSON line)

| Field | Type | Notes |
|---|---|---|
| schema | Int | always 1 |
| ts | String | UTC ISO-8601 with milliseconds, e.g. `2026-10-02T08:15:30.123Z` |
| vehicleId | String | |
| batteryId | String | |
| batteryIdSource | enum | `dummy`, `vcu`, `csms`, `manual` |
| tripId | String | |
| lat, lon, altM | Double? | |
| speedKmh, headingDeg, gpsAccuracyM | Double? | |
| odometerKm, tripKm | Double | |
| soc | Double? | |
| packVoltageV, packCurrentA | Double? | current A, negative = discharging |
| packTempMinC, packTempMaxC | Double? | |
| energyConsumedKwh, energyRegenKwh | Double | cumulative per trip |
| batteryDataStale | Boolean | |
| ambientTempC, humidityPct | Double? | |
| pm25, pm10 | Int? | |
| sensorError | Boolean? | |

## Schema v1 — `TripSummary` (one record per closed trip)

| Field | Type |
|---|---|
| schema | Int = 1 |
| tripId, vehicleId, batteryId | String |
| batteryIdSource | enum (same as sample) |
| startedAt, endedAt | String, UTC ISO-8601 |
| odometerStartKm, odometerEndKm, distanceKm | Double |
| energyConsumedKwh, energyRegenKwh, energyNetKwh | Double |
| consumptionKwhPerKm | Double? |
| socStart, socEnd | Double? |
| startLat, startLon, startAltM, endLat, endLon, endAltM | Double? |
| co2SavingKg | Double? |

Encode/decode via `TripSummaryCodec`.

## Files, rotation, compression, recovery, retention

- Directory of daily files. File name: `<sanitisedVehicleId>_<yyyy-MM-dd>.ndjson`
- Vehicle id in the name: only `[A-Za-z0-9._-]`; every other character becomes `_`
- Date is the **UTC** calendar day of `sample.ts`, independent of the JVM default timezone
- `HistoryLogWriter.append` never throws; `IOException` becomes `Result.failure` and the next call may retry
- Buffered writes; flush every `flushEveryRecords` (default 10) or `flushEveryMillis` (default 10_000, measured with the injected clock), and on `close()`
- When `sample.ts` moves to a new UTC day: flush, close, compress the previous file
- Compression: write `<name>.ndjson.gz.tmp`, fsync, rename to `<name>.ndjson.gz`, then delete the plain file. On failure the plain file is kept
- `recover()` (also on construct): compress every leftover plain `.ndjson` for this vehicle whose date is a **past** UTC day; today’s file stays open for append
- `HistoryLogFiles.listClosed()`: `.ndjson.gz` files, oldest date first
- `HistoryLogFiles.deleteOlderThan(days, now)`: deletes closed gzip files whose UTC date is strictly before `now` (UTC date) minus `days`. Never deletes the current day’s plain file
