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

    interface Callbacks {
        fun onKey(output: String, action: com.infikey.layout.KeyAction)
        fun onGlideStart(x: Float, y: Float)
        fun onGlideMove(x: Float, y: Float)
        fun onGlideEnd(cancelled: Boolean)
        fun onHideRequest()
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
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SHADOW_LAYER)
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
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (e1 == null) return false
            val dy = e2.y - e1.y
            val dx = e2.x - e1.x
            return when {
                // swipe down anywhere → hide keyboard
                dy > 120 && abs(dy) > abs(dx) * 1.6f -> { callbacks?.onHideRequest(); true }
                // horizontal swipes on space / delete handled via active key at fling start
                else -> horizontalGesture(e1, dx)
            }
        }

        private fun horizontalGesture(e1: MotionEvent, dx: Float): Boolean {
            val k = keyRects.firstOrNull { it.first.contains(e1.x, e1.y) }?.second
            return when {
                k?.action == com.infikey.layout.KeyAction.SPACE && dx < -60 -> {
                    gestureCursor(-1); true
                }
                k?.action == com.infikey.layout.KeyAction.SPACE && dx > 60 -> {
                    gestureCursor(1); true
                }
                k?.action == com.infikey.layout.KeyAction.BACKSPACE && dx < -60 -> {
                    callbacks?.onKey("delete_word", com.infikey.layout.KeyAction.NONE); true
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
        textPaint.typeface = tf
        hintTextPaint.typeface = tf
        invalidate()
    }

    fun setLayout(l: com.infikey.layout.Layout) {
        layout = l
        requestLayout()
        invalidate()
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
        setMeasuredDimension(w, h.coerceAtMost(d * 420))
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

            // soft neumorphic shadow
            shadowPaint.color = base
            shadowPaint.maskFilter = BlurMaskFilter(d * 3f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(rect, radius, radius, shadowPaint)
            canvas.drawRoundRect(rect, radius, radius, fill)

            if (key.action == com.infikey.layout.KeyAction.SHIFT && (shiftOn || capsLocked)) {
                accentPaint.color = if (capsLocked) theme.accent else theme.accent2
                canvas.drawRoundRect(rect, radius, radius, accentPaint)
            }
            if (key == rippleKey && rippleAlpha > 0) {
                ripplePaint.color = withAlpha(theme.accent, rippleAlpha)
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
            if (key.popups.isNotEmpty() && !special) {
                hintTextPaint.color = theme.hint
                canvas.drawText(key.popups.first(), rect.right - d * 8, rect.top + d * 12, hintTextPaint)
            }
        }
    }

    private fun displayLabel(key: com.infikey.layout.Key): String = when (key.action) {
        com.infikey.layout.KeyAction.SPACE -> key.label
        com.infikey.layout.KeyAction.SHIFT ->
            if (capsLocked) "\u21E7" else if (shiftOn) "\u21E7" else "\u21E7"
        else -> if (shiftOn && layout?.hasCase == true && key.label.length == 1) key.label.uppercase() else key.label
    }

    private fun drawBackgroundImage(canvas: Canvas, radius: Int) {
        val path = prefs.bgImagePath
        if (path.isEmpty()) return
        runCatching {
            val bmp = android.graphics.BitmapFactory.decodeFile(path) ?: return
            val dim = prefs.bgDim
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = ((1f - dim) * 255).toInt() }
            val scaled = android.graphics.Bitmap.createScaledBitmap(
                bmp, width, height, true
            )
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
                activeKey = keyRects.firstOrNull { it.first.contains(event.x, event.y) }?.second
                downTime = System.currentTimeMillis()
                longPressFired = false
                activeKey?.let { ripple(it); haptic(); if (prefs.keySound) click() }
                postDelayed(longPressRunnable, 380)
            }
            MotionEvent.ACTION_MOVE -> {
                val now = keyRects.firstOrNull { it.first.contains(event.x, event.y) }?.second
                if (now != activeKey) {
                    removeCallbacks(longPressRunnable)
                    activeKey = now
                    activeKey?.let { ripple(it) }
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                val k = activeKey
                activeKey = null
                rippleKey = null
                invalidate()
                if (!longPressFired && k != null) fireKey(k)
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
        runCatching {
            val am = context.getSystemService(android.media.AudioManager::class.java) ?: return
            am.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD, 0.6f)
        }
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
