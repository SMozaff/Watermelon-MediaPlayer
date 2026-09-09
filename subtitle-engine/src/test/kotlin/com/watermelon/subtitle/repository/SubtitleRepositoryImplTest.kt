package com.watermelon.subtitle.repository

import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleCue
import com.watermelon.common.model.SubtitleTrack
import com.watermelon.common.model.VideoQuery
import com.watermelon.common.repository.OnlineSubtitleSearchResult
import com.watermelon.subtitle.cache.SubtitleCacheStore
import com.watermelon.subtitle.provider.AuthenticationRequiredException
import com.watermelon.subtitle.provider.PermissionDeniedException
import com.watermelon.subtitle.provider.ProviderException
import com.watermelon.subtitle.provider.ProviderResponseException
import com.watermelon.subtitle.provider.ProviderUnavailableException
import com.watermelon.subtitle.provider.QuotaExceededException
import com.watermelon.subtitle.provider.SubtitleDownloadLink
import com.watermelon.subtitle.provider.SubtitleProvider
import com.watermelon.subtitle.provider.SubtitleProviderQuery
import com.watermelon.subtitle.provider.registry.SubtitleProviderRegistry
import com.watermelon.subtitle.source.SidecarSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondRedirect
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pure-JVM tests for [SubtitleRepositoryImpl] offline-first contract and
 * online-search/download error mapping. No Android APIs, no network.
 */
class SubtitleRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mediaItem = MediaItem(
        uri = "file:///storage/Movies/Movie.2024.mkv",
        fileSize = 1234567890L,
        displayName = "Movie.2024.mkv",
        parentFolder = "/storage/Movies",
        durationMs = 7_200_000L,
        width = 1920,
        height = 1080,
        mimeType = "video/x-matroska"
    )

    private val validSrt = """
        1
        00:00:01,000 --> 00:00:02,000
        Hello

        2
        00:00:03,000 --> 00:00:04,000
        World
    """.trimIndent()

    private val validSrtBytes = validSrt.toByteArray(Charsets.UTF_8)

    private fun track(
        providerId: String? = "opensubtitles.com",
        remoteFileId: Long? = 12345L
    ) = SubtitleTrack(
        language = "en",
        label = "English",
        downloadUrl = "",
        rating = 0f,
        providerId = providerId,
        remoteFileId = remoteFileId
    )

    private class FakeProvider(
        override val id: String = "opensubtitles.com",
        var configured: Boolean = true,
        var searchResult: List<SubtitleTrack> = emptyList(),
        var searchError: ProviderException? = null,
        var downloadUrl: String = "https://opensubtitles.com/dl/12345.srt",
        val searchCalls: MutableList<SubtitleProviderQuery> = mutableListOf(),
        val resolveCalls: MutableList<SubtitleTrack> = mutableListOf(),
    ) : SubtitleProvider {
        override val isConfigured: Boolean get() = configured

        override suspend fun search(query: SubtitleProviderQuery): List<SubtitleTrack> {
            searchCalls += query
            searchError?.let { throw it }
            return searchResult
        }

        override suspend fun resolveDownload(track: SubtitleTrack): SubtitleDownloadLink {
            resolveCalls += track
            return SubtitleDownloadLink(url = downloadUrl, fileName = "track.srt")
        }
    }

    private class FakeSidecar(var result: ParsedSubtitle? = null) : SidecarSource {
        val calls = mutableListOf<VideoQuery>()
        override suspend fun findAndParse(query: VideoQuery): ParsedSubtitle? {
            calls += query
            return result
        }
    }

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        cacheDir = tempFolder.newFolder("subtitles")
    }

    private fun repo(
        providers: List<SubtitleProvider>,
        sidecar: FakeSidecar = FakeSidecar(),
        downloadHandler: MockRequestHandler = { respond(validSrtBytes) },
        network: Boolean = true,
        hash: String = "abc123hash",
    ): SubtitleRepositoryImpl {
        val client = HttpClient(MockEngine(downloadHandler)) { expectSuccess = false }
        return SubtitleRepositoryImpl(
            providerRegistry = SubtitleProviderRegistry(providers),
            sidecarSource = sidecar,
            downloadClient = client,
            cacheStore = SubtitleCacheStore(cacheDir),
            isNetworkAvailable = { network },
            hashFor = { hash },
        )
    }

    // ── findSubtitles: cache-only ─────────────────────────────────────────────

    @Test
    fun `findSubtitles returns empty and makes zero provider calls`() = runTest {
        val provider = FakeProvider()
        val repository = repo(listOf(provider))

        val tracks = repository.findSubtitles(mediaItem, listOf("en"))

        assertTrue(tracks.isEmpty())
        assertTrue(provider.searchCalls.isEmpty())
    }

    @Test
    fun `findSubtitles serves cached tracks without provider calls`() = runTest {
        val provider = FakeProvider()
        val repository = repo(listOf(provider))

        repository.downloadSubtitle(mediaItem, track())
        val tracks = repository.findSubtitles(mediaItem, listOf("en"))

        assertEquals(1, tracks.size)
        assertEquals("opensubtitles.com", tracks[0].providerId)
        assertEquals(12345L, tracks[0].remoteFileId)
        assertTrue(provider.searchCalls.isEmpty())
    }

    // ── parsedFor: offline only ───────────────────────────────────────────────

    @Test
    fun `parsedFor returns null when sidecar and cache are empty`() = runTest {
        val provider = FakeProvider()
        val sidecar = FakeSidecar()
        val repository = repo(listOf(provider), sidecar = sidecar)

        assertNull(repository.parsedFor(mediaItem, listOf("en")))
        assertEquals(1, sidecar.calls.size)
        assertTrue(provider.searchCalls.isEmpty())
    }

    @Test
    fun `parsedFor returns sidecar hit directly`() = runTest {
        val expected = ParsedSubtitle(
            cues = listOf(SubtitleCue(1, 1000L, 2000L, "hi")),
            language = "en"
        )
        val provider = FakeProvider()
        val repository = repo(listOf(provider), sidecar = FakeSidecar(expected))

        assertEquals(expected, repository.parsedFor(mediaItem, listOf("en")))
        assertTrue(provider.searchCalls.isEmpty())
    }

    // ── searchOnlineSubtitles mapping ─────────────────────────────────────────

    @Test
    fun `searchOnline with no configured providers`() = runTest {
        val repository = repo(listOf(FakeProvider(configured = false)))

        assertEquals(
            OnlineSubtitleSearchResult.ProviderNotConfigured,
            repository.searchOnlineSubtitles(mediaItem, listOf("en"))
        )
    }

    @Test
    fun `searchOnline when offline`() = runTest {
        val provider = FakeProvider()
        val repository = repo(listOf(provider), network = false)

        assertEquals(
            OnlineSubtitleSearchResult.Offline,
            repository.searchOnlineSubtitles(mediaItem, listOf("en"))
        )
        assertTrue(provider.searchCalls.isEmpty())
    }

    @Test
    fun `searchOnline maps provider exceptions`() = runTest {
        val cases = listOf(
            AuthenticationRequiredException() to OnlineSubtitleSearchResult.AuthenticationRequired,
            PermissionDeniedException() to OnlineSubtitleSearchResult.PermissionDenied,
            QuotaExceededException() to OnlineSubtitleSearchResult.QuotaExceeded,
        )
        for ((error, expected) in cases) {
            val repository = repo(listOf(FakeProvider(searchError = error)))
            assertEquals(
                "error $error should map to $expected",
                expected,
                repository.searchOnlineSubtitles(mediaItem, listOf("en"))
            )
        }

        val unavailable = repo(listOf(FakeProvider(searchError = ProviderUnavailableException("down"))))
        assertTrue(unavailable.searchOnlineSubtitles(mediaItem, listOf("en")) is OnlineSubtitleSearchResult.Failure)

        val badResponse = repo(listOf(FakeProvider(searchError = ProviderResponseException("bad"))))
        assertTrue(badResponse.searchOnlineSubtitles(mediaItem, listOf("en")) is OnlineSubtitleSearchResult.Failure)
    }

    @Test
    fun `searchOnline success and empty`() = runTest {
        val found = repo(listOf(FakeProvider(searchResult = listOf(track()))))
        val success = found.searchOnlineSubtitles(mediaItem, listOf("en"))
        assertTrue(success is OnlineSubtitleSearchResult.Success)
        assertEquals(1, (success as OnlineSubtitleSearchResult.Success).tracks.size)

        val empty = repo(listOf(FakeProvider(searchResult = emptyList())))
        assertEquals(
            OnlineSubtitleSearchResult.NoResults,
            empty.searchOnlineSubtitles(mediaItem, listOf("en"))
        )
    }

    // ── downloadSubtitle ──────────────────────────────────────────────────────

    @Test
    fun `download resolves through registry first and activates valid SRT`() = runTest {
        var requestedUrl: String? = null
        val provider = FakeProvider()
        val repository = repo(
            listOf(provider),
            downloadHandler = { request ->
                requestedUrl = request.url.toString()
                respond(validSrtBytes)
            }
        )

        val downloaded = repository.downloadSubtitle(mediaItem, track())

        assertEquals(listOf(track()), provider.resolveCalls)
        assertEquals(provider.downloadUrl, requestedUrl)
        assertEquals(2, downloaded.subtitle.cues.size)
        assertTrue(File(downloaded.localPath).isFile)
        assertEquals(1, repository.findSubtitles(mediaItem, listOf("en")).size)
    }

    @Test
    fun `download rejects non-success status`() = runTest {
        val repository = repo(
            listOf(FakeProvider()),
            downloadHandler = { respond("", HttpStatusCode.NotFound) }
        )

        try {
            repository.downloadSubtitle(mediaItem, track())
            throw AssertionError("Expected RuntimeException for HTTP 404")
        } catch (e: RuntimeException) {
            assertTrue(e.message?.contains("404") == true)
        }
    }

    @Test
    fun `oversized Content-Length is rejected`() {
        // MockEngine normalizes response headers from the body, so a lying
        // Content-Length cannot be staged over HTTP — exercise the exact gate
        // downloadSubtitle applies to the declared header value directly.
        val repository = repo(listOf(FakeProvider()))

        try {
            repository.checkContentLength(6L * 1024 * 1024)
            throw AssertionError("Expected RuntimeException for oversized Content-Length")
        } catch (e: RuntimeException) {
            assertTrue(e.message?.contains("too large") == true)
        }

        // Null (absent header) and in-limit values pass without throwing.
        repository.checkContentLength(null)
        repository.checkContentLength(1024L)
        repository.checkContentLength((5L * 1024 * 1024))
    }

    @Test
    fun `download rejects oversized body`() = runTest {
        val repository = repo(
            listOf(FakeProvider()),
            downloadHandler = { respond(ByteArray(5 * 1024 * 1024 + 1)) }
        )

        try {
            repository.downloadSubtitle(mediaItem, track())
            throw AssertionError("Expected RuntimeException for oversized body")
        } catch (e: RuntimeException) {
            assertTrue(e.message?.contains("too large") == true)
        }
    }

    @Test
    fun `download rejects untrusted resolve URL`() = runTest {
        val provider = FakeProvider(downloadUrl = "https://evil.example.com/x.srt")
        val repository = repo(listOf(provider))

        try {
            repository.downloadSubtitle(mediaItem, track())
            throw AssertionError("Expected IllegalArgumentException for untrusted URL")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("untrusted") == true)
        }
        assertEquals(1, provider.resolveCalls.size)
    }

    @Test
    fun `download rejects final redirect leaving opensubtitles`() = runTest {
        val provider = FakeProvider(downloadUrl = "https://opensubtitles.com/dl/12345.srt")
        val repository = repo(
            listOf(provider),
            downloadHandler = { request ->
                if (request.url.host == "opensubtitles.com") {
                    respondRedirect("https://evil.example.com/gotcha.srt")
                } else {
                    respond(validSrtBytes)
                }
            }
        )

        try {
            repository.downloadSubtitle(mediaItem, track())
            throw AssertionError("Expected IllegalArgumentException for redirected URL")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("redirect") == true)
        }
        // resolveDownload still ran first — the redirect check cannot be skipped.
        assertEquals(1, provider.resolveCalls.size)
    }

    @Test
    fun `download with invalid SRT leaves nothing cached`() = runTest {
        val repository = repo(
            listOf(FakeProvider()),
            downloadHandler = { respond("no cues here at all".toByteArray()) }
        )

        try {
            repository.downloadSubtitle(mediaItem, track())
            throw AssertionError("Expected IllegalArgumentException for cue-less SRT")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("no valid cues") == true)
        }
        assertTrue(repository.findSubtitles(mediaItem, listOf("en")).isEmpty())
    }

    // ── cache reopen semantics ────────────────────────────────────────────────

    @Test
    fun `same media reopens cached subtitle without network`() = runTest {
        val seeder = repo(listOf(FakeProvider()))
        seeder.downloadSubtitle(mediaItem, track())

        // Hostile environment: no providers, no network, exploding HTTP client.
        val offline = SubtitleRepositoryImpl(
            providerRegistry = SubtitleProviderRegistry(emptyList()),
            sidecarSource = FakeSidecar(),
            downloadClient = HttpClient(MockEngine {
                throw AssertionError("network must not be touched on reopen")
            }),
            cacheStore = SubtitleCacheStore(cacheDir),
            isNetworkAvailable = { false },
            hashFor = { throw AssertionError("hash must not be touched on reopen") },
        )

        val parsed = offline.parsedFor(mediaItem, listOf("en"))
        assertEquals(2, parsed?.cues?.size)
    }

    @Test
    fun `different media cannot see another items cache`() = runTest {
        val seeder = repo(listOf(FakeProvider()))
        seeder.downloadSubtitle(mediaItem, track())

        val other = mediaItem.copy(uri = "file:///storage/Movies/Other.mkv")
        val offline = SubtitleRepositoryImpl(
            providerRegistry = SubtitleProviderRegistry(emptyList()),
            sidecarSource = FakeSidecar(),
            downloadClient = HttpClient(MockEngine {
                throw AssertionError("network must not be touched")
            }),
            cacheStore = SubtitleCacheStore(cacheDir),
            isNetworkAvailable = { false },
            hashFor = { throw AssertionError("hash must not be touched") },
        )

        assertNull(offline.parsedFor(other, listOf("en")))
    }
}
