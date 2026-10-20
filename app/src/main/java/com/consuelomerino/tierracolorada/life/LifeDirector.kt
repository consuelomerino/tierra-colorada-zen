package com.consuelomerino.tierracolorada.life

import com.consuelomerino.tierracolorada.objects.GardenObject
import com.consuelomerino.tierracolorada.objects.ObjectType
import kotlin.random.Random

enum class VisitPhase { FLYING_IN, HOVERING, LEAVING, DONE }

/** A hummingbird flying in to a heliconia, hovering for [hoverMs], then darting away. */
data class HummingbirdVisit(val target: GardenObject, val startMs: Long, val hoverMs: Long) {
    fun phase(nowMs: Long): VisitPhase {
        val elapsed = nowMs - startMs
        return when {
            elapsed < FLY_IN_MS -> VisitPhase.FLYING_IN
            elapsed < FLY_IN_MS + hoverMs -> VisitPhase.HOVERING
            elapsed < FLY_IN_MS + hoverMs + LEAVE_MS -> VisitPhase.LEAVING
            else -> VisitPhase.DONE
        }
    }

    companion object {
        const val FLY_IN_MS = 1500L
        const val LEAVE_MS = 1000L
    }
}

/** A leaf drifting from (fromX, fromY) to (toX, toY) in grid cells, then resting and fading. */
data class DriftingLeaf(
    val startMs: Long,
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val seed: Int,
) {
    companion object {
        const val DRIFT_MS = 4000L
        const val FADE_MS = 5000L
    }
}

data class LifeState(
    val hummingbird: HummingbirdVisit?,
    val leaves: List<DriftingLeaf>,
    val shadowPhase: Float,
    val showShadows: Boolean,
)

/** Decides when the garden's ambient life appears. Pure timing; drawing lives in LifeDrawing. */
class LifeDirector(
    private val random: Random,
    private val gridWidth: Int,
    private val gridHeight: Int,
) {
    private var started = false
    private var nextVisitAtMs = 0L
    private var nextLeafAtMs = 0L
    private var visit: HummingbirdVisit? = null
    private val leaves = mutableListOf<DriftingLeaf>()

    fun update(nowMs: Long, objects: List<GardenObject>, reducedMotion: Boolean): LifeState {
        if (reducedMotion) {
            visit = null
            leaves.clear()
            return LifeState(null, emptyList(), 0f, showShadows = false)
        }
        if (!started) {
            started = true
            nextVisitAtMs = nowMs + visitDelay()
            nextLeafAtMs = nowMs + leafDelay()
        }
        updateHummingbird(nowMs, objects)
        updateLeaves(nowMs)
        val shadowPhase = (nowMs % SHADOW_CYCLE_MS).toFloat() / SHADOW_CYCLE_MS
        return LifeState(visit, leaves.toList(), shadowPhase, showShadows = true)
    }

    private fun updateHummingbird(nowMs: Long, objects: List<GardenObject>) {
        val current = visit
        if (current != null) {
            val targetGone = objects.none { it.id == current.target.id }
            if (current.phase(nowMs) == VisitPhase.DONE || targetGone) {
                visit = null
                nextVisitAtMs = nowMs + visitDelay()
            }
            return
        }
        if (nowMs < nextVisitAtMs) return
        val heliconias = objects.filter { it.type == ObjectType.HELICONIA }
        if (heliconias.isEmpty()) {
            nextVisitAtMs = nowMs + visitDelay()
        } else {
            visit = HummingbirdVisit(heliconias.random(random), nowMs, random.nextLong(HOVER_MIN_MS, HOVER_MAX_MS + 1))
        }
    }

    private fun updateLeaves(nowMs: Long) {
        leaves.removeAll { nowMs - it.startMs > DriftingLeaf.DRIFT_MS + DriftingLeaf.FADE_MS }
        if (nowMs < nextLeafAtMs) return
        leaves += DriftingLeaf(
            startMs = nowMs,
            fromX = -10f,
            fromY = random.nextFloat() * gridHeight,
            toX = gridWidth * (0.2f + random.nextFloat() * 0.6f),
            toY = gridHeight * (0.1f + random.nextFloat() * 0.8f),
            seed = random.nextInt(),
        )
        nextLeafAtMs = nowMs + leafDelay()
    }

    private fun visitDelay() = random.nextLong(VISIT_MIN_DELAY_MS, VISIT_MAX_DELAY_MS)
    private fun leafDelay() = random.nextLong(LEAF_MIN_DELAY_MS, LEAF_MAX_DELAY_MS)

    companion object {
        const val VISIT_MIN_DELAY_MS = 30_000L
        const val VISIT_MAX_DELAY_MS = 90_000L
        const val HOVER_MIN_MS = 3_000L
        const val HOVER_MAX_MS = 5_000L
        const val LEAF_MIN_DELAY_MS = 20_000L
        const val LEAF_MAX_DELAY_MS = 60_000L
        const val SHADOW_CYCLE_MS = 60_000L
    }
}
