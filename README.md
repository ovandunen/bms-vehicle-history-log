# bms-vehicle-history-log

Plain Kotlin/JVM library. One GeoJSON sample per call, one file per VIN and UTC day. No Android dependency. Upload is not in this library.

**Coordinates:** `com.fleet.shared:bms-vehicle-history-log:1.0.0-SNAPSHOT`  
**Package:** `com.fleet.shared.history`

## Consume from the BMS app (composite build)

```kotlin
includeBuild("../bms-vehicle-history-log")
```

```kotlin
implementation("com.fleet.shared:bms-vehicle-history-log")
```

The caller chooses the sample rate. This library only stores.

## Files

- Open day: `<root>/<vin>/<yyyy-MM-dd>.geojsonl` — one GeoJSON `Feature` per line.
- Closed day: `<root>/<vin>/<yyyy-MM-dd>.geojson` — one GeoJSON `FeatureCollection`.
- A later UTC day for the same VIN closes the older open file. `closeFinishedDays(now)` closes every open file whose UTC day is before `now`.
- `pendingUploads()` lists closed files. `confirmUploaded` deletes a file only when the sha256 matches the bytes on disk.

Missing optional properties are JSON `null`. They are not omitted and not written as `0`. `time` is UTC with a `Z` suffix. Coordinates are `[lon, lat]` or `[lon, lat, alt]`.
