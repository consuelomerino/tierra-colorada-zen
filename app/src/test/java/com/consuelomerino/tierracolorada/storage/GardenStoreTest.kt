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

    @Test fun oversizedGridIsRejectedBeforeAllocating() {
        // A real garden grid is 360 wide and at most ~1100 tall; a header claiming millions of cells
        // must be refused before the app tries to allocate it (a phone would run out of memory).
        store.save(GardenSnapshot(HeightMap(3000, 3000), emptyList(), false))
        assertNull(store.load(360, 800))
    }

    /** Writes a file by hand: [typeCode] is the ordinal in version 1 and the stable code in version 2. */
    private fun writeFile(version: Int, objects: List<GardenObject>, typeCode: (ObjectType) -> Int) {
        val heights = HeightMap(10, 10)
        val raw = java.nio.ByteBuffer.allocate(heights.values.size * 4).apply { asFloatBuffer().put(heights.values) }.array()
        val deflater = java.util.zip.Deflater().apply { setInput(raw); finish() }
        val buffer = ByteArray(4096)
        val compressed = buffer.copyOf(deflater.deflate(buffer))
        DataOutputStream(File(dir, "garden.bin").outputStream()).use { out ->
            out.writeInt(0x54435A31); out.writeInt(version)
            out.writeInt(10); out.writeInt(10)
            out.writeInt(compressed.size); out.write(compressed)
            out.writeInt(objects.size)
            for (o in objects) {
                out.writeLong(o.id); out.writeInt(typeCode(o.type))
                out.writeFloat(o.x); out.writeFloat(o.y); out.writeFloat(o.scale); out.writeFloat(o.rotation)
                out.writeInt(o.seed)
            }
            out.writeBoolean(true)
        }
    }

    @Test fun versionOneFilesStillLoad() {
        val objects = ObjectType.entries.mapIndexed { i, type -> GardenObject(i + 1L, type, 2f, 3f, 1.2f, 0.4f, i) }
        writeFile(1, objects) { it.ordinal }
        val s = store.load(10, 10)!!
        assertEquals(objects, s.objects)
        assertTrue(s.muted)
    }

    @Test fun typesAreSavedAsStableCodesNotOrdinals() {
        // Version 2 codes are fixed numbers, so reordering ObjectType can never turn a stone into a pond.
        val objects = listOf(GardenObject(1, ObjectType.POND, 2f, 3f, seed = 0), GardenObject(2, ObjectType.STONE, 4f, 5f, seed = 0))
        val codes = mapOf(ObjectType.STONE to 1, ObjectType.FERN to 2, ObjectType.HELICONIA to 3, ObjectType.POND to 4)
        writeFile(2, objects) { codes.getValue(it) }
        assertEquals(objects, store.load(10, 10)!!.objects)
        store.save(GardenSnapshot(HeightMap(10, 10), objects, false))
        val bytes = File(dir, "garden.bin").readBytes()
        val input = java.io.DataInputStream(bytes.inputStream())
        input.readInt(); assertEquals(2, input.readInt())
        input.readInt(); input.readInt(); input.skipBytes(input.readInt())
        assertEquals(2, input.readInt())
        input.readLong(); assertEquals(4, input.readInt())
    }

    @Test fun objectsWithBrokenNumbersAreDroppedAndScaleIsClamped() {
        val good = GardenObject(1, ObjectType.STONE, 2f, 3f, 9f, 0f, 0)
        val broken = listOf(
            GardenObject(2, ObjectType.FERN, Float.NaN, 3f, seed = 0),
            GardenObject(3, ObjectType.FERN, 2f, Float.POSITIVE_INFINITY, seed = 0),
            GardenObject(4, ObjectType.FERN, 2f, 3f, rotation = Float.NaN, seed = 0),
        )
        writeFile(1, listOf(good) + broken) { it.ordinal }
        val loaded = store.load(10, 10)!!.objects
        assertEquals(listOf(good.copy(scale = 2f)), loaded)
    }

    @Test fun nanScaleBecomesOne() {
        writeFile(1, listOf(GardenObject(1, ObjectType.STONE, 2f, 3f, Float.NaN, 0f, 0))) { it.ordinal }
        assertEquals(1f, store.load(10, 10)!!.objects.single().scale)
    }

    @Test fun concurrentSavesNeverLeaveAnUnreadableFile() {
        val a = GardenSnapshot(HeightMap(360, 800).apply { values.fill(-0.3f) }, emptyList(), false)
        val b = GardenSnapshot(HeightMap(360, 800).apply { values.fill(0.2f) }, emptyList(), true)
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val threads = List(4) { i ->
            Thread { repeat(15) { runCatching { store.save(if (i % 2 == 0) a else b) }.onFailure { errors += it } } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue("save threw: $errors", errors.isEmpty())
        assertTrue(store.load(360, 800) != null)
    }
}
