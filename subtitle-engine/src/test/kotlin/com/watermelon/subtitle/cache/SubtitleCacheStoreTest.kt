package com.watermelon.subtitle.cache

import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for [SubtitleCacheStore] file bookkeeping.
 * Covers write/list/isCached/clear — all pure-JVM paths that never touch Android APIs.
 */
class SubtitleCacheStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var store: SubtitleCacheStore

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

    private fun track(
        language: String = "en",
        // NOTE: intentionally dot-free. SubtitleCacheStore separates filename fields
        // with '.', so a real-world providerId such as opensubtitles.com does not
        // round-trip exactly through list() (pre-existing store limitation, which has
        // no live callers today — findSubtitles is interface surface for future use).
        providerId: String = "testprovider",
        remoteFileId: Long = 100L
    ) = SubtitleTrack(
        language = language,
        label = "$language track",
        downloadUrl = "",
        rating = 0f,
        providerId = providerId,
        remoteFileId = remoteFileId
    )

    private val srtBytes = """
        1
        00:00:01,000 --> 00:00:02,000
        Hello
    """.trimIndent().toByteArray(Charsets.UTF_8)

    @Before
    fun setUp() {
        store = SubtitleCacheStore(tempFolder.newFolder("subtitles"))
    }

    @Test
    fun `list is empty before anything is cached`() {
        assertTrue(store.list(mediaItem, listOf("en")).isEmpty())
        assertFalse(store.isCached(mediaItem, track(), "testprovider"))
    }

    @Test
    fun `write then list returns the cached track`() {
        val written = store.write(mediaItem, track(), "testprovider", srtBytes)

        assertTrue(written.isFile)
        assertTrue(store.isCached(mediaItem, track(), "testprovider"))

        val tracks = store.list(mediaItem, listOf("en"))
        assertEquals(1, tracks.size)
        assertEquals("en", tracks[0].language)
        assertEquals("testprovider", tracks[0].providerId)
        assertEquals(100L, tracks[0].remoteFileId)
        assertTrue(tracks[0].downloadUrl.startsWith("file:"))
    }

    @Test
    fun `list filters by preferred languages`() {
        store.write(mediaItem, track(language = "en"), "testprovider", srtBytes)
        store.write(mediaItem, track(language = "fa", remoteFileId = 101L), "testprovider", srtBytes)

        assertEquals(1, store.list(mediaItem, listOf("fa")).size)
        assertEquals("fa", store.list(mediaItem, listOf("fa"))[0].language)
        assertEquals(2, store.list(mediaItem, emptyList()).size)
    }

    @Test
    fun `list orders by language preference`() {
        store.write(mediaItem, track(language = "en"), "testprovider", srtBytes)
        store.write(mediaItem, track(language = "fa", remoteFileId = 101L), "testprovider", srtBytes)

        val tracks = store.list(mediaItem, listOf("fa", "en"))
        assertEquals(listOf("fa", "en"), tracks.map { it.language })
    }

    @Test
    fun `write is scoped to the owning media item`() {
        val other = mediaItem.copy(uri = "file:///storage/Movies/Other.mkv")
        store.write(mediaItem, track(), "testprovider", srtBytes)

        assertTrue(store.list(other, listOf("en")).isEmpty())
        assertEquals(1, store.list(mediaItem, listOf("en")).size)
    }

    @Test
    fun `write rejects oversized payloads`() {
        val oversized = ByteArray(5 * 1024 * 1024 + 1)

        try {
            store.write(mediaItem, track(), "testprovider", oversized)
            throw AssertionError("Expected IllegalArgumentException for oversized payload")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("too large") == true)
        }
        assertTrue(store.list(mediaItem, listOf("en")).isEmpty())
    }

    @Test
    fun `clear removes all cached files`() {
        store.write(mediaItem, track(), "testprovider", srtBytes)
        assertEquals(1, store.list(mediaItem, listOf("en")).size)

        store.clear()

        assertTrue(store.list(mediaItem, listOf("en")).isEmpty())
    }
}
