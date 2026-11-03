package com.vinstall.alwiz.settings

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vinstall.alwiz.R

enum class BlockDuration(val labelRes: Int, val millis: Long) {
    MINUTES_15(R.string.block_15_min, 15 * 60_000L),
    HOUR_1(R.string.block_1_hour, 60 * 60_000L),
    HOURS_6(R.string.block_6_hours, 6 * 60 * 60_000L),
    DAY_1(R.string.block_1_day, 24 * 60 * 60_000L),
    DAYS_7(R.string.block_7_days, 7 * 24 * 60 * 60_000L),
    PERMANENT(R.string.block_permanent, BlockedCallers.PERMANENT)
}

object BlockedCallers {

    const val PERMANENT = -1L

    private const val PREFS = "blocked_callers"
    private const val KEY_ENTRIES = "entries"
    private val gson = Gson()

    data class Entry(val packageName: String, val expiresAt: Long) {
        val permanent: Boolean get() = expiresAt == PERMANENT
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context): MutableMap<String, Long> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, Long>>() {}.type
            gson.fromJson<MutableMap<String, Long>>(raw, type) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun save(context: Context, map: Map<String, Long>) {
        prefs(context).edit().putString(KEY_ENTRIES, gson.toJson(map)).apply()
    }

    @Synchronized
    fun all(context: Context): List<Entry> {
        val map = load(context)
        val now = System.currentTimeMillis()
        val removed = map.entries.removeAll { it.value != PERMANENT && it.value <= now }
        if (removed) save(context, map)
        return map.map { Entry(it.key, it.value) }.sortedBy { it.packageName }
    }

    fun isBlocked(context: Context, packageName: String): Boolean =
        all(context).any { it.packageName == packageName }

    fun count(context: Context): Int = all(context).size

    @Synchronized
    fun block(context: Context, packageName: String, durationMs: Long) {
        val map = load(context)
        map[packageName] = if (durationMs == PERMANENT) PERMANENT else System.currentTimeMillis() + durationMs
        save(context, map)
    }

    @Synchronized
    fun unblock(context: Context, packageName: String) {
        val map = load(context)
        if (map.remove(packageName) != null) save(context, map)
    }
}
