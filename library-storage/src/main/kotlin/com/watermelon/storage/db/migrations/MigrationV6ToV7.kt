package com.watermelon.storage.db.migrations

import android.database.sqlite.SQLiteDatabase

/**
 * v6 → v7: add [firstSeenAt] and [lastPlayedAt] columns to MediaItems to power the
 * ⭐ new-file badge.
 *
 * [firstSeenAt] — epoch-ms when the URI was first indexed (written once on INSERT, never
 *                 overwritten). DEFAULT 0 so pre-migration rows are treated as "old".
 * [lastPlayedAt] — epoch-ms when playback last started. NULL means never played → ⭐ shown.
 *
 * Idempotent: both columns are added via [addColumnIfMissing], so re-running this step
 * against a database that already has them (e.g. a repeated-ladder test, or any future
 * scenario where a migration step runs more than once) is a safe no-op rather than an
 * `SQLiteException: duplicate column name` crash.
 */
object MigrationV6ToV7 {
    fun migrate(db: SQLiteDatabase) {
        addColumnIfMissing(db, "MediaItems", "firstSeenAt", "firstSeenAt INTEGER DEFAULT 0")
        addColumnIfMissing(db, "MediaItems", "lastPlayedAt", "lastPlayedAt INTEGER")
    }
}
