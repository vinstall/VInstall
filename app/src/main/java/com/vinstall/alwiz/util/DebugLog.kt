package com.vinstall.alwiz.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {

    private const val MAX_ENTRIES = 300
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries

    fun d(tag: String, msg: String) {
        Log.d("VInstall/$tag", msg)
        append("D/$tag: $msg")
    }

    fun e(tag: String, msg: String) {
        Log.e("VInstall/$tag", msg)
        append("E/$tag: $msg")
    }

    fun i(tag: String, msg: String) {
        Log.i("VInstall/$tag", msg)
        append("I/$tag: $msg")
    }

    fun w(tag: String, msg: String) {
        Log.w("VInstall/$tag", msg)
        append("W/$tag: $msg")
    }

    private fun append(line: String) {
        val ts = fmt.format(Date())
        val entry = "[$ts] $line"
        val current = _entries.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_ENTRIES) current.subList(0, current.size - MAX_ENTRIES).clear()
        _entries.value = current
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun getAll(): String = _entries.value.joinToString("\n")
}
