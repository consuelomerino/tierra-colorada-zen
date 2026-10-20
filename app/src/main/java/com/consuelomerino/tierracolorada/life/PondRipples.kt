package com.consuelomerino.tierracolorada.life

/** A ring spreading from where the pond was tapped; [ageMs] grows until it disappears. */
data class Ripple(val x: Float, val y: Float, val ageMs: Long)

class PondRipples {
    private data class Tap(val x: Float, val y: Float, val atMs: Long)

    private val taps = mutableListOf<Tap>()

    fun tap(x: Float, y: Float, nowMs: Long) {
        taps += Tap(x, y, nowMs)
    }

    fun active(nowMs: Long): List<Ripple> {
        taps.removeAll { nowMs - it.atMs >= LIFETIME_MS }
        return taps.map { Ripple(it.x, it.y, nowMs - it.atMs) }
    }

    companion object {
        const val LIFETIME_MS = 2000L
    }
}
