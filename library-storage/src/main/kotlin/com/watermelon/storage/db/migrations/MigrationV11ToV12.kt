package com.watermelon.storage.db.migrations

import android.database.sqlite.SQLiteDatabase

/**
 * V12 adds persisted subtitle synchronization profiles keyed by media identity + subtitle
 * fingerprint. Manual correction is stored independently from automatic correction so manual
 * timing always has higher authority and an Auto Sync engine upgrade never invalidates it.
 */
object MigrationV11ToV12 {
    fun migrate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS SubtitleSyncProfiles (
                mediaId               TEXT NOT NULL,
                mediaFileSize         INTEGER NOT NULL,
                subtitleFingerprint   TEXT NOT NULL,
                subtitleLanguage      TEXT,
                autoModelType         TEXT,
                autoOffsetMs          INTEGER,
                autoScale             REAL,
                autoAnchorsJson       TEXT,
                autoConfidence        REAL,
                autoEngineVersion     INTEGER,
                autoUpdatedAt         INTEGER,
                manualOffsetMs        INTEGER,
                manualUpdatedAt       INTEGER,
                PRIMARY KEY (mediaId, mediaFileSize, subtitleFingerprint)
            );
            """.trimIndent()
        )
    }
}
