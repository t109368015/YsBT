package com.orango.electronic.orange_og_lib.Util

import android.util.Log
import java.util.*

class YsClock {
    private var startTime: Long = 0
    private var tag = "YsClock"

    init { reset() }

    fun reset() {
        startTime = Date().time
    }

    fun runTimeMs(): Long {
        return try {
            Date().time - startTime
        } catch (e: Exception) {
            Log.d(tag, "runTimeMs Error: ${e.message}")
            0
        }
    }

    fun runTimeS(): Double {
        return try {
            val elapsedTime = (Date().time - startTime) / 1000.0
            String.format("%.1f", elapsedTime).toDoubleOrNull() ?: 0.0
        } catch (e: Exception) {
            Log.d(tag, "runTimeS Error: ${e.message}")
            0.0
        }
    }
}