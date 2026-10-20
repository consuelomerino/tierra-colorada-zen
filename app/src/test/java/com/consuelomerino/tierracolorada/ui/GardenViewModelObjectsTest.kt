package com.consuelomerino.tierracolorada.ui

import com.consuelomerino.tierracolorada.objects.ObjectType
import com.consuelomerino.tierracolorada.storage.GardenStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GardenViewModelObjectsTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var dir: File
    private lateinit var vm: GardenViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dir = temp.newFolder()
        vm = GardenViewModel(GardenStore(dir))
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun placingStoneStampsRipples() {
        vm.initialize(360, 800)
        vm.addObject(ObjectType.STONE, 180f, 400f)
        assertTrue(vm.heights!![180 + 14 + 3, 400] < -0.5f)
    }

    @Test fun rakeSkipsObjects() {
        vm.initialize(360, 800)
        vm.addObject(ObjectType.FERN, 100f, 100f)
        vm.strokeStart(60f, 100f)
        vm.strokeMove(140f, 100f, 16)
        repeat(3) { vm.strokeMove(140f, 100f, 16) }
        vm.strokeEnd()
        assertEquals(0f, vm.heights!![100, 96])
    }

    @Test fun objectsSurviveSaveAndLoad() {
        vm.initialize(360, 800)
        vm.addObject(ObjectType.HELICONIA, 50f, 60f)
        vm.saveNow()
        val vm2 = GardenViewModel(GardenStore(dir))
        vm2.initialize(360, 800)
        assertEquals(ObjectType.HELICONIA, vm2.objects.single().type)
    }
}
