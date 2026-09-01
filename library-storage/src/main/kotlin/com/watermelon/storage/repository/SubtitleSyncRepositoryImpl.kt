package com.watermelon.storage.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.watermelon.common.subtitle.sync.SubtitleSyncModel
import com.watermelon.common.subtitle.sync.SubtitleSyncProfile
import com.watermelon.common.subtitle.sync.SubtitleSyncRepository
import com.watermelon.storage.db.WatermelonDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SubtitleSyncRepositoryImpl(
    private val db: WatermelonDatabase,
) : SubtitleSyncRepository {

    override suspend fun get(
        mediaId: String,
        mediaFileSize: Long,
        subtitleFingerprint: String,
    ): SubtitleSyncProfile? = withContext(Dispatchers.IO) {
        runCatching {
            db.readableDatabase.rawQuery(
                """
                SELECT subtitleLanguage,
                       autoModelType, autoOffsetMs, autoScale, autoAnchorsJson,
                       autoConfidence, autoEngineVersion, autoUpdatedAt,
                       manualOffsetMs, manualUpdatedAt
                FROM SubtitleSyncProfiles
                WHERE mediaId = ? AND mediaFileSize = ? AND subtitleFingerprint = ?
                """.trimIndent(),
                arrayOf(mediaId, mediaFileSize.toString(), subtitleFingerprint),
            ).use { c ->
                if (!c.moveToFirst()) return@use null

                val modelType = c.getNullableString(1)
                val autoOffset = c.getNullableLong(2)
                val autoScale = c.getNullableDouble(3)
                val autoModel = when (modelType) {
                    MODEL_IDENTITY -> SubtitleSyncModel.Identity
                    MODEL_OFFSET -> autoOffset?.let(SubtitleSyncModel::Offset)
                    MODEL_AFFINE -> if (autoOffset != null && autoScale != null) {
                        SubtitleSyncModel.Affine(autoScale, autoOffset)
                    } else null
                    // V1 does not persist/apply piecewise anchors. Leave future rows intact but
                    // do not pretend to understand them until the parser is implemented.
                    MODEL_PIECEWISE -> null
                    else -> null
                }

                SubtitleSyncProfile(
                    mediaId = mediaId,
                    mediaFileSize = mediaFileSize,
                    subtitleFingerprint = subtitleFingerprint,
                    subtitleLanguage = c.getNullableString(0),
                    autoModel = autoModel,
                    autoConfidence = c.getNullableFloat(5),
                    autoEngineVersion = c.getNullableInt(6),
                    autoUpdatedAt = c.getNullableLong(7),
                    manualOffsetMs = c.getNullableLong(8),
                    manualUpdatedAt = c.getNullableLong(9),
                )
            }
        }.getOrNull()
    }

    override suspend fun saveAuto(
        mediaId: String,
        mediaFileSize: Long,
        subtitleFingerprint: String,
        subtitleLanguage: String?,
        model: SubtitleSyncModel,
        confidence: Float,
        engineVersion: Int,
    ) = withContext(Dispatchers.IO) {
        val writable = db.writableDatabase
        writable.beginTransaction()
        try {
            ensureRow(writable, mediaId, mediaFileSize, subtitleFingerprint, subtitleLanguage)
            val values = ContentValues().apply {
                put("subtitleLanguage", subtitleLanguage)
                put("autoConfidence", confidence.coerceIn(0f, 1f))
                put("autoEngineVersion", engineVersion)
                put("autoUpdatedAt", System.currentTimeMillis())
                when (model) {
                    SubtitleSyncModel.Identity -> {
                        put("autoModelType", MODEL_IDENTITY)
                        put("autoOffsetMs", 0L)
                        putNull("autoScale")
                        putNull("autoAnchorsJson")
                    }
                    is SubtitleSyncModel.Offset -> {
                        put("autoModelType", MODEL_OFFSET)
                        put("autoOffsetMs", model.offsetMs)
                        putNull("autoScale")
                        putNull("autoAnchorsJson")
                    }
                    is SubtitleSyncModel.Affine -> {
                        put("autoModelType", MODEL_AFFINE)
                        put("autoOffsetMs", model.offsetMs)
                        put("autoScale", model.scale)
                        putNull("autoAnchorsJson")
                    }
                    is SubtitleSyncModel.Piecewise -> {
                        // The V1 automatic engine deliberately detects-but-does-not-apply complex
                        // drift, so this should not be written by V1. Fail closed if a caller tries.
                        error("Piecewise subtitle sync persistence is not supported in engine v1")
                    }
                }
            }
            writable.update(
                TABLE,
                values,
                KEY_WHERE,
                arrayOf(mediaId, mediaFileSize.toString(), subtitleFingerprint),
            )
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    override suspend fun setManualOffset(
        mediaId: String,
        mediaFileSize: Long,
        subtitleFingerprint: String,
        offsetMs: Long,
    ) = withContext(Dispatchers.IO) {
        val writable = db.writableDatabase
        writable.beginTransaction()
        try {
            ensureRow(writable, mediaId, mediaFileSize, subtitleFingerprint, null)
            writable.update(
                TABLE,
                ContentValues().apply {
                    put("manualOffsetMs", offsetMs)
                    put("manualUpdatedAt", System.currentTimeMillis())
                },
                KEY_WHERE,
                arrayOf(mediaId, mediaFileSize.toString(), subtitleFingerprint),
            )
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }
    }

    override suspend fun clearManualOffset(
        mediaId: String,
        mediaFileSize: Long,
        subtitleFingerprint: String,
    ) = withContext(Dispatchers.IO) {
        db.writableDatabase.update(
            TABLE,
            ContentValues().apply {
                putNull("manualOffsetMs")
                putNull("manualUpdatedAt")
            },
            KEY_WHERE,
            arrayOf(mediaId, mediaFileSize.toString(), subtitleFingerprint),
        )
        Unit
    }

    override suspend fun clearAutoResult(
        mediaId: String,
        mediaFileSize: Long,
        subtitleFingerprint: String,
    ) = withContext(Dispatchers.IO) {
        db.writableDatabase.update(
            TABLE,
            ContentValues().apply {
                putNull("autoModelType")
                putNull("autoOffsetMs")
                putNull("autoScale")
                putNull("autoAnchorsJson")
                putNull("autoConfidence")
                putNull("autoEngineVersion")
                putNull("autoUpdatedAt")
            },
            KEY_WHERE,
            arrayOf(mediaId, mediaFileSize.toString(), subtitleFingerprint),
        )
        Unit
    }

    private fun ensureRow(
        writable: SQLiteDatabase,
        mediaId: String,
        mediaFileSize: Long,
        subtitleFingerprint: String,
        subtitleLanguage: String?,
    ) {
        writable.insertWithOnConflict(
            TABLE,
            null,
            ContentValues().apply {
                put("mediaId", mediaId)
                put("mediaFileSize", mediaFileSize)
                put("subtitleFingerprint", subtitleFingerprint)
                put("subtitleLanguage", subtitleLanguage)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    private fun android.database.Cursor.getNullableString(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun android.database.Cursor.getNullableLong(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    private fun android.database.Cursor.getNullableInt(index: Int): Int? =
        if (isNull(index)) null else getInt(index)

    private fun android.database.Cursor.getNullableFloat(index: Int): Float? =
        if (isNull(index)) null else getFloat(index)

    private fun android.database.Cursor.getNullableDouble(index: Int): Double? =
        if (isNull(index)) null else getDouble(index)

    companion object {
        private const val TABLE = "SubtitleSyncProfiles"
        private const val KEY_WHERE =
            "mediaId = ? AND mediaFileSize = ? AND subtitleFingerprint = ?"
        private const val MODEL_IDENTITY = "IDENTITY"
        private const val MODEL_OFFSET = "OFFSET"
        private const val MODEL_AFFINE = "AFFINE"
        private const val MODEL_PIECEWISE = "PIECEWISE"
    }
}
