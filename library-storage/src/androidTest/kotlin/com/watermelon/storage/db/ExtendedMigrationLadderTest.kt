package com.watermelon.storage.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watermelon.storage.db.migrations.MigrationV10ToV11
import com.watermelon.storage.db.migrations.MigrationV11ToV12
import com.watermelon.storage.db.migrations.MigrationV1ToV2
import com.watermelon.storage.db.migrations.MigrationV2ToV3
import com.watermelon.storage.db.migrations.MigrationV3ToV4
import com.watermelon.storage.db.migrations.MigrationV4ToV5
import com.watermelon.storage.db.migrations.MigrationV5ToV6
import com.watermelon.storage.db.migrations.MigrationV6ToV7
import com.watermelon.storage.db.migrations.MigrationV7ToV8
import com.watermelon.storage.db.migrations.MigrationV8ToV9
import com.watermelon.storage.db.migrations.MigrationV9ToV10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Extended migration-ladder coverage, V1 → V12 (Phase B1). [MigrationTest] (the original,
 * still-authoritative CI-gated class) only ever exercised V1 → V6; every step from V7
 * onward — including [MigrationV9ToV10]'s rename/migrate/drop fix for a real, previously
 * silent playlist-data-loss bug (see that migration's own doc comment) — had zero test
 * coverage despite DATABASE_VERSION already being 12.
 *
 * This class is deliberately separate from [MigrationTest] rather than an addition to it,
 * so the original CI-gated class (and whatever external tooling references its exact name
 * per its own "Handover §4" doc comment) is left untouched; this class should be added
 * alongside it as an equally-required instrumented test, not a replacement.
 */
@RunWith(AndroidJUnit4::class)
class ExtendedMigrationLadderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val fullLadder = listOf(
        MigrationV1ToV2::migrate, MigrationV2ToV3::migrate, MigrationV3ToV4::migrate,
        MigrationV4ToV5::migrate, MigrationV5ToV6::migrate, MigrationV6ToV7::migrate,
        MigrationV7ToV8::migrate, MigrationV8ToV9::migrate, MigrationV9ToV10::migrate,
        MigrationV10ToV11::migrate, MigrationV11ToV12::migrate,
    )

    private fun openFixture(name: String): SQLiteDatabase {
        val dbFile = File(context.cacheDir, name).apply { delete() }
        return SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    }

    private fun closeFixture(db: SQLiteDatabase, name: String) {
        db.close()
        File(context.cacheDir, name).delete()
    }

    // ── Baseline builders — mirror MigrationTest's own createV1Baseline pattern for the ──
    // ── V1 starting point, then apply real prior steps to reach later starting versions ──

    private fun createV1Baseline(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE MediaItems (
                mediaId TEXT PRIMARY KEY, fileSize INTEGER NOT NULL, displayName TEXT NOT NULL,
                parentFolder TEXT NOT NULL, durationMs INTEGER, width INTEGER, height INTEGER,
                mimeType TEXT);"""
        )
        db.execSQL(
            """CREATE TABLE Folders (
                folderPath TEXT PRIMARY KEY, displayName TEXT NOT NULL,
                itemCount INTEGER DEFAULT 0, lastScannedAt INTEGER);"""
        )
        db.execSQL(
            """CREATE TABLE PlaybackPositions (
                mediaId TEXT NOT NULL, fileSize INTEGER NOT NULL, positionMs INTEGER NOT NULL,
                updatedAt INTEGER, PRIMARY KEY (mediaId, fileSize));"""
        )
    }

    /** Applies real migration steps up to (but not including) [uptoExclusiveIndex] of
     *  [fullLadder], so later-version baselines are built from the ladder's own real
     *  behaviour rather than a hand-written approximation of what V5/V6/V10/V11 "should"
     *  look like. */
    private fun createBaselineAt(db: SQLiteDatabase, uptoExclusiveIndex: Int) {
        createV1Baseline(db)
        for (i in 0 until uptoExclusiveIndex) fullLadder[i](db)
    }

    private fun assertTableExists(db: SQLiteDatabase, table: String) {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)
        ).use { c -> assertTrue("Missing table $table", c.moveToFirst()) }
    }

    private fun assertColumnExists(db: SQLiteDatabase, table: String, column: String) {
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIdx) == column) { found = true; break }
            }
            assertTrue("Missing column $table.$column", found)
        }
    }

    /** Full post-V12 schema assertions shared by every "reached the end" test below. */
    private fun assertFinalSchema(db: SQLiteDatabase) {
        // V7: MediaItems badge columns.
        assertColumnExists(db, "MediaItems", "firstSeenAt")
        assertColumnExists(db, "MediaItems", "lastPlayedAt")
        // V8: playlist support tables (in their V9→V10-corrected shape).
        assertTableExists(db, "Playlists")
        assertTableExists(db, "PlaylistItems")
        assertTableExists(db, "Favourites")
        assertColumnExists(db, "Playlists", "id")
        assertColumnExists(db, "Playlists", "type")
        assertColumnExists(db, "PlaylistItems", "uri")
        // V9: sort/reorder support.
        assertColumnExists(db, "MediaItems", "dateAdded")
        assertTableExists(db, "CustomOrder")
        // V10: the corrected playlist schema must be in place, with no leftover renamed
        // tables from the fix-up path.
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                "('Playlists_old_v9','PlaylistItems_old_v9')",
            null
        ).use { c -> assertFalse("stale renamed V9 tables were left behind", c.moveToFirst()) }
        // V11: modified-date sort support.
        assertColumnExists(db, "MediaItems", "dateModified")
        // V12: subtitle sync profiles.
        assertTableExists(db, "SubtitleSyncProfiles")
        assertColumnExists(db, "SubtitleSyncProfiles", "manualOffsetMs")
        assertColumnExists(db, "SubtitleSyncProfiles", "autoOffsetMs")
        // Never-drop guarantee (Manifest §10.1), still true at the end of the full ladder.
        assertTableExists(db, "PlaybackPositions")
        assertTableExists(db, "SubtitleOffsets")
    }

    // ── Path 1: fresh V1 → V12, each step applied twice (idempotency across the WHOLE ────
    // ── ladder, not just the V1-V6 prefix the original MigrationTest covers) ─────────────

    @Test
    fun freshV1ToV12_eachStepTwice_reachesCorrectFinalSchema() {
        val name = "ladder_v1_to_v12.db"
        val db = openFixture(name)
        try {
            createV1Baseline(db)
            for (step in fullLadder) { step(db); step(db) }
            assertFinalSchema(db)
        } finally {
            closeFixture(db, name)
        }
    }

    // ── Path 2: the specific data-preservation case MigrationV9ToV10's own doc comment ───
    // ── describes — old-schema playlist rows must survive the rename/migrate/drop fix ────

    @Test
    fun v9ToV10_preservesExistingPlaylistDataThroughSchemaFix() {
        val name = "ladder_v9_playlist_preservation.db"
        val db = openFixture(name)
        try {
            // Build up to (but not including) the V9→V10 step using the real ladder, so the
            // starting point is exactly what a real upgrading install would have — namely,
            // the OLD (playlistId-keyed) Playlists/PlaylistItems schema from V1ToV2/V2ToV3,
            // per MigrationV9ToV10's own doc comment: V7ToV8's "CREATE TABLE IF NOT EXISTS"
            // silently no-op'd against it, so it was never actually replaced by V8.
            createBaselineAt(db, uptoExclusiveIndex = 8) // through MigrationV8ToV9 inclusive

            db.execSQL(
                "INSERT INTO Playlists (playlistId, name, createdAt, updatedAt) VALUES " +
                    "('pl-1', 'Road Trip', 1700000000000, 1700000000000)"
            )
            db.execSQL(
                "INSERT INTO PlaylistItems (playlistId, mediaId, sortOrder) VALUES " +
                    "('pl-1', 'content://v/1', 0), ('pl-1', 'content://v/2', 1)"
            )

            MigrationV9ToV10.migrate(db)

            // The playlist itself survived the schema fix, now under the corrected column
            // names (id, not playlistId).
            db.rawQuery(
                "SELECT name, type FROM Playlists WHERE id = ?", arrayOf("pl-1")
            ).use { c ->
                assertTrue("playlist 'pl-1' was lost during the V9->V10 schema fix", c.moveToFirst())
                assertEquals("Road Trip", c.getString(0))
                assertEquals(
                    "old-schema rows have no type column to preserve, so they must be " +
                        "mapped to USER per MigrationV9ToV10's documented best-effort mapping",
                    "USER", c.getString(1)
                )
            }

            // Membership rows survived too, under the corrected column name (uri, not
            // mediaId), and both original member URIs are present.
            val members = mutableListOf<String>()
            db.rawQuery(
                "SELECT uri FROM PlaylistItems WHERE playlistId = ?", arrayOf("pl-1")
            ).use { c -> while (c.moveToNext()) members += c.getString(0) }
            assertEquals(
                "playlist membership was lost or duplicated during the V9->V10 schema fix",
                listOf("content://v/1", "content://v/2"), members.sorted()
            )

            // Idempotency: running the same step again against the now-corrected schema
            // must be a safe no-op, not a re-triggered rename/migrate/drop.
            MigrationV9ToV10.migrate(db)
            db.rawQuery(
                "SELECT COUNT(*) FROM PlaylistItems WHERE playlistId = ?", arrayOf("pl-1")
            ).use { c -> c.moveToFirst(); assertEquals(2, c.getInt(0)) }
        } finally {
            closeFixture(db, name)
        }
    }

    // ── Paths 3-6: the specific intermediate starting points real upgrading installs ─────
    // ── will actually hit — not just the two ends of the ladder ──────────────────────────

    @Test
    fun v5Baseline_toV12_reachesCorrectFinalSchema() {
        val name = "ladder_v5_to_v12.db"
        val db = openFixture(name)
        try {
            createBaselineAt(db, uptoExclusiveIndex = 4) // through MigrationV4ToV5 inclusive
            for (i in 4 until fullLadder.size) fullLadder[i](db)
            assertFinalSchema(db)
        } finally {
            closeFixture(db, name)
        }
    }

    @Test
    fun v6Baseline_toV12_reachesCorrectFinalSchema() {
        val name = "ladder_v6_to_v12.db"
        val db = openFixture(name)
        try {
            createBaselineAt(db, uptoExclusiveIndex = 5) // through MigrationV5ToV6 inclusive
            for (i in 5 until fullLadder.size) fullLadder[i](db)
            assertFinalSchema(db)
        } finally {
            closeFixture(db, name)
        }
    }

    @Test
    fun v10Baseline_toV12_reachesCorrectFinalSchema() {
        val name = "ladder_v10_to_v12.db"
        val db = openFixture(name)
        try {
            createBaselineAt(db, uptoExclusiveIndex = 9) // through MigrationV9ToV10 inclusive
            for (i in 9 until fullLadder.size) fullLadder[i](db)
            assertFinalSchema(db)
        } finally {
            closeFixture(db, name)
        }
    }

    @Test
    fun v11Baseline_toV12_reachesCorrectFinalSchema() {
        val name = "ladder_v11_to_v12.db"
        val db = openFixture(name)
        try {
            createBaselineAt(db, uptoExclusiveIndex = 10) // through MigrationV10ToV11 inclusive
            fullLadder[10](db) // MigrationV11ToV12
            assertFinalSchema(db)
        } finally {
            closeFixture(db, name)
        }
    }

    // ── Path 7: a database already at V12 must tolerate the "no-op" upgrade path ─────────
    // ── (WatermelonDatabase.runMigrations(from=12, to=12) applies zero steps) ────────────

    @Test
    fun v12Baseline_reRunningLastStep_isNoOpAndPreservesData() {
        val name = "ladder_v12_noop.db"
        val db = openFixture(name)
        try {
            createBaselineAt(db, uptoExclusiveIndex = fullLadder.size) // full ladder once
            db.execSQL(
                "INSERT INTO SubtitleSyncProfiles " +
                    "(mediaId, mediaFileSize, subtitleFingerprint, manualOffsetMs) VALUES " +
                    "('content://v/9', 512, 'fp-1', 250)"
            )

            // Re-running the last step against an already-V12 database (WatermelonDatabase
            // itself would never call this — runMigrations(12, 12)'s range is empty — but a
            // defensive re-application, e.g. from a future refactor, must not destroy data).
            MigrationV11ToV12.migrate(db)

            db.rawQuery(
                "SELECT manualOffsetMs FROM SubtitleSyncProfiles WHERE mediaId = ?",
                arrayOf("content://v/9")
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(250L, c.getLong(0))
            }
        } finally {
            closeFixture(db, name)
        }
    }
}
