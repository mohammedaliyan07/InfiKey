package com.infikey.view

import android.content.Context
import android.view.Gravity
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import com.infikey.theme.ThemeEngine

/** Emoji picker: category tabs + grid, commits via the same key callback. */
class EmojiPanel(context: Context) : LinearLayout(context) {

    companion object {
        private val CATEGORIES = linkedMapOf(
            "Smileys" to listOf(
                "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","😉","😊","😇","🥰","😍","🤩",
                "😘","😗","😚","😙","🥲","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔","🤐",
                "😐","😑","😶","😏","😒","🙄","😬","🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕"
            ),
            "Gestures" to listOf(
                "👍","👎","👌","🤌","✌️","🤞","🫰","🤟","🤘","👏","🙌","🫶","🤝","🙏","💪","🦾",
                "👋","🤙","🫡","🙇","🙌","🤱","🫂","✍️","💅","🤳"
            ),
            "Hearts" to listOf(
                "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖",
                "💘","💝","💟","♥️","💌"
            ),
            "Festivals" to listOf(
                "🪔","🎆","🎇","✨","🎉","🎊","🎁","🎈","🍾","🥂","🌙","⭐","🌟","🕉️","☪️","✝️",
                "🛕","⛪","🕌","🐻","🧨","🧧","🎃","🎄"
            ),
            "Nature" to listOf(
                "🌸","🌺","🌻","🌷","🌹","🥀","🍁","🍀","🌿","🌴","🌵","🌊","🔥","❄️","⛅","🌈",
                "☀️","🌕","🌙","⚡","🌧️","🌱","🐝","🦋"
            ),
            "Food" to listOf(
                "🍎","🍌","🍇","🍓","🥭","🍍","🥥","🥝","🍅","🥑","🥦","🌶️","🌽","🥕","🍞","🥐",
                "🧀","🍗","🍔","🍟","🍕","🌮","🍜","🍛","🍚","🥘","🍦","🍰","☕","🍵","🥤","🧉"
            ),
            "Objects" to listOf(
                "📱","💻","⌨️","🖥️","🖨️","🖱️","💾","📷","🎥","📞","🔋","🔌","💡","🔦","🕯️","🧯",
                "📚","✏️","📝","📌","🔑","🔒","🔒","🧸","🎮","🎧","🎸","⚽","🏏","🥇","🎯","🎲"
            ),
            "Kaomoji" to listOf(
                "(◕‿◕)","(｡◕‿◕｡)","ʕ•ᴥ•ʔ","(¬‿¬)","ᕕ( ᐛ )ᕗ","(╯°□°）╯︵ ┻━┻","(•̀ᴗ•́)و",
                "(๑•̀ㅂ•́)و✧","(＾▽＾)","(─‿─)","(♡˙︶˙♡)","(っ˘ڡ˘ς)","(ง'̀-'́)ง","(•_•)","¯\\_(ツ)_/¯",
                "→","←","↑","↓","★","☆","✦","✧","•","◦","‣","※","₹","©","®","™","°","±","×","÷","∞","≈","≠","√","†","‡","§","¶"
            )
        )
    }

    interface Callbacks { fun onEmoji(text: String) }
    var callbacks: Callbacks? = null

    private val theme get() = ThemeEngine(context).current()
    private val d = resources.displayMetrics.density
    private lateinit var grid: GridView
    private val tabs = mutableListOf<TextView>()

    /** Recent tab first (#31) — recents from prefs, then all categories. */
    private fun tabNames(): List<String> {
        val recents = com.infikey.Global.prefs.emojiRecents()
        return (if (recents.isNotEmpty()) listOf("Recent") else emptyList()) + CATEGORIES.keys
    }

    /** One shared glyph probe: entries the device font cannot draw are hidden
     *  instead of showing empty tofu boxes in the grid (reported bug). */
    private val glyphPaint = android.graphics.Paint()

    private fun renderable(items: List<String>): List<String> =
        items.filter { runCatching { glyphPaint.hasGlyph(it) }.getOrDefault(true) }

    private fun itemsFor(index: Int): List<String> {
        val recents = com.infikey.Global.prefs.emojiRecents()
        val offset = if (recents.isNotEmpty()) 1 else 0
        return if (index == 0 && recents.isNotEmpty()) renderable(recents)
        else renderable(CATEGORIES.values.toList()[index - offset])
    }

    init {
        orientation = VERTICAL
        // panels start hidden — the keyboard must open as JUST the keyboard;
        // an emoji/clipboard wall covering the app on first open was reported
        // as "the keyboard issue" (see InfiKeyService.onCreateInputView)
        visibility = GONE
        setBackgroundColor(theme.bg)
        setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())

        val tabRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        tabNames().forEachIndexed { i, name ->
            val tab = TextView(context).apply {
                text = name
                textSize = 11f
                gravity = Gravity.CENTER
                maxLines = 1
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                setPadding((2 * d).toInt(), (8 * d).toInt(), (2 * d).toInt(), (8 * d).toInt())
                setOnClickListener { selectTab(i) }
                setTextColor(if (i == 0) theme.accent else theme.hint)
            }
            tabs.add(tab)
            tabRow.addView(tab)
        }
        addView(tabRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        grid = GridView(context).apply {
            numColumns = 8
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (230 * d).toInt())
        }
        addView(grid)

        selectTab(0)
    }

    private fun selectTab(index: Int) {
        val currentItems = itemsFor(index)
        tabs.forEachIndexed { i, t -> t.setTextColor(if (i == index) theme.accent else theme.hint) }
        grid.adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = currentItems.size
            override fun getItem(position: Int) = currentItems[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val tv = (convertView as? TextView) ?: TextView(context).apply {
                    textSize = 24f
                    gravity = Gravity.CENTER
                }
                tv.text = currentItems[position]
                tv.setOnClickListener { callbacks?.onEmoji(currentItems[position]) }
                return tv
            }
        }
        grid.invalidateViews()
    }
}
