package com.watermelon.storage.db.migrations

import android.database.sqlite.SQLiteDatabase

/**
 * V9 adds:
 *   - MediaItems.dateAdded column (epoch-ms from MediaStore DATE_ADDED) for Date sort
 *     and Recently Added. fileExtension is derived at read-time from displayName, so no
 *     column is needed for it.
 *   - CustomOrder table for manual drag-reorder within playlists and Favourites.
 *
 * CustomOrder.containerId is the playlist id (or Favourites system id). Recently Added
 * is intentionally excluded — it always sorts by dateAdded descending.
 */
object MigrationV8ToV9 {
    fun migrate(db: SQLiteDatabase) {
        // dateAdded column — default 0 for existing rows; Phase2Extractor backfills on next
        // index. Guarded explicitly (not runCatching) so a genuine, unrelated SQL error here
        // surfaces instead of being silently swallowed alongside the expected
        // already-exists case.
        addColumnIfMissing(db, "MediaItems", "dateAdded", "dateAdded INTEGER NOT NULL DEFAULT 0")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS CustomOrder (
                containerId TEXT NOT NULL,
                uri         TEXT NOT NULL,
                position    INTEGER NOT NULL,
                PRIMARY KEY (containerId, uri)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_customorder_container
            ON CustomOrder (containerId, position)
        """.trimIndent())
    }
}
