package com.watermelon.subtitle.provider.opensubtitles

import com.watermelon.subtitle.provider.AuthenticationRequiredException
import com.watermelon.subtitle.provider.PermissionDeniedException
import com.watermelon.subtitle.provider.ProviderResponseException
import com.watermelon.subtitle.provider.ProviderUnavailableException
import com.watermelon.subtitle.provider.QuotaExceededException
import com.watermelon.subtitle.provider.SubtitleProviderQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
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
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    /**
     * Test client mirrors the production client's JSON handling: without
     * ContentNegotiation the client cannot serialize request bodies or deserialize
     * response bodies, so every test that performs an HTTP round-trip needs it.
     */
    private fun testClient(handler: MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(json)
            }
            expectSuccess = false
        }
    }

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
                headers = jsonHeaders()
            )
        }

        val client = testClient(handler)
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client
        )

        val query = SubtitleProviderQuery(
            movieHash = "abc123hash",
            fileSize = 1234567890,
            displayName = "Movie.2024.mkv",
            preferredLanguages = listOf("en", "fa")
        )

        provider.search(query)

        // Query parameters are appended to the URL — compare the endpoint path only.
        assertEquals("https://api.opensubtitles.com/api/v1/subtitles", capturedUrl?.substringBefore("?"))
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
                headers = jsonHeaders()
            )
        }

        val client = testClient(handler)
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client
        )

        val tracks = provider.search(
            SubtitleProviderQuery(
                displayName = "Movie.2024.mkv",
                preferredLanguages = emptyList()
            )
        )

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
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }

        val client = testClient(handler)
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client
        )

        val tracks = provider.search(
            SubtitleProviderQuery(
                displayName = "Movie.2024.mkv",
                preferredLanguages = emptyList()
            )
        )

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
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }

        val client = testClient(handler)
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client
        )

        val tracks = provider.search(
            SubtitleProviderQuery(
                displayName = "Movie.2024.mkv",
                preferredLanguages = listOf("fa", "ar", "en")
            )
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
        var capturedApiKey: String? = null
        var capturedUserAgent: String? = null

        val handler: MockRequestHandler = { request ->
            capturedUrl = request.url.toString()
            capturedBody = (request.body as? TextContent)?.text
            capturedContentType = request.body.contentType?.toString()
            capturedApiKey = request.headers["Api-Key"]
            capturedUserAgent = request.headers["User-Agent"]

            respond(
                content = """{"link": "https://dl.opensubtitles.com/download/12345"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }

        val client = testClient(handler)
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client
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
        assertEquals(testApiKey, capturedApiKey)
        assertEquals(testUserAgent, capturedUserAgent)
        assertEquals("application/json", capturedContentType)
        assertTrue(capturedBody?.contains("\"file_id\":12345") == true || capturedBody?.contains("\"file_id\": 12345") == true)
        assertTrue(capturedBody?.contains("\"sub_format\":\"srt\"") == true || capturedBody?.contains("\"sub_format\": \"srt\"") == true)

        client.close()
    }

    @Test
    fun `resolveDownload validates HTTPS URL`() = runTest {
        val handler: MockRequestHandler = { request ->
            respond(
                content = """{"link": "http://insecure.example.com/download"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }

        val client = testClient(handler)
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client
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
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }

        val client = testClient(handler)
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client
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
            httpClient = client
        )

        assertFalse(provider.isConfigured)

        client.close()
    }

    @Test
    fun `search throws when provider not configured`() = runTest {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        val provider = OpenSubtitlesProvider(
            apiKey = "",
            userAgent = testUserAgent,
            httpClient = client
        )

        try {
            provider.search(
                SubtitleProviderQuery(
                    displayName = "Movie.2024.mkv",
                    preferredLanguages = listOf("en")
                )
            )
            throw AssertionError("Expected ProviderUnavailableException when API key is blank")
        } catch (e: ProviderUnavailableException) {
            assertTrue(e.message?.contains("not configured") == true)
        }

        client.close()
    }

    @Test
    fun `isConfigured returns false when user agent is blank`() = runTest {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = "   ",
            httpClient = client
        )

        assertFalse(provider.isConfigured)

        try {
            provider.search(
                SubtitleProviderQuery(
                    displayName = "Movie.2024.mkv",
                    preferredLanguages = listOf("en")
                )
            )
            throw AssertionError("Expected ProviderUnavailableException when user agent is blank")
        } catch (e: ProviderUnavailableException) {
            assertTrue(e.message?.contains("not configured") == true)
        }

        client.close()
    }

    @Test
    fun `search maps 401 to AuthenticationRequiredException`() = runTest {
        val client = testClient { respond("", HttpStatusCode.Unauthorized) }
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client
        )

        try {
            provider.search(
                SubtitleProviderQuery(
                    displayName = "Movie.2024.mkv",
                    preferredLanguages = listOf("en")
                )
            )
            throw AssertionError("Expected AuthenticationRequiredException for 401")
        } catch (e: AuthenticationRequiredException) {
            // expected
        }

        client.close()
    }

    @Test
    fun `search maps 403 to PermissionDeniedException`() = runTest {
        val client = testClient { respond("", HttpStatusCode.Forbidden) }
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client
        )

        try {
            provider.search(
                SubtitleProviderQuery(
                    displayName = "Movie.2024.mkv",
                    preferredLanguages = listOf("en")
                )
            )
            throw AssertionError("Expected PermissionDeniedException for 403")
        } catch (e: PermissionDeniedException) {
            // expected
        }

        client.close()
    }

    @Test
    fun `search maps 429 to QuotaExceededException`() = runTest {
        val client = testClient { respond("", HttpStatusCode.TooManyRequests) }
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client
        )

        try {
            provider.search(
                SubtitleProviderQuery(
                    displayName = "Movie.2024.mkv",
                    preferredLanguages = listOf("en")
                )
            )
            throw AssertionError("Expected QuotaExceededException for 429")
        } catch (e: QuotaExceededException) {
            // expected
        }

        client.close()
    }

    @Test
    fun `search maps 5xx to ProviderUnavailableException`() = runTest {
        val statuses = listOf(
            HttpStatusCode.InternalServerError,
            HttpStatusCode.BadGateway,
            HttpStatusCode.ServiceUnavailable
        )
        for (status in statuses) {
            val client = testClient { respond("", status) }
            val provider = OpenSubtitlesProvider(
                apiKey = testApiKey,
                userAgent = testUserAgent,
                httpClient = client
            )

            try {
                provider.search(
                    SubtitleProviderQuery(
                        displayName = "Movie.2024.mkv",
                        preferredLanguages = listOf("en")
                    )
                )
                throw AssertionError("Expected ProviderUnavailableException for $status")
            } catch (e: ProviderUnavailableException) {
                // expected
            }

            client.close()
        }
    }

    @Test
    fun `search maps other non-2xx to ProviderResponseException`() = runTest {
        val client = testClient { respond("", HttpStatusCode.UnprocessableEntity) }
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client
        )

        try {
            provider.search(
                SubtitleProviderQuery(
                    displayName = "Movie.2024.mkv",
                    preferredLanguages = listOf("en")
                )
            )
            throw AssertionError("Expected ProviderResponseException for 422")
        } catch (e: ProviderResponseException) {
            // expected
        }

        client.close()
    }

    @Test
    fun `search maps malformed successful JSON to typed provider failure`() = runTest {
        val client = testClient { respond("this is not json", HttpStatusCode.OK, jsonHeaders()) }
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client
        )

        try {
            provider.search(
                SubtitleProviderQuery(
                    displayName = "Movie.2024.mkv",
                    preferredLanguages = listOf("en")
                )
            )
            throw AssertionError("Expected ProviderUnavailableException for malformed JSON")
        } catch (e: ProviderUnavailableException) {
            // expected — deserialization failure is wrapped, never leaks raw
        }

        client.close()
    }

    @Test
    fun `resolveDownload rejects response missing link`() = runTest {
        val handler: MockRequestHandler = { request ->
            respond(
                content = """{"link": null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }

        val client = testClient(handler)
        val provider = OpenSubtitlesProvider(
            apiKey = testApiKey,
            userAgent = testUserAgent,
            httpClient = client
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
            throw AssertionError("Expected ProviderResponseException for missing link")
        } catch (e: ProviderResponseException) {
            assertTrue(e.message?.contains("link") == true)
        }

        client.close()
    }
}
