package com.consuelomerino.tierracolorada.life

import com.consuelomerino.tierracolorada.objects.GardenObject
import com.consuelomerino.tierracolorada.objects.ObjectType
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeDirectorTest {
    private val heliconia = GardenObject(1, ObjectType.HELICONIA, 100f, 100f, seed = 1)

    private fun runFor(d: LifeDirector, ms: Long, objs: List<GardenObject>, reduced: Boolean = false) =
        (0..ms step 100).map { d.update(it, objs, reduced) }

    @Test fun noHeliconiaNoHummingbird() {
        val states = runFor(LifeDirector(Random(1), 360, 800), 600_000, emptyList())
        assertTrue(states.all { it.hummingbird == null })
    }

    @Test fun firstVisitStartsWithin30To90Seconds() {
        val states = runFor(LifeDirector(Random(2), 360, 800), 120_000, listOf(heliconia))
        val first = states.first { it.hummingbird != null }.hummingbird!!
        assertTrue(first.startMs in 30_000..90_000)
        assertEquals(heliconia, first.target)
    }

    @Test fun hoverLastsThreeToFiveSeconds() {
        val v = runFor(LifeDirector(Random(3), 360, 800), 120_000, listOf(heliconia))
            .first { it.hummingbird != null }.hummingbird!!
        assertTrue(v.hoverMs in 3000..5000)
    }

    @Test fun leavesAppearAndExpire() {
        val states = runFor(LifeDirector(Random(4), 360, 800), 200_000, emptyList())
        assertTrue(states.any { it.leaves.isNotEmpty() })
        states.forEachIndexed { i, s -> s.leaves.forEach { assertTrue(i * 100L - it.startMs <= 9000) } }
    }

    @Test fun reducedMotionStopsEverything() {
        val states = runFor(LifeDirector(Random(5), 360, 800), 300_000, listOf(heliconia), reduced = true)
        assertTrue(states.all { it.hummingbird == null && it.leaves.isEmpty() && it.shadowPhase == 0f && !it.showShadows })
    }
}
