package com.infikey.view

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import com.infikey.prefs.Prefs
import com.infikey.theme.ThemeEngine
import kotlin.math.abs

/**
 * The InfiKey keyboard surface: neumorphic rounded keys drawn on a Canvas,
 * long-press popups, glide tracking, swipe gestures, background image with
 * dim/blur, one-handed mode and elder-mode scaling. No XML involved.
 */
class KeyboardView(context: Context) : View(context) {

    companion object { const val TAG = "KeyboardView" }

    /** Layouts whose glyphs need the system Noto Indic faces (#40). */
    private val INDIC_IDS = setOf(
        "devanagari", "bengali", "tamil", "telugu", "kannada", "malayalam",
        "gujarati", "gurmukhi", "odia", "urdu", "ol_chiki"
    )

    interface Callbacks {
        fun onKey(output: String, action: com.infikey.layout.KeyAction)
        fun onGlideStart(x: Float, y: Float)
        fun onGlideMove(x: Float, y: Float)
        fun onGlideEnd(cancelled: Boolean)
        fun onHideRequest()
        /** Shape gesture detected: "caret" or "circle" (#74). */
        fun onShape(shape: String)
    }

    var callbacks: Callbacks? = null

    private val prefs = Prefs(context)
    private val themes = ThemeEngine(context)
    private val theme get() = themes.current()

    // layout geometry
    private var layout: com.infikey.layout.Layout? = null
    private val keyRects = mutableListOf<Pair<RectF, com.infikey.layout.Key>>()
    private var keyH = 0f
    private var gap = 0f
    private var contentLeft = 0f
    private var contentRight = 0f
    private var shiftOn = false
    private var capsLocked = false
    private var oneHandedShift = 0f

    // paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keySpecialFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var rippleKey: com.infikey.layout.Key? = null
    private var rippleAlpha = 0f

    // touch state
    private var activeKey: com.infikey.layout.Key? = null
    private var downTime = 0L
    private var longPressFired = false
    private var glideMode = false
    private var lastTouchAt = 0L

    // glide trail (#3) — visible stroke that follows the finger
    private val trailPoints = mutableListOf<Pair<Float, Float>>()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // shape gestures (#74) — sampled touch path, classified on lift
    private val shapePoints = mutableListOf<Pair<Float, Float>>()

    // spacebar trackpad (#13) — continuous slide moves the caret
    private var trackpadLastX = Float.NaN
    private var trackpadAccum = 0f

    // swipe-to-delete (#23) — slide left on backspace wipes word after word
    private var backspaceLastX = Float.NaN
    private var backspaceAccum = 0f

    private val powerSave: Boolean
        get() = (context.getSystemService(android.os.PowerManager::class.java))?.isPowerSaveMode == true
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (e1 == null) return false
            val dy = e2.y - e1.y
            val dx = e2.x - e1.x
            return when {
                // swipe down anywhere → hide keyboard
                dy > 120 && abs(dy) > abs(dx) * 1.6f -> { callbacks?.onHideRequest(); true }
                // swipe up on space → switch language (#24)
                dy < -120 && abs(dy) > abs(dx) * 1.2f && spaceUnder(e1) -> {
                    callbacks?.onKey("globe", com.infikey.layout.KeyAction.GLOBE); true
                }
                // horizontal swipes on space / delete handled via active key at fling start
                else -> horizontalGesture(e1, dx)
            }
        }

        private fun spaceUnder(e1: MotionEvent): Boolean =
            prefs.feature("swipe_space_lang") &&
                keyRects.firstOrNull { it.first.contains(e1.x, e1.y) }?.second?.action ==
                com.infikey.layout.KeyAction.SPACE

        private fun horizontalGesture(e1: MotionEvent, dx: Float): Boolean {
            val k = keyRects.firstOrNull { it.first.contains(e1.x, e1.y) }?.second
            return when {
                // cursor drag on spacebar (#12): slide left/right moves the caret
                k?.action == com.infikey.layout.KeyAction.SPACE && dx < -60 &&
                    prefs.feature("cursor_drag") -> { gestureCursor(-1); true }
                k?.action == com.infikey.layout.KeyAction.SPACE && dx > 60 &&
                    prefs.feature("cursor_drag") -> { gestureCursor(1); true }
                k?.action == com.infikey.layout.KeyAction.BACKSPACE && dx < -60 -> {
                    // delete-word (#18) unless continuous swipe-to-delete (#23) owns the gesture
                    if (!prefs.feature("swipe_delete") && prefs.feature("delete_word"))
                        callbacks?.onKey("delete_word", com.infikey.layout.KeyAction.NONE)
                    true
                }
                else -> false
            }
        }

        private fun gestureCursor(dir: Int) {
            callbacks?.onKey(if (dir < 0) "cursor_left" else "cursor_right", com.infikey.layout.KeyAction.NONE)
        }
    })

    private val longPressRunnable = Runnable { showPopup(activeKey) }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // needed for blur shadows
        contentDescription = "InfiKey keyboard" // TalkBack announcement
        textPaint.textAlign = Paint.Align.CENTER
        hintTextPaint.textAlign = Paint.Align.CENTER
        hintTextPaint.textSize = resources.displayMetrics.density * 9f
        ripplePaint.style = Paint.Style.FILL
        refreshTypeface()
    }

    fun refreshTypeface() {
        val tf: Typeface? = runCatching {
            com.infikey.Global.fonts.typeface(prefs.fontId)
        }.getOrNull()
        // Indic font pack (#40): the system Noto faces shape every Indic script
        // correctly — prefer them over a user font that may miss the glyphs
        val id = layout?.id
        val chosen = if (prefs.feature("indic_fonts") && id != null && id in INDIC_IDS)
            Typeface.create("sans-serif", Typeface.NORMAL) else tf
        textPaint.typeface = chosen
        hintTextPaint.typeface = chosen
        invalidate()
    }

    fun setLayout(l: com.infikey.layout.Layout) {
        // number row toggle (#1): prepend digits to any letter layout
        layout = if (prefs.numberRow && l.id != "numpad" && !l.id.startsWith("_symbols"))
            withNumberRow(l) else l
        requestLayout()
        invalidate()
    }

    private fun withNumberRow(l: com.infikey.layout.Layout): com.infikey.layout.Layout {
        if (l.main.firstOrNull()?.keys?.firstOrNull()?.label == "1") return l
        val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            .map { com.infikey.layout.Key(it, width = 1f) }
        return l.copy(main = listOf(com.infikey.layout.row(*digits.toTypedArray())) + l.main)
    }

    fun setCapsState(shift: Boolean, locked: Boolean) {
        shiftOn = shift
        capsLocked = locked
        invalidate()
    }

    fun setOneHanded(mode: String) {
        oneHandedShift = when (mode) {
            "left" -> resources.displayMetrics.widthPixels * -0.06f
            "right" -> resources.displayMetrics.widthPixels * 0.06f
            else -> 0f
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val d = resources.displayMetrics.density
        val rows = layout?.main?.size ?: 4
        val rowH = prefs.keyHeightDp * d * (if (prefs.elderMode) 1.18f else 1f)
        val h = (rows * rowH + (rows + 1) * gap + d * 8).toInt()
        setMeasuredDimension(w, h.coerceAtMost((d * 420).toInt()))
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        computeKeyRects(w, h)
    }

    private fun computeKeyRects(w: Int, h: Int) {
        keyRects.clear()
        val l = layout ?: return
        val d = resources.displayMetrics.density
        gap = d * 4f
        keyH = (h - (l.main.size + 1) * gap - d * 8) / l.main.size
        val margin = d * 3f
        contentLeft = margin + oneHandedShift
        contentRight = w - margin + oneHandedShift
        val usable = contentRight - contentLeft
        var y = gap + d * 4
        for (row in l.main) {
            val totalWeight = row.keys.sumOf { it.width.toDouble() }.toFloat()
            var x = contentLeft
            for (key in row.keys) {
                val kw = usable * (key.width / totalWeight) - gap
                keyRects.add(RectF(x, y, x + kw, y + keyH) to key)
                x += kw + gap
            }
            y += keyH + gap
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val radius = theme.radiusDp * d

        // keyboard background
        bgPaint.color = theme.bg
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawBackgroundImage(canvas, radius)

        for ((rect, key) in keyRects) {
            val special = key.isSpecial
            val fill = if (special) keySpecialFill else keyFill
            val base = if (special) theme.keySpecial else theme.key
            fill.color = base

            // soft neumorphic shadow (#71) — togglable flat rendering
            if (prefs.feature("neumorphic")) {
                shadowPaint.color = base
                shadowPaint.maskFilter = BlurMaskFilter(d * 3f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawRoundRect(rect, radius, radius, shadowPaint)
            }
            canvas.drawRoundRect(rect, radius, radius, fill)

            if (key.action == com.infikey.layout.KeyAction.SHIFT && (shiftOn || capsLocked)) {
                accentPaint.color = if (capsLocked) theme.accent else theme.accent2
                canvas.drawRoundRect(rect, radius, radius, accentPaint)
            }
            if (key == rippleKey && rippleAlpha > 0) {
                // press animation styles (#58): glow / ripple / bounce / retro
                when (prefs.pressAnim) {
                    "glow", "bounce" -> {
                        ripplePaint.color = withAlpha(theme.accent, rippleAlpha)
                        ripplePaint.maskFilter = BlurMaskFilter(d * 9f, BlurMaskFilter.Blur.NORMAL)
                    }
                    "retro" -> {
                        ripplePaint.color = withAlpha(theme.accent2, 0.85f)
                        ripplePaint.maskFilter = null
                    }
                    else -> {
                        ripplePaint.color = withAlpha(theme.accent, rippleAlpha)
                        ripplePaint.maskFilter = null
                    }
                }
                canvas.drawRoundRect(rect, radius, radius, ripplePaint)
            }

            // label
            textPaint.color = theme.text
            val scale = if (prefs.elderMode) 1.15f else 1f
            textPaint.textSize = (if (special) d * 14f else d * 21f) * scale
            val label = displayLabel(key)
            val cx = rect.centerX()
            val cy = rect.centerY() - (textPaint.textSize - textPaint.ascent() - textPaint.descent()) / 2
            canvas.drawText(label, cx, cy, textPaint)
            if (prefs.feature("long_press_popups") && key.popups.isNotEmpty() && !special) {
                hintTextPaint.color = if (prefs.feature("accessibility")) theme.text else theme.hint
                canvas.drawText(key.popups.first(), rect.right - d * 8, rect.top + d * 12, hintTextPaint)
            }
        }

        drawGlideTrail(canvas, d)
        drawIdleGlow(canvas, radius, d)
    }

    /** Fading accent stroke following the glide path (#3). */
    private fun drawGlideTrail(canvas: Canvas, d: Float) {
        if (!glideMode || trailPoints.size < 2 || powerSave) return
        val path = Path()
        trailPoints.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        trailPaint.strokeWidth = d * 5f
        trailPaint.color = theme.accent
        trailPaint.alpha = 150
        canvas.drawPath(path, trailPaint)
    }

    /** Idle delight (#82): keys breathe light while you think (skipped in saver). */
    private fun drawIdleGlow(canvas: Canvas, radius: Float, d: Float) {
        if (!prefs.idleEasterEgg || powerSave || glideMode) return
        val idleMs = System.currentTimeMillis() - lastTouchAt
        if (idleMs < 4000) return
        val breathe = ((idleMs % 2400) / 2400f)
        val pulse = 0.5f - 0.5f * kotlin.math.cos(breathe * 2f * Math.PI.toFloat())
        shadowPaint.color = withAlpha(theme.accent, (0.10f + 0.10f * pulse))
        shadowPaint.maskFilter = BlurMaskFilter(d * 10f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, shadowPaint
        )
        shadowPaint.maskFilter = BlurMaskFilter(d * 3f, BlurMaskFilter.Blur.NORMAL)
        postInvalidateOnAnimation()
    }

    private fun displayLabel(key: com.infikey.layout.Key): String = when (key.action) {
        com.infikey.layout.KeyAction.SPACE -> key.label
        com.infikey.layout.KeyAction.SHIFT ->
            if (capsLocked) "\u21E7" else if (shiftOn) "\u21E7" else "\u21E7"
        else -> if (shiftOn && layout?.hasCase == true && key.label.length == 1) key.label.uppercase() else key.label
    }

    private fun drawBackgroundImage(canvas: Canvas, radius: Float) {
        val path = prefs.bgImagePath
        if (path.isEmpty()) return
        if (!prefs.feature("photo_keyboard")) return
        runCatching {
            val bmp = android.graphics.BitmapFactory.decodeFile(path) ?: return
            // blur & dim sliders (#26) only affect the backdrop when enabled
            val fx = prefs.feature("blur_dim")
            val dim = if (fx) prefs.bgDim else 0f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = ((1f - dim) * 255).toInt() }
            // blur (#25): downscale then upscale — cheap, real-looking softening
            val pre = if (fx && prefs.bgBlur > 1f) {
                val f = 1f / (prefs.bgBlur / 4f).coerceIn(1f, 16f)
                android.graphics.Bitmap.createScaledBitmap(
                    bmp, (bmp.width * f).toInt().coerceAtLeast(1),
                    (bmp.height * f).toInt().coerceAtLeast(1), true
                )
            } else bmp
            val scaled = android.graphics.Bitmap.createScaledBitmap(
                pre, width, height, true
            )
            if (pre !== bmp) pre.recycle()
            canvas.save()
            val clip = Path().apply { addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius.toFloat(), radius.toFloat(), Path.Direction.CW) }
            canvas.clipPath(clip)
            canvas.drawBitmap(scaled, 0f, 0f, paint)
            canvas.restore()
            scaled.recycle()
            bmp.recycle()
        }.onFailure { Log.w(TAG, "bg image failed", it) }
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        ((alpha * 255).toInt() shl 24) or (color and 0xFFFFFF)

    // ---- touch ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (prefs.glideEnabled && !glideMode && event.action == MotionEvent.ACTION_DOWN) {
            val key = keyRects.firstOrNull { it.first.contains(event.x, event.y) }?.second
            if (key != null && !key.isSpecial && key.label.length == 1) {
                glideMode = true
                callbacks?.onGlideStart(event.x, event.y)
            }
        }
        if (glideMode) return handleGlide(event)

        gestureDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchAt = System.currentTimeMillis()
                activeKey = keyRects.firstOrNull { it.first.contains(event.x, event.y) }?.second
                downTime = System.currentTimeMillis()
                longPressFired = false
                trailPoints.clear()
                shapePoints.clear()
                shapePoints.add(event.x to event.y)
                trackpadLastX = event.x
                trackpadAccum = 0f
                activeKey?.let { ripple(it); haptic(); if (prefs.keySound) click() }
                backspaceLastX = event.x
                backspaceAccum = 0f
                if (prefs.feature("long_press_popups")) postDelayed(longPressRunnable, 380)
            }
            MotionEvent.ACTION_MOVE -> {
                val now = keyRects.firstOrNull { it.first.contains(event.x, event.y) }?.second
                if (now != activeKey) {
                    removeCallbacks(longPressRunnable)
                    activeKey = now
                    activeKey?.let { ripple(it) }
                }
                if (trailPoints.isEmpty() ||
                    (kotlin.math.hypot(
                        (event.x - trailPoints.last().first).toDouble(),
                        (event.y - trailPoints.last().second).toDouble()
                    ) > 8)
                ) trailPoints.add(event.x to event.y)
                if (shapePoints.size < 64) shapePoints.add(event.x to event.y)
                // spacebar slide (#12 basic caret move, #13 trackpad = fine step)
                if (activeKey?.action == com.infikey.layout.KeyAction.SPACE &&
                    !trackpadLastX.isNaN()
                ) {
                    trackpadAccum += event.x - trackpadLastX
                    trackpadLastX = event.x
                    val fine = prefs.feature("trackpad")
                    if (fine || prefs.feature("cursor_drag")) {
                        val step = resources.displayMetrics.density * (if (fine) 12f else 34f)
                        while (trackpadAccum > step) {
                            callbacks?.onKey("cursor_right", com.infikey.layout.KeyAction.NONE)
                            trackpadAccum -= step
                        }
                        while (trackpadAccum < -step) {
                            callbacks?.onKey("cursor_left", com.infikey.layout.KeyAction.NONE)
                            trackpadAccum += step
                        }
                    } else trackpadAccum = 0f
                }
                // swipe-to-delete (#23): slide left on backspace wipes fast
                if (activeKey?.action == com.infikey.layout.KeyAction.BACKSPACE &&
                    !backspaceLastX.isNaN()
                ) {
                    val dxm = event.x - backspaceLastX
                    if (dxm < 0) backspaceAccum -= dxm else backspaceAccum = 0f
                    backspaceLastX = event.x
                    if (prefs.feature("swipe_delete")) {
                        val step = resources.displayMetrics.density * 60f
                        while (backspaceAccum > step) {
                            callbacks?.onKey("delete_word", com.infikey.layout.KeyAction.NONE)
                            backspaceAccum -= step
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                val k = activeKey
                activeKey = null
                rippleKey = null
                trailPoints.clear()
                val shape = if (prefs.shapeGestures && shapePoints.size >= 5) classifyShape(shapePoints) else null
                shapePoints.clear()
                if (shape != null) {
                    callbacks?.onShape(shape)
                } else if (!longPressFired && k != null) {
                    fireKey(k)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                activeKey = null
                rippleKey = null
                invalidate()
            }
        }
        return true
    }

    private fun handleGlide(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_MOVE -> callbacks?.onGlideMove(event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                if (glideMode) {
                    glideMode = false
                    callbacks?.onGlideEnd(event.action == MotionEvent.ACTION_CANCEL)
                }
        }
        return true
    }

    /** Letter key under a point — the glide tracker's lookup. */
    fun letterUnder(x: Float, y: Float): String? =
        keyRects.firstOrNull {
            it.first.contains(x, y) &&
                it.second.action == com.infikey.layout.KeyAction.CHAR &&
                it.second.label.length == 1
        }?.second?.label

    private fun ripple(k: com.infikey.layout.Key) {
        rippleKey = k
        rippleAlpha = 0.55f
        invalidate()
    }

    private fun fireKey(k: com.infikey.layout.Key) {
        val out = if (shiftOn && layout?.hasCase == true && k.label.length == 1 && k.action == com.infikey.layout.KeyAction.CHAR)
            k.label.uppercase() else k.output
        callbacks?.onKey(out, k.action)
        if (k.action == com.infikey.layout.KeyAction.CHAR && shiftOn && !capsLocked) {
            shiftOn = false
            invalidate()
        }
    }

    private fun haptic() {
        if (!prefs.haptics) return
        runCatching {
            val v = context.getSystemService(Vibrator::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(12)
        }
    }

    private fun click() {
        // sound packs (#59)
        val fx = when (prefs.soundPack) {
            "silent" -> return
            "mechanical" -> android.media.AudioManager.FX_KEY_CLICK
            "typewriter" -> android.media.AudioManager.FX_KEYPRESS_DELETE
            "pop" -> android.media.AudioManager.FX_KEYPRESS_SPACEBAR
            else -> android.media.AudioManager.FX_KEYPRESS_STANDARD
        }
        runCatching {
            val am = context.getSystemService(android.media.AudioManager::class.java) ?: return
            am.playSoundEffect(fx, 0.6f)
        }
    }

    /** Classifies the sampled touch path: "^" opens AI, circle selects all (#74). */
    private fun classifyShape(pts: List<Pair<Float, Float>>): String? {
        val dp = resources.displayMetrics.density
        val xs = pts.map { it.first }
        val ys = pts.map { it.second }
        val cx = xs.average(); val cy = ys.average()
        var total = 0f
        var prevA = Float.NaN
        for ((x, y) in pts) {
            val a = Math.atan2((y - cy).toDouble(), (x - cx).toDouble()).toFloat()
            if (!prevA.isNaN()) {
                var da = a - prevA
                if (da > Math.PI) da -= 2 * Math.PI.toFloat()
                if (da < -Math.PI) da += 2 * Math.PI.toFloat()
                total += da
            }
            prevA = a
        }
        if (Math.abs(total) > Math.PI * 1.6) return "circle"
        val minY = ys.min()
        val iMin = ys.indexOf(minY)
        if (iMin in 1 until pts.size - 1 &&
            (ys[0] - minY) > 110 * dp && (ys.last() - minY) > 110 * dp &&
            abs(xs.last() - xs.first()) < 150 * dp
        ) return "caret"
        return null
    }

    // ---- popups ----

    private var popup: android.widget.PopupWindow? = null

    private fun showPopup(k: com.infikey.layout.Key?) {
        if (k == null || k.popups.isEmpty()) return
        longPressFired = true
        haptic()
        val d = resources.displayMetrics.density
        val holder = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setBackgroundColor(withAlpha(theme.keySpecial, 1f))
            setPadding((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
        }
        val labels = if (layout?.hasCase == true && shiftOn) k.popups.map { it.uppercase() } else k.popups
        for (alt in labels) {
            holder.addView(android.widget.TextView(context).apply {
                text = alt
                textSize = 22f
                setTextColor(theme.text)
                typeface = textPaint.typeface
                setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0)
                setOnClickListener {
                    callbacks?.onKey(alt, com.infikey.layout.KeyAction.CHAR)
                    popup?.dismiss()
                }
            })
        }
        popup = android.widget.PopupWindow(holder, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(withAlpha(theme.key, 1f)))
            val rect = keyRects.firstOrNull { it.second == k }
            if (rect != null) showAsDropDown(this@KeyboardView, rect.first.left.toInt(), -(height + rect.first.height().toInt() + (10 * d).toInt()))
        }
        holder.startAnimation(AnimationUtils.loadAnimation(context, android.R.anim.fade_in))
    }

    fun dismissPopup() {
        popup?.dismiss()
        popup = null
    }
}
