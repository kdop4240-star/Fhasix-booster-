package com.example.gamebooster

import android.app.ActivityManager
import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit

object SystemStats {

    private var lastIdle = 0L
    private var lastTotal = 0L

    /** CPU usage in percent, measured between calls. Call it about once per second. */
    fun cpuPercent(): Int {
        return try {
            val line = File("/proc/stat").useLines { it.first() }
            val parts = line.trim().split("\\s+".toRegex()).drop(1).map { it.toLong() }
            val idle = parts[3] + parts[4]          // idle + iowait
            val total = parts.sum()
            val dIdle = idle - lastIdle
            val dTotal = total - lastTotal
            lastIdle = idle
            lastTotal = total
            if (dTotal <= 0) 0 else (100 * (dTotal - dIdle) / dTotal).toInt().coerceIn(0, 100)
        } catch (e: Exception) {
            -1
        }
    }

    /** RAM in use, in percent. */
    fun ramPercent(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.totalMem <= 0) return -1
        return (100 * (info.totalMem - info.availMem) / info.totalMem).toInt()
    }

    /** CPU temperature in Celsius, or null if this device does not expose it. */
    fun tempC(): Float? {
        val zones = File("/sys/class/thermal").listFiles { f ->
            f.name.startsWith("thermal_zone")
        } ?: return null

        for (zone in zones) {
            val type = File(zone, "type").takeIf { it.canRead() }?.readText()?.trim() ?: continue
            if (type.contains("cpu", ignoreCase = true) || type.contains("tsens", ignoreCase = true)) {
                val raw = File(zone, "temp").takeIf { it.canRead() }?.readText()?.trim()?.toFloatOrNull()
                    ?: continue
                // Most devices report millidegrees (e.g. 42000 = 42.0 C)
                return if (raw > 1000f) raw / 1000f else raw
            }
        }
        return null
    }

    /** Ping in milliseconds. Blocking call: run it on a background thread. */
    fun pingMs(host: String = "8.8.8.8"): Int? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", host))
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroy()
                return null
            }
            Regex("time=([0-9.]+)").find(output)?.groupValues?.get(1)?.toFloat()?.toInt()
        } catch (e: Exception) {
            null
        }
    }
}
