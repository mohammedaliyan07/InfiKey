package com.infikey.ime

/**
 * Glide typing: samples the touch path, keeps the key letters the finger
 * crossed (with dwell filtering), and hands them to the suggestion engine
 * for scoring once the finger lifts.
 */
class GlideTracker {

    data class Sample(val x: Float, val y: Float, val tMs: Long)

    private val samples = mutableListOf<Sample>()
    private val letters = mutableListOf<String>()
    private var active = false
    private var lastKey: String? = null
    private var lastKeyChangeMs = 0L

    /** Minimum dwell on a key (ms) before it counts as a letter. */
    var minDwellMs: Long = 55L
    /** Minimum travel between samples (px). */
    var minTravelPx: Float = 6f

    fun start(x: Float, y: Float, now: Long) {
        samples.clear()
        letters.clear()
        lastKey = null
        active = true
        samples.add(Sample(x, y, now))
    }

    fun move(x: Float, y: Float, now: Long, keyAt: (Float, Float) -> String?) {
        if (!active) return
        val last = samples.lastOrNull() ?: return
        val d = kotlin.math.hypot((x - last.x).toDouble(), (y - last.y).toDouble())
        if (d < minTravelPx) return
        samples.add(Sample(x, y, now))
        val key = keyAt(x, y)
        if (key != null && key != lastKey) {
            lastKey = key
            lastKeyChangeMs = now
        } else if (key != null && key == lastKey && now - lastKeyChangeMs >= minDwellMs) {
            if (letters.isEmpty() || letters.last() != key) letters.add(key)
            lastKeyChangeMs = now
        }
    }

    /** Returns the ordered key letters of the glide path, or null if it wasn't a glide. */
    fun finish(): List<String>? {
        active = false
        return if (letters.size >= 2) letters.toList() else null
    }

    fun isActive(): Boolean = active

    fun letterCount(): Int = letters.size
}
