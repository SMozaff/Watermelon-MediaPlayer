package com.watermelon.subtitle.network

import com.watermelon.common.model.SubtitleTrack
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Ktor client for the OpenSubtitles.com REST API (api.opensubtitles.com).
 * Network is only ever touched after an explicit user action (Privacy §14).
 *
 * This client targets ONLY the modern OpenSubtitles.com v1 API.
 * Legacy opensubtitles.org mirrors are NOT supported.
 */
class SubtitleApiClient(
    private val httpClient: HttpClient = HttpClient(Android),
    private val responseParser: (String) -> List<SubtitleTrack> = ::parseTracks
) {
    /**
     * Query subtitles for a given OpenSubtitles file [hash], filtered to [preferredLanguages].
     * Fails closed on timeout or server error.
     */
    suspend fun query(
        hash: String,
        fileSize: Long,
        preferredLanguages: List<String>,
        apiKey: String,
        userAgent: String
    ): List<SubtitleTrack> {
        return runCatching {
            withTimeout(REQUEST_TIMEOUT_MS) {
                val response: HttpResponse = httpClient.get("$BASE_URL/subtitles") {
                    header("Api-Key", apiKey)
                    header("User-Agent", userAgent)
                    header("Accept", "application/json")
                    parameter("moviehash", hash)
                    if (fileSize > 0) {
                        parameter("moviebytesize", fileSize.toString())
                    }
                    if (preferredLanguages.isNotEmpty()) {
                        parameter("languages", preferredLanguages.joinToString(","))
                    }
                }
                if (response.status.isSuccess()) {
                    responseParser(response.bodyAsText())
                        .filterByLanguages(preferredLanguages)
                } else {
                    emptyList()
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun List<SubtitleTrack>.filterByLanguages(prefs: List<String>): List<SubtitleTrack> {
        if (prefs.isEmpty()) return this
        return filter { it.language in prefs }
            .sortedWith(compareBy({ prefs.indexOf(it.language) }, { -it.rating }))
    }

    fun close() = httpClient.close()

    companion object {
        private const val REQUEST_TIMEOUT_MS = 8_000L
        private const val BASE_URL = "https://api.opensubtitles.com/api/v1"

        /**
         * Parses OpenSubtitles.com v1 search response into [SubtitleTrack]s.
         *
         * Response shape:
         * { "data": [ { "attributes": { "language": "...", "files": [{ "file_id": ..., "file_name": ... }], ... } } ] }
         *
         * Entries missing language or file_id are dropped.
         */
        fun parseTracks(body: String): List<SubtitleTrack> {
            val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
            val entries: List<JsonObject> = when {
                root is JsonObject && root["data"] is JsonArray ->
                    (root.jsonObject["data"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                else -> emptyList()
            }
            return entries.flatMap { it.toSubtitleTracks() }
        }

        private fun JsonObject.toSubtitleTracks(): List<SubtitleTrack> {
            val attrs = (this["attributes"] as? JsonObject) ?: return emptyList()
            val language = attrs.stringOrNull("language") ?: return emptyList()
            val release = attrs.stringOrNull("release")
            val ratings = attrs.floatOrNull("ratings") ?: 0f
            val downloadCount = attrs.intOrNull("downloadCount") ?: 0
            val hashMatched = attrs.booleanOrNull("moviehashMatch") == true
            
            // Parse files array
            val files = (attrs["files"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            
            return files.mapNotNull { file ->
                val fileId = file.longOrNull("fileId") ?: return@mapNotNull null
                val fileName = file.stringOrNull("fileName")
                
                SubtitleTrack(
                    language = language,
                    label = release ?: fileName ?: "$language subtitle",
                    downloadUrl = "", // Empty until resolved via POST /download
                    rating = ratings,
                    providerId = "opensubtitles.com",
                    remoteFileId = fileId,
                    remoteFileName = fileName,
                    downloadCount = downloadCount,
                    hashMatched = hashMatched
                )
            }
        }

        private fun JsonObject.stringOrNull(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull
        
        private fun JsonObject.floatOrNull(key: String): Float? =
            (this[key] as? JsonPrimitive)?.floatOrNull
        
        private fun JsonObject.intOrNull(key: String): Int? =
            (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
        
        private fun JsonObject.longOrNull(key: String): Long? =
            (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
        
        private fun JsonObject.booleanOrNull(key: String): Boolean? =
            (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
    }
}
