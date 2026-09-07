package com.watermelon.storage.db.migrations

import android.database.sqlite.SQLiteDatabase

/**
 * Shared helpers so every migration step can genuinely satisfy [WatermelonDatabase]'s own
 * stated policy — "ordered, idempotent steps" — including the ones that use
 * `ALTER TABLE ... ADD COLUMN`, which (unlike `CREATE TABLE IF NOT EXISTS`) has no built-in
 * "if not exists" guard in SQLite and throws if the column is already present.
 *
 * Before this helper existed, [MigrationV6ToV7]'s two `ALTER TABLE` calls were genuinely not
 * idempotent (its own doc comment said so explicitly, relying on the version system to
 * guarantee single execution) while [MigrationV9ToV10]'s doc comment claimed to be
 * idempotent — two migrations in the same ladder holding contradictory policies. Every
 * `ALTER TABLE ADD COLUMN` in this ladder now goes through [addColumnIfMissing] so the
 * whole ladder can honestly claim one consistent policy.
 */

/** True if [table] currently has a column named [column]. */
fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean {
    rawQuery("PRAGMA table_info($table)", null).use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        if (nameIndex < 0) return false
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) return true
        }
    }
    return false
}

/**
 * Runs `ALTER TABLE $table ADD COLUMN $columnDefinition` only if the named column doesn't
 * already exist, making an otherwise non-idempotent `ALTER TABLE ADD COLUMN` safe to run
 * more than once against the same database — e.g. if a migration step is re-applied, or a
 * test exercises the ladder twice to prove idempotency (Phase B1's migration-ladder test).
 *
 * [columnName] must match the column name as it appears at the start of [columnDefinition]
 * (e.g. `addColumnIfMissing(db, "MediaItems", "firstSeenAt", "firstSeenAt INTEGER DEFAULT 0")`)
 * — kept as separate parameters rather than parsed out of the SQL fragment, so there's no
 * risk of misparsing a column name that happens to contain a keyword.
 */
fun addColumnIfMissing(
    db: SQLiteDatabase,
    table: String,
    columnName: String,
    columnDefinition: String,
) {
    if (!db.hasColumn(table, columnName)) {
        db.execSQL("ALTER TABLE $table ADD COLUMN $columnDefinition")
    }
}
