package com.infikey.theme

import android.content.Context
import android.graphics.Color
import org.json.JSONObject

/**
 * Theme engine: 10 presets + unlimited custom "theme codes".
 * A theme code is a compact hex string, e.g.
 *   bg=12101A;keys=1C1830;special=241F3D;text=F1EEFF;hint=9C93C9;accent=8B5CF6;accent2=34D399;radius=18
 * Codes can be shared with friends (Settings → Themes → Enter code).
 */
class ThemeEngine(private val context: Context) {

    data class Theme(
        val id: String,
        val name: String,
        val bg: Int,
        val key: Int,
        val keySpecial: Int,
        val text: Int,
        val hint: Int,
        val accent: Int,
        val accent2: Int,
        val radiusDp: Int = 16
    ) {
        fun toCode(): String = listOf(
            "bg=%06X".format(bg and 0xFFFFFF),
            "keys=%06X".format(key and 0xFFFFFF),
            "special=%06X".format(keySpecial and 0xFFFFFF),
            "text=%06X".format(text and 0xFFFFFF),
            "hint=%06X".format(hint and 0xFFFFFF),
            "accent=%06X".format(accent and 0xFFFFFF),
            "accent2=%06X".format(accent2 and 0xFFFFFF),
            "radius=$radiusDp"
        ).joinToString(";")

        fun toJson(): JSONObject = JSONObject()
            .put("bg", bg).put("key", key).put("keySpecial", keySpecial)
            .put("text", text).put("hint", hint)
            .put("accent", accent).put("accent2", accent2).put("radius", radiusDp)
    }

    companion object {
        private fun rgb(hex: Int, dim: Float = 1f) = Color.rgb(
            (Color.red(hex) * dim).toInt(), (Color.green(hex) * dim).toInt(), (Color.blue(hex) * dim).toInt()
        )

        val PRESETS: List<Theme> = listOf(
            Theme(
                "obsidian_aurora", "Obsidian Aurora",
                bg = 0xFF101014.toInt(), key = 0xFF1C1C26.toInt(), keySpecial = 0xFF23232F.toInt(),
                text = 0xFFF4F4FA.toInt(), hint = 0xFF8A8A9A.toInt(),
                accent = 0xFF8B5CF6.toInt(), accent2 = 0xFF34D399.toInt(), radiusDp = 18
            ),
            Theme(
                "paper_white", "Paper White",
                bg = 0xFFF3F1EC.toInt(), key = 0xFFFFFFFF.toInt(), keySpecial = 0xFFE7E3DA.toInt(),
                text = 0xFF20232A.toInt(), hint = 0xFF8B8F98.toInt(),
                accent = 0xFF2563EB.toInt(), accent2 = 0xFFF59E0B.toInt(), radiusDp = 14
            ),
            Theme(
                "mint_ink", "Mint Ink",
                bg = 0xFF0E1512.toInt(), key = 0xFF15211B.toInt(), keySpecial = 0xFF1C2B23.toInt(),
                text = 0xFFE8FFF4.toInt(), hint = 0xFF7FA391.toInt(),
                accent = 0xFF34D399.toInt(), accent2 = 0xFF10B981.toInt(), radiusDp = 20
            ),
            Theme(
                "sakura_glow", "Sakura Glow",
                bg = 0xFF1A1117.toInt(), key = 0xFF2A1B24.toInt(), keySpecial = 0xFF36222E.toInt(),
                text = 0xFFFFF0F5.toInt(), hint = 0xFFB98FA2.toInt(),
                accent = 0xFFF472B6.toInt(), accent2 = 0xFFFB7185.toInt(), radiusDp = 22
            ),
            Theme(
                "deep_ocean", "Deep Ocean",
                bg = 0xFF0A1220.toInt(), key = 0xFF12202F.toInt(), keySpecial = 0xFF182B3D.toInt(),
                text = 0xFFE6F1FF.toInt(), hint = 0xFF7A96B4.toInt(),
                accent = 0xFF38BDF8.toInt(), accent2 = 0xFF6366F1.toInt(), radiusDp = 16
            ),
            Theme(
                "desert_gold", "Desert Gold",
                bg = 0xFF171310.toInt(), key = 0xFF241E18.toInt(), keySpecial = 0xFF302620.toInt(),
                text = 0xFFFFF6E8.toInt(), hint = 0xFFB39B7D.toInt(),
                accent = 0xFFF59E0B.toInt(), accent2 = 0xFFD97706.toInt(), radiusDp = 14
            ),
            Theme(
                "carbon_mono", "Carbon Mono",
                bg = 0xFF0D0D0D.toInt(), key = 0xFF1A1A1A.toInt(), keySpecial = 0xFF242424.toInt(),
                text = 0xFFF2F2F2.toInt(), hint = 0xFF8C8C8C.toInt(),
                accent = 0xFFE5E5E5.toInt(), accent2 = 0xFFAAAAAA.toInt(), radiusDp = 10
            ),
            Theme(
                "indigo_night", "Indigo Night",
                bg = 0xFF0F1123.toInt(), key = 0xFF181B33.toInt(), keySpecial = 0xFF20244a.toInt(),
                text = 0xFFEAEBFF.toInt(), hint = 0xFF8E93C4.toInt(),
                accent = 0xFF6366F1.toInt(), accent2 = 0xFFA855F7.toInt(), radiusDp = 18
            ),
            Theme(
                "bamboo_green", "Bamboo Green",
                bg = 0xFF111711.toInt(), key = 0xFF1B241B.toInt(), keySpecial = 0xFF232F23.toInt(),
                text = 0xFFEFFFEF.toInt(), hint = 0xFF8AAE8A.toInt(),
                accent = 0xFF22C55E.toInt(), accent2 = 0xFF84CC16.toInt(), radiusDp = 16
            ),
            Theme(
                "rose_cream", "Rose Cream",
                bg = 0xFFFBEFF1.toInt(), key = 0xFFFFFFFF.toInt(), keySpecial = 0xFFF7E0E4.toInt(),
                text = 0xFF3D2228.toInt(), hint = 0xFFA98A90.toInt(),
                accent = 0xFFE11D48.toInt(), accent2 = 0xFFFB923C.toInt(), radiusDp = 20
            )
        )

        fun byId(id: String): Theme = PRESETS.firstOrNull { it.id == id } ?: PRESETS.first()

        /** Parses a share code or "bg=..;keys=..;..." into a Theme. */
        fun fromCode(code: String, name: String = "Custom"): Theme? = runCatching {
            val map = code.trim().lowercase().split(";").mapNotNull {
                val kv = it.split("=", limit = 2)
                if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
            }.toMap()
            fun color(k: String, d: Int) = map[k]?.let {
                when (it.length) {
                    6 -> (0xFF000000 or it.toLong(16)).toInt()
                    8 -> it.toLong(16).toInt()
                    else -> d
                }
            } ?: d
            Theme(
                id = "custom_$code".take(40),
                name = name,
                bg = color("bg", 0xFF101014.toInt()),
                key = color("keys", 0xFF1C1C26.toInt()),
                keySpecial = color("special", 0xFF23232F.toInt()),
                text = color("text", 0xFFF4F4FA.toInt()),
                hint = color("hint", 0xFF8A8A9A.toInt()),
                accent = color("accent", 0xFF8B5CF6.toInt()),
                accent2 = color("accent2", 0xFF34D399.toInt()),
                radiusDp = map["radius"]?.toIntOrNull() ?: 16
            )
        }.getOrNull()
    }

    private val sp = context.getSharedPreferences("infikey", Context.MODE_PRIVATE)

    /** Cached theme with the stored values it was built from. */
    @Volatile
    private var cache: Pair<String, Theme>? = null

    fun current(): Theme {
        // other ThemeEngine instances (settings screen) write the same prefs
        val id = sp.getString("theme_id", "obsidian_aurora") ?: "obsidian_aurora"
        val key = if (id.startsWith("custom_")) id + sp.getString("custom_theme_json", "") else id
        cache?.let { if (it.first == key) return it.second }
        val t = if (id.startsWith("custom_")) {
            val stored = sp.getString("custom_theme_json", null)
            stored?.let { runCatching { themeFromJson(JSONObject(it)) }.getOrNull() } ?: byId(id)
        } else byId(id)
        cache = key to t
        return t
    }

    fun apply(id: String) {
        sp.edit().putString("theme_id", id).apply()
        cache = null
    }

    fun saveCustom(t: Theme) {
        sp.edit().putString("theme_id", t.id)
            .putString("custom_theme_json", t.toJson().toString())
            .putStringSet("theme_codes", (sp.getStringSet("theme_codes", emptySet()) ?: emptySet()) + t.toCode())
            .apply()
        cache = null
    }

    fun savedCodes(): Set<String> =
        (sp.getStringSet("theme_codes", emptySet()) ?: emptySet())

    fun addCode(code: String): Boolean {
        val t = fromCode(code) ?: return false
        saveCustom(t)
        return true
    }

    private fun themeFromJson(o: JSONObject) = Theme(
        id = "custom_stored", name = "Custom",
        bg = o.optInt("bg"), key = o.optInt("key"), keySpecial = o.optInt("keySpecial"),
        text = o.optInt("text"), hint = o.optInt("hint"),
        accent = o.optInt("accent"), accent2 = o.optInt("accent2"), radiusDp = o.optInt("radius", 16)
    )
}
