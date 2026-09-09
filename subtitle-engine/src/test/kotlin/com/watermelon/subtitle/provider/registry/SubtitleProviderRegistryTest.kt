package com.watermelon.subtitle.provider.registry

import com.watermelon.common.model.SubtitleTrack
import com.watermelon.subtitle.provider.ProviderResponseException
import com.watermelon.subtitle.provider.SubtitleDownloadLink
import com.watermelon.subtitle.provider.SubtitleProvider
import com.watermelon.subtitle.provider.SubtitleProviderQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SubtitleProviderRegistry] coordination logic.
 * Uses fake providers — no network access.
 */
class SubtitleProviderRegistryTest {

    private fun query() = SubtitleProviderQuery(
        movieHash = "abc123",
        fileSize = 12345L,
        displayName = "Movie.2024.mkv",
        preferredLanguages = listOf("en", "fa")
    )

    private fun track(
        language: String = "en",
        label: String = "English",
        rating: Float = 5f,
        downloadCount: Int = 10,
        hashMatched: Boolean = false,
        providerId: String = "opensubtitles.com",
        remoteFileId: Long = 1L
    ) = SubtitleTrack(
        language = language,
        label = label,
        downloadUrl = "",
        rating = rating,
        providerId = providerId,
        remoteFileId = remoteFileId,
        downloadCount = downloadCount,
        hashMatched = hashMatched
    )

    private class FakeProvider(
        override val id: String,
        override val isConfigured: Boolean = true,
        var searchResult: List<SubtitleTrack> = emptyList(),
        var searchError: Exception? = null,
        var downloadLink: SubtitleDownloadLink =
            SubtitleDownloadLink("https://dl.opensubtitles.com/x", "x.srt"),
        var searchCalls: Int = 0
    ) : SubtitleProvider {
        override suspend fun search(query: SubtitleProviderQuery): List<SubtitleTrack> {
            searchCalls++
            searchError?.let { throw it }
            return searchResult
        }

        override suspend fun resolveDownload(track: SubtitleTrack): SubtitleDownloadLink = downloadLink
    }

    @Test
    fun `configuredProviders filters out unconfigured providers`() {
        val configured = FakeProvider("a", isConfigured = true)
        val unconfigured = FakeProvider("b", isConfigured = false)
        val registry = SubtitleProviderRegistry(listOf(configured, unconfigured))

        assertEquals(listOf(configured), registry.configuredProviders())
    }

    @Test
    fun `search aggregates configured providers and skips unconfigured`() = runTest {
        val first = FakeProvider("a", searchResult = listOf(track(label = "A")))
        val skipped = FakeProvider("b", isConfigured = false, searchResult = listOf(track(label = "B")))
        val second = FakeProvider("c", searchResult = listOf(track(label = "C")))
        val registry = SubtitleProviderRegistry(listOf(first, skipped, second))

        val results = registry.search(query())

        assertEquals(listOf("A", "C"), results.map { it.label })
        assertEquals(0, skipped.searchCalls)
    }

    @Test
    fun `search orders by provider order then hash match then rating`() = runTest {
        val first = FakeProvider(
            "a",
            searchResult = listOf(
                track(label = "a-plain", rating = 9f, hashMatched = false),
                track(label = "a-hash", rating = 1f, hashMatched = true)
            )
        )
        val second = FakeProvider("b", searchResult = listOf(track(label = "b-best", rating = 10f)))
        val registry = SubtitleProviderRegistry(listOf(first, second))

        val results = registry.search(query())

        // Provider order wins over rating; within a provider hash-match wins over rating.
        assertEquals(listOf("a-hash", "a-plain", "b-best"), results.map { it.label })
    }

    @Test
    fun `search propagates provider exceptions unchanged`() = runTest {
        val failing = FakeProvider("a", searchError = ProviderResponseException("bad gateway"))
        val registry = SubtitleProviderRegistry(listOf(failing))

        try {
            registry.search(query())
            throw AssertionError("Expected ProviderResponseException")
        } catch (e: ProviderResponseException) {
            assertEquals("bad gateway", e.message)
        }
    }

    @Test
    fun `search wraps unexpected exceptions`() = runTest {
        val failing = FakeProvider("a", searchError = IllegalStateException("boom"))
        val registry = SubtitleProviderRegistry(listOf(failing))

        try {
            registry.search(query())
            throw AssertionError("Expected ProviderResponseException")
        } catch (e: ProviderResponseException) {
            assertTrue(e.message?.contains("boom") == true)
        }
    }

    @Test
    fun `providerFor finds provider by id`() {
        val provider = FakeProvider("opensubtitles.com")
        val registry = SubtitleProviderRegistry(listOf(provider))

        assertSame(provider, registry.providerFor("opensubtitles.com"))
        assertNull(registry.providerFor("unknown"))
    }

    @Test
    fun `resolveDownload delegates to matching provider`() = runTest {
        val link = SubtitleDownloadLink("https://dl.opensubtitles.com/1", "movie.en.srt")
        val provider = FakeProvider("opensubtitles.com", downloadLink = link)
        val registry = SubtitleProviderRegistry(listOf(provider))

        assertSame(link, registry.resolveDownload(track(providerId = "opensubtitles.com")))
    }

    @Test
    fun `resolveDownload rejects track without providerId`() = runTest {
        val registry = SubtitleProviderRegistry(listOf(FakeProvider("opensubtitles.com")))

        try {
            registry.resolveDownload(track(providerId = "opensubtitles.com").copy(providerId = null))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("providerId") == true)
        }
    }

    @Test
    fun `resolveDownload rejects unknown provider id`() = runTest {
        val registry = SubtitleProviderRegistry(listOf(FakeProvider("opensubtitles.com")))

        try {
            registry.resolveDownload(track(providerId = "nope"))
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("nope") == true)
        }
    }

    @Test
    fun `resolveDownload rejects unconfigured provider`() = runTest {
        val registry = SubtitleProviderRegistry(listOf(FakeProvider("opensubtitles.com", isConfigured = false)))

        try {
            registry.resolveDownload(track(providerId = "opensubtitles.com"))
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("not configured") == true)
        }
    }
}
