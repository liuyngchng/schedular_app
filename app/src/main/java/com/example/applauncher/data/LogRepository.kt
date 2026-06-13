package com.example.applauncher.data

import android.content.Context
import com.example.applauncher.model.ExecutionLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class LogRepository(private val context: Context) {

    private val logFile: File get() = File(context.filesDir, "execution_logs.json")
    private val gson = Gson()
    private val mutex = Mutex()
    private val _logs = MutableStateFlow<List<ExecutionLog>>(emptyList())
    val logs: StateFlow<List<ExecutionLog>> = _logs

    init {
        _logs.value = readLogs()
    }

    suspend fun addLog(log: ExecutionLog) {
        mutex.withLock {
            val current = readLogs().toMutableList()
            current.add(0, log)
            val cutoff = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
            val pruned = current.filter { it.timestamp >= cutoff }
            writeLogs(pruned)
            _logs.value = pruned
        }
    }

    private fun readLogs(): List<ExecutionLog> {
        if (!logFile.exists()) return emptyList()
        return try {
            val json = logFile.readText()
            val type = object : TypeToken<List<ExecutionLog>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeLogs(logs: List<ExecutionLog>) {
        logFile.writeText(gson.toJson(logs))
    }

    suspend fun clearAll() {
        mutex.withLock {
            writeLogs(emptyList())
            _logs.value = emptyList()
        }
    }
}
