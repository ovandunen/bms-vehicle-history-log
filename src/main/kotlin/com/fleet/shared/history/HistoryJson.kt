package com.fleet.shared.history

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
val HistoryJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
    prettyPrint = false
}

object TripSummaryCodec {
    fun encode(summary: TripSummary): String = HistoryJson.encodeToString(TripSummary.serializer(), summary)

    fun decode(text: String): Result<TripSummary> = runCatching {
        val summary = HistoryJson.decodeFromString(TripSummary.serializer(), text)
        if (summary.schema != 1) {
            error("unsupported schema ${summary.schema} (expected 1)")
        }
        summary
    }
}
