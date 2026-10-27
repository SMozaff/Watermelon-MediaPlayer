package com.watermelon.app

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import com.watermelon.common.util.FileLogger
import java.io.File
import java.io.FileWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * File and crash loggers, extracted from MainActivity in Phase 2b.
 *
 * The file logger writes to Documents/watermelon.log when the public Documents
 * directory is writable, falling back to app-private external storage otherwise.
 * The crash handler chains into the previous default handler so multiple
 * installers don't swallow each other's reports.
 */

private val crashLoggerInstalled = AtomicBoolean(false)

internal fun ComponentActivity.installFileLogger() {
    val docsDir = android.os.Environment.getExternalStoragePublicDirectory(
        android.os.Environment.DIRECTORY_DOCUMENTS
    )
    val primary = File(docsDir, "watermelon.log")
    val fallback = File(getExternalFilesDir(null), "watermelon.log")
    val logFile = if (runCatching {
            docsDir.mkdirs()
            FileWriter(primary, true).use { it.append("") }
            true
        }.getOrDefault(false)) primary else fallback
    runCatching { if (logFile.exists()) logFile.delete() }
    val lock = Any()
    FileLogger.install { line ->
        synchronized(lock) {
            runCatching {
                FileWriter(logFile, true).use { it.append(line).append('\n') }
            }
        }
    }
    FileLogger.i("Log", "log file at: ${logFile.absolutePath}")
}

internal fun Context.installCrashLogger() {
    if (!crashLoggerInstalled.compareAndSet(false, true)) return
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOCUMENTS
            )
            val file = File(dir, "watermelon_crash_${System.currentTimeMillis()}.txt")
            file.writeText(
                "Thread: ${thread.name}\n\n" + Log.getStackTraceString(throwable)
            )
        }
        previous?.uncaughtException(thread, throwable)
    }
}
