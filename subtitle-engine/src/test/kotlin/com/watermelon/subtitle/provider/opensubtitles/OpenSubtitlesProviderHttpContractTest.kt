package com.watermelon.subtitle.provider.opensubtitles

import com.watermelon.subtitle.provider.SubtitleProviderQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for OpenSubtitlesProvider HTTP contract using MockEngine.
 * Verifies correct endpoint, headers, and JSON field mappings without network access.
 */
class OpenSubtitlesProviderHttpContractTest {

    private val testApiKey = "test-api-key"
    private val testUserAgent = "Watermelon v1.0.0"
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `search sends correct request with hash and size`() = runTest {
        var capturedUrl: String? = null
        var capturedApiKey: String? = null
        var capturedUserAgent: String? = null
        var capturedMovieHash: String? = null
        var capturedMovieByteSize: String? = null
        var capturedLanguages: String? = null

        val handler: MockRequestHandler = { request ->
            capturedUrl = request.url.toString()
            capturedApiKey = request.headers["Api-Key"]
            capturedUserAgent = request.headers["User-Agent"]
            capturedMovieHash = request.url.parameters["moviehash"]
            capturedMovieByteSize = request.url.parameters["moviebytesize"]
            capturedLanguages = request.url.parameters["languages"]

            respond(
                content = """{
                    "data": [
                        {
                            "attributes": {
                                "language": "en",
                                "release": "Movie.2024.1080p",
                                "ratings": 8.5,
                                "download_count": 1500,
                                "moviehash_match": true,
                                "files": [
                                    {
                                        "file_id": 12345,
                                        "file_name": "movie.en.srt"
                                    }
                                ]
                            }
                        }
                    ]
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(MockEngine(handler))
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client,
            json = json
        )

        val query = SubtitleProviderQuery(
            movieHash = "abc123hash",
            fileSize = 1234567890,
            displayName = "Movie.2024.mkv",
            preferredLanguages = listOf("en", "fa")
        )

        provider.search(query)

        assertEquals("https://api.opensubtitles.com/api/v1/subtitles", capturedUrl)
        assertEquals(testApiKey, capturedApiKey)
        assertEquals(testUserAgent, capturedUserAgent)
        assertEquals("abc123hash", capturedMovieHash)
        assertEquals("1234567890", capturedMovieByteSize)
        assertEquals("en,fa", capturedLanguages)

        client.close()
    }

    @Test
    fun `search parses nested files array correctly`() = runTest {
        val handler: MockRequestHandler = { request ->
            respond(
                content = """{
                    "data": [
                        {
                            "attributes": {
                                "language": "en",
                                "release": "Movie.Release",
                                "ratings": 9.0,
                                "download_count": 2000,
                                "moviehash_match": true,
                                "files": [
                                    {"file_id": 111, "file_name": "movie.en.srt"},
                                    {"file_id": 222, "file_name": "movie.en.forced.srt"}
                                ]
                            }
                        },
                        {
                            "attributes": {
                                "language": "fa",
                                "release": "Movie.Release",
                                "ratings": 7.5,
                                "download_count": 500,
                                "moviehash_match": false,
                                "files": [
                                    {"file_id": 333, "file_name": "movie.fa.srt"}
                                ]
                            }
                        }
                    ]
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(MockEngine(handler))
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client,
            json = json
        )

        val tracks = provider.search(SubtitleProviderQuery(preferredLanguages = emptyList()))

        // Should flatten all files from all results
        assertEquals(3, tracks.size)
        
        val enTracks = tracks.filter { it.language == "en" }
        assertEquals(2, enTracks.size)
        
        val faTracks = tracks.filter { it.language == "fa" }
        assertEquals(1, faTracks.size)

        client.close()
    }

    @Test
    fun `search drops entries without file_id`() = runTest {
        val handler: MockRequestHandler = { request ->
            respond(
                content = """{
                    "data": [
                        {
                            "attributes": {
                                "language": "en",
                                "files": [
                                    {"file_id": 100, "file_name": "valid.srt"},
                                    {"file_name": "missing_file_id.srt"}
                                ]
                            }
                        }
                    ]
                }""",
                status = HttpStatusCode.OK
            )
        }

        val client = HttpClient(MockEngine(handler))
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client,
            json = json
        )

        val tracks = provider.search(SubtitleProviderQuery())

        // Only the valid entry with file_id should be returned
        assertEquals(1, tracks.size)
        assertEquals(100L, tracks[0].remoteFileId)

        client.close()
    }

    @Test
    fun `search orders by preferred language then hash match then rating`() = runTest {
        val handler: MockRequestHandler = { request ->
            respond(
                content = """{
                    "data": [
                        {
                            "attributes": {
                                "language": "fa",
                                "ratings": 5.0,
                                "download_count": 100,
                                "moviehash_match": false,
                                "files": [{"file_id": 1, "file_name": "fa.srt"}]
                            }
                        },
                        {
                            "attributes": {
                                "language": "en",
                                "ratings": 9.5,
                                "download_count": 5000,
                                "moviehash_match": false,
                                "files": [{"file_id": 2, "file_name": "en-high.srt"}]
                            }
                        },
                        {
                            "attributes": {
                                "language": "en",
                                "ratings": 8.0,
                                "download_count": 3000,
                                "moviehash_match": true,
                                "files": [{"file_id": 3, "file_name": "en-hash.srt"}]
                            }
                        },
                        {
                            "attributes": {
                                "language": "ar",
                                "ratings": 7.0,
                                "download_count": 200,
                                "moviehash_match": true,
                                "files": [{"file_id": 4, "file_name": "ar.srt"}]
                            }
                        }
                    ]
                }""",
                status = HttpStatusCode.OK
            )
        }

        val client = HttpClient(MockEngine(handler))
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client,
            json = json
        )

        val tracks = provider.search(
            SubtitleProviderQuery(preferredLanguages = listOf("fa", "ar", "en"))
        )

        // Expected order: fa first (preferred), then ar (preferred), then en hash-match, then en non-hash
        assertEquals(4, tracks.size)
        assertEquals("fa", tracks[0].language)
        assertEquals("ar", tracks[1].language)
        assertEquals("en", tracks[2].language)
        assertTrue(tracks[2].hashMatched) // hash-matched en comes before non-hash-matched en
        assertEquals("en", tracks[3].language)
        assertFalse(tracks[3].hashMatched)

        client.close()
    }

    @Test
    fun `resolveDownload sends correct POST request with file_id and sub_format`() = runTest {
        var capturedUrl: String? = null
        var capturedBody: String? = null
        var capturedContentType: String? = null

        val handler: MockRequestHandler = { request ->
            capturedUrl = request.url.toString()
            capturedBody = (request.body as? TextContent)?.text
            capturedContentType = request.contentType()?.toString()

            respond(
                content = """{"link": "https://dl.opensubtitles.com/download/12345"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(MockEngine(handler))
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client,
            json = json
        )

        val track = com.watermelon.common.model.SubtitleTrack(
            language = "en",
            label = "English",
            downloadUrl = "",
            rating = 0f,
            providerId = "opensubtitles.com",
            remoteFileId = 12345,
            remoteFileName = "movie.en.srt"
        )

        provider.resolveDownload(track)

        assertEquals("https://api.opensubtitles.com/api/v1/download", capturedUrl)
        assertEquals(testApiKey, request.headers["Api-Key"])
        assertEquals(testUserAgent, request.headers["User-Agent"])
        assertEquals("application/json; charset=UTF-8", capturedContentType)
        assertTrue(capturedBody?.contains("\"file_id\":12345") == true || capturedBody?.contains("\"file_id\": 12345") == true)
        assertTrue(capturedBody?.contains("\"sub_format\":\"srt\"") == true || capturedBody?.contains("\"sub_format\": \"srt\"") == true)

        client.close()
    }

    @Test
    fun `resolveDownload validates HTTPS URL`() = runTest {
        val handler: MockRequestHandler = { request ->
            respond(
                content = """{"link": "http://insecure.example.com/download"}""",
                status = HttpStatusCode.OK
            )
        }

        val client = HttpClient(MockEngine(handler))
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client,
            json = json
        )

        val track = com.watermelon.common.model.SubtitleTrack(
            language = "en",
            label = "English",
            downloadUrl = "",
            rating = 0f,
            providerId = "opensubtitles.com",
            remoteFileId = 12345
        )

        try {
            provider.resolveDownload(track)
            // Should not reach here
            throw AssertionError("Expected IllegalArgumentException for non-HTTPS URL")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("HTTPS") == true)
        }

        client.close()
    }

    @Test
    fun `resolveDownload rejects non-opensubtitles host`() = runTest {
        val handler: MockRequestHandler = { request ->
            respond(
                content = """{"link": "https://malicious.example.com/download"}""",
                status = HttpStatusCode.OK
            )
        }

        val client = HttpClient(MockEngine(handler))
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client,
            json = json
        )

        val track = com.watermelon.common.model.SubtitleTrack(
            language = "en",
            label = "English",
            downloadUrl = "",
            rating = 0f,
            providerId = "opensubtitles.com",
            remoteFileId = 12345
        )

        try {
            provider.resolveDownload(track)
            throw AssertionError("Expected IllegalArgumentException for invalid host")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("not allowed") == true || e.message?.contains("host") == true)
        }

        client.close()
    }

    @Test
    fun `isConfigured returns false when API key is blank`() {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        val provider = OpenSubtitlesProvider(
            apiKey = "",
            userAgent = testUserAgent,
            httpClient = client,
            json = json
        )

        assertFalse(provider.isConfigured)

        client.close()
    }

    @Test
    fun `search returns empty list when provider not configured`() = runTest {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        val provider = OpenSubtitlesProvider(
            apiKey = "",
            userAgent = testUserAgent,
            httpClient = client,
            json = json
        )

        val tracks = provider.search(SubtitleProviderQuery())

        assertEquals(0, tracks.size)

        client.close()
    }
}
