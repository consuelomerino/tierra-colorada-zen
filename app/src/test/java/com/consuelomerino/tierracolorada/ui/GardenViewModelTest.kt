package com.consuelomerino.tierracolorada.ui

import com.consuelomerino.tierracolorada.storage.GardenStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
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

    @After fun tearDown() = Dispatchers.resetMain()

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

    @Test fun clearNeedsConfirmation() {
        vm.initialize(360, 800)
        vm.strokeStart(50f, 50f); vm.strokeEnd()
        vm.requestClear()
        assertTrue(vm.confirmingClear)
        vm.cancelClear()
        assertFalse(vm.heights!!.values.all { it == 0f })
    }

    @Test fun confirmedClearFlattensAfterSweep() {
        vm.initialize(360, 800)
        vm.strokeStart(50f, 50f); vm.strokeEnd()
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
        assertTrue("ticks = ${ticks.size}", ticks.size in 2..4)
    }
}
