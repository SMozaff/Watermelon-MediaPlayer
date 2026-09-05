package com.watermelon.app.managers

import android.app.Activity
import android.os.Environment
import com.watermelon.common.util.FileLogger
import java.io.File
import java.io.FileWriter

/**
 * Handles file logging and crash logging setup.
 */
class LoggingManager {

    fun installFileLogger(activity: Activity): File {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val primary = File(docsDir, "watermelon.log")
        val fallback = File(activity.getExternalFilesDir(null), "watermelon.log")
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
        return logFile
    }

    fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val file = File(dir, "watermelon_crash_${System.currentTimeMillis()}.txt")
                file.writeText(
                    "Thread: ${thread.name}\n\n" +
                    android.util.Log.getStackTraceString(throwable)
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}