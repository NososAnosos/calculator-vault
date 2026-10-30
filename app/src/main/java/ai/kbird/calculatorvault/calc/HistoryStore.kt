package ai.kbird.calculatorvault.calc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists calculator history (the stock app keeps it across restarts; an empty
 * history after every launch would be a tell). Vault PINs never reach history:
 * a bare number is not a calculation, so equals on it records nothing.
 */
class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("calc_history", Context.MODE_PRIVATE)

    fun load(): List<HistoryEntry> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            HistoryEntry(o.getString("f"), o.getString("r"))
        }
    }.getOrDefault(emptyList())

    fun save(entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(JSONObject().put("f", it.formula).put("r", it.result)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "h"
    }
}
