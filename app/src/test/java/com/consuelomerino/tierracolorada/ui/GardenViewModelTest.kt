package com.consuelomerino.tierracolorada.ui

import com.consuelomerino.tierracolorada.objects.ObjectType
import com.consuelomerino.tierracolorada.storage.GardenStore
import java.io.File
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GardenViewModelTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var dir: File
    private lateinit var vm: GardenViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dir = temp.newFolder()
        vm = GardenViewModel(GardenStore(dir))
    }

    @After fun tearDown() {
        stopAutosave()
        Dispatchers.resetMain()
    }

    /**
     * runTest shares the main test scheduler and skips ahead through delays, which would fire the
     * 30 s autosave; its save then resumes on Main after tearDown has reset it. Call at the end of runTest bodies.
     */
    private fun stopAutosave() = vm.viewModelScope.cancel()

    @Test fun rakeStrokeChangesSandAndBumpsFrame() {
        vm.initialize(360, 800)
        val f = vm.sandFrame
        vm.strokeStart(50f, 50f); vm.strokeMove(90f, 50f, 16); vm.strokeEnd()
        assertTrue(vm.sandFrame > f)
        assertTrue(vm.heights!!.values.any { it < -0.5f })
    }

    @Test fun smoothToolDoesNotCarve() {
        vm.initialize(360, 800)
        vm.selectTool(Tool.SMOOTH)
        vm.strokeStart(50f, 50f); vm.strokeMove(90f, 50f, 16); vm.strokeEnd()
        assertTrue(vm.heights!!.values.all { it == 0f })
    }

    @Test fun smoothKeepsWorkingEachFrameWhileTheFingerIsStill() {
        vm.initialize(360, 800)
        vm.heights!![100, 100] = -0.8f
        vm.selectTool(Tool.SMOOTH)
        vm.strokeStart(100.5f, 100.5f)
        assertTrue(vm.smoothing)
        repeat(3) { vm.smoothFrame() }
        assertEquals(-0.8f * 0.8f * 0.8f * 0.8f, vm.heights!![100, 100], 1e-4f)
        vm.strokeEnd()
        assertFalse(vm.smoothing)
        vm.smoothFrame()
        assertEquals(-0.8f * 0.8f * 0.8f * 0.8f, vm.heights!![100, 100], 1e-4f)
    }

    @Test fun smoothFollowsTheFinger() {
        vm.initialize(360, 800)
        vm.heights!![200, 100] = -0.8f
        vm.selectTool(Tool.SMOOTH)
        vm.strokeStart(100f, 100f)
        vm.strokeMove(200.5f, 100.5f, 16)
        vm.smoothFrame()
        assertEquals(-0.8f * 0.8f, vm.heights!![200, 100], 1e-4f)
        vm.strokeEnd()
    }

    @Test fun newWindowSizeRefitsSandAndObjects() {
        vm.initialize(360, 800)
        vm.addObject(ObjectType.FERN, 100f, 400f)
        vm.heights!![200, 400] = -0.8f
        vm.initialize(360, 720) // e.g. the app went into split screen
        val map = vm.heights!!
        assertEquals(720, map.height)
        assertEquals(-0.8f, map[200, 360], 0.05f)
        assertEquals(360f, vm.objects.single().y, 1e-3f)
        // The rake now works on the new grid, right under the finger.
        vm.strokeStart(300f, 700f); vm.strokeMove(340f, 700f, 16); vm.strokeEnd()
        assertTrue((690..710).any { y -> (300..340).any { x -> map[x, y] < -0.5f } })
        // The garden's rules use the new size: an object dropped low stays inside it.
        vm.addObject(ObjectType.STONE, 50f, 790f)
        assertTrue(vm.objects.all { it.y <= 720f })
    }

    @Test fun sameWindowSizeKeepsTheGarden() {
        vm.initialize(360, 800)
        val map = vm.heights
        vm.initialize(360, 800)
        assertSame(map, vm.heights)
    }

    @Test fun clearNeedsConfirmation() {
        vm.initialize(360, 800)
        vm.strokeStart(50f, 50f); vm.strokeMove(90f, 50f, 16); vm.strokeEnd()
        vm.requestClear()
        assertTrue(vm.confirmingClear)
        vm.cancelClear()
        assertFalse(vm.heights!!.values.all { it == 0f })
    }

    @Test fun confirmedClearFlattensAfterSweep() {
        vm.initialize(360, 800)
        vm.strokeStart(50f, 50f); vm.strokeMove(90f, 50f, 16); vm.strokeEnd()
        vm.requestClear(); vm.confirmClear(); vm.advanceClear(600)
        assertTrue(vm.heights!!.values.all { it == 0f })
    }

    @Test fun muteIsSavedAndRestored() {
        vm.initialize(360, 800)
        vm.toggleMute(); vm.saveNow()
        val vm2 = GardenViewModel(GardenStore(dir))
        vm2.initialize(360, 800)
        assertTrue(vm2.muted)
    }

    @Test fun rakingIsTrueOnlyDuringARakeStroke() {
        vm.initialize(360, 800)
        assertFalse(vm.raking)
        vm.strokeStart(50f, 50f)
        assertTrue(vm.raking)
        vm.strokeMove(90f, 50f, 16)
        vm.strokeEnd()
        assertFalse(vm.raking)
    }

    @Test fun stopScrapeIsDeliveredEvenWhenTheListenerFallsBehind() = runTest {
        vm.initialize(360, 800)
        val speeds = mutableListOf<Float>()
        // A collector that only runs when the test scheduler advances, like a busy main thread.
        val job = launch(StandardTestDispatcher(testScheduler)) {
            vm.events.collect { if (it is GardenEvent.RakeSpeed) speeds += it.cellsPerSecond }
        }
        testScheduler.runCurrent() // subscribe
        vm.strokeStart(10f, 100f)
        repeat(40) { vm.strokeMove(10f + (it + 1) * 5f, 100f, 16) }
        vm.strokeEnd()
        testScheduler.runCurrent()
        job.cancel()
        stopAutosave()
        assertEquals("the final event must stop the scrape", 0f, speeds.last())
    }

    @Test fun hapticTickEvery24Cells() = runTest {
        vm.initialize(360, 800)
        val ticks = mutableListOf<GardenEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { if (it == GardenEvent.HapticTick) ticks += it }
        }
        vm.strokeStart(10f, 100f)
        repeat(10) { vm.strokeMove(10f + (it + 1) * 10f, 100f, 16) }
        vm.strokeEnd()
        job.cancel()
        stopAutosave()
        assertTrue("ticks = ${ticks.size}", ticks.size in 2..4)
    }

    @Test fun failedSaveDoesNotCrashAndStaysPending() {
        val blocked = File(temp.root, "not-a-dir").apply { writeText("x") }
        val failing = GardenViewModel(GardenStore(blocked))
        failing.initialize(360, 800)
        failing.toggleMute()
        failing.saveNow() // must not throw even though the directory is a file
        blocked.delete(); blocked.mkdirs()
        failing.saveNow() // the change is still pending, so this save writes it
        val reloaded = GardenViewModel(GardenStore(blocked)).apply { initialize(360, 800) }
        assertTrue(reloaded.muted)
    }
}
