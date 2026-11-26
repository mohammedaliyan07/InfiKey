package com.infikey.prefs

import android.content.Context
import org.json.JSONObject

/** Per-app memory: remembers keyboard language + AI tone for each app. */
class PerAppMemory(context: Context) {

    data class Memory(val layoutId: String, val tone: String)

    private val prefs = context.getSharedPreferences("infikey", Context.MODE_PRIVATE)

    private fun map(): MutableMap<String, Memory> {
        val o = JSONObject(prefs.getString("per_app_memory", "{}") ?: "{}")
        val m = mutableMapOf<String, Memory>()
        o.keys().asSequence().forEach { pkg ->
            val e = o.getJSONObject(pkg)
            m[pkg] = Memory(e.optString("layout", "latin_qwerty"), e.optString("tone", ""))
        }
        return m
    }

    private fun save(m: Map<String, Memory>) {
        val o = JSONObject()
        m.forEach { (pkg, mem) ->
            o.put(pkg, JSONObject().put("layout", mem.layoutId).put("tone", mem.tone))
        }
        prefs.edit().putString("per_app_memory", o.toString()).apply()
    }

    fun get(pkg: String): Memory? = map()[pkg]

    fun remember(pkg: String, layoutId: String) {
        val m = map()
        val cur = m[pkg] ?: Memory(layoutId, "")
        m[pkg] = cur.copy(layoutId = layoutId)
        save(m)
    }

    fun rememberTone(pkg: String, tone: String) {
        val m = map()
        val cur = m[pkg] ?: Memory("latin_qwerty", tone)
        m[pkg] = cur.copy(tone = tone)
        save(m)
    }

    fun clear(pkg: String) {
        save(map().filterKeys { it != pkg })
    }
}
