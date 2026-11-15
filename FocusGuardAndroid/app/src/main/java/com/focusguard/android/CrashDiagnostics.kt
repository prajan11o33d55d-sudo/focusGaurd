package com.focusguard.android

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stores crash diagnostics only in this app's private local storage. Nothing is uploaded. */
object CrashDiagnostics {
    private const val FILE_NAME = "focus_guard_last_crash.txt"
    private const val TAG = "FocusGuardCrash"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(appContext, "Uncaught exception on ${thread.name}", error)
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    @Synchronized
    fun record(context: Context, location: String, error: Throwable) {
        try {
            val trace = StringWriter().also { writer -> error.printStackTrace(PrintWriter(writer)) }.toString()
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date())
            val report = buildString {
                appendLine("Focus Guard startup/runtime diagnostic")
                appendLine("Time: $timestamp")
                appendLine("Location: $location")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android API: ${Build.VERSION.SDK_INT}")
                appendLine("Error: ${error.javaClass.name}: ${error.message ?: "(no message)"}")
                appendLine()
                append(trace)
            }
            context.applicationContext.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(report.toByteArray(Charsets.UTF_8))
            }
            Log.e(TAG, report)
        } catch (diagnosticFailure: Throwable) {
            Log.e(TAG, "Could not save local crash diagnostics", diagnosticFailure)
        }
    }

    fun readLast(context: Context): String? = try {
        context.applicationContext.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        null
    }

    fun clear(context: Context) {
        runCatching { context.applicationContext.deleteFile(FILE_NAME) }
    }
}
