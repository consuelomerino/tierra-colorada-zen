package com.consuelomerino.tierracolorada.storage

import com.consuelomerino.tierracolorada.objects.GardenObject
import com.consuelomerino.tierracolorada.objects.ObjectType
import com.consuelomerino.tierracolorada.sand.HeightMap
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GardenStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var dir: File
    private lateinit var store: GardenStore

    @Before fun setUp() {
        dir = temp.newFolder()
        store = GardenStore(dir)
    }

    @Test fun roundTrip() {
        val m = HeightMap(360, 800).apply { this[5, 5] = -0.7f }
        val o = GardenObject(3, ObjectType.FERN, 10f, 20f, 1.5f, 0.3f, 99)
        store.save(GardenSnapshot(m, listOf(o), muted = true))
        val s = store.load(360, 800)!!
        assertEquals(-0.7f, s.heights[5, 5])
        assertEquals(listOf(o), s.objects)
        assertTrue(s.muted)
    }

    @Test fun missingFileLoadsAsNull() = assertNull(store.load(360, 800))

    @Test fun truncatedFileLoadsAsNull() {
        store.save(GardenSnapshot(HeightMap(360, 800), emptyList(), false))
        val f = File(dir, "garden.bin")
        f.writeBytes(f.readBytes().copyOf(20))
        assertNull(store.load(360, 800))
    }

    @Test fun garbageFileLoadsAsNull() {
        File(dir, "garden.bin").writeText("hello")
        assertNull(store.load(360, 800))
    }

    @Test fun unknownVersionLoadsAsNull() {
        DataOutputStream(File(dir, "garden.bin").outputStream()).use { it.writeInt(0x54435A31); it.writeInt(99) }
        assertNull(store.load(360, 800))
    }

    @Test fun differentAspectIsResampled() {
        store.save(GardenSnapshot(HeightMap(360, 800), listOf(GardenObject(1, ObjectType.STONE, 100f, 400f, seed = 1)), false))
        val s = store.load(360, 720)!!
        assertEquals(720, s.heights.height)
        assertEquals(360f, s.objects[0].y, 1e-3f)
    }

    @Test fun noTempFileLeftBehind() {
        store.save(GardenSnapshot(HeightMap(10, 10), emptyList(), false))
        assertFalse(File(dir, "garden.bin.tmp").exists())
    }
}
