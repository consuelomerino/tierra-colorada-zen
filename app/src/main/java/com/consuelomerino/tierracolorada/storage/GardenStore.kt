package com.consuelomerino.tierracolorada.storage

import android.util.Log
import com.consuelomerino.tierracolorada.objects.GardenObject
import com.consuelomerino.tierracolorada.objects.GardenObjects
import com.consuelomerino.tierracolorada.objects.ObjectType
import com.consuelomerino.tierracolorada.sand.HeightMap
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.Deflater
import java.util.zip.Inflater

/** Everything that is kept between sessions. */
data class GardenSnapshot(val heights: HeightMap, val objects: List<GardenObject>, val muted: Boolean)

/** Saves the garden to a private file atomically and loads it back, tolerating damaged files. */
class GardenStore(private val dir: File) {
    private val file get() = File(dir, FILE_NAME)
    private val tempFile get() = File(dir, "$FILE_NAME.tmp")

    /** Writes the snapshot atomically. Calls are serialised so concurrent saves never share the temp file. */
    @Synchronized
    fun save(snapshot: GardenSnapshot) {
        dir.mkdirs()
        val tmp = tempFile
        FileOutputStream(tmp).use { fileOut ->
            val out = DataOutputStream(fileOut.buffered())
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            val heights = snapshot.heights
            out.writeInt(heights.width)
            out.writeInt(heights.height)
            val compressed = compress(heights.values)
            out.writeInt(compressed.size)
            out.write(compressed)
            out.writeInt(snapshot.objects.size)
            for (o in snapshot.objects) {
                out.writeLong(o.id)
                out.writeInt(typeCode(o.type))
                out.writeFloat(o.x)
                out.writeFloat(o.y)
                out.writeFloat(o.scale)
                out.writeFloat(o.rotation)
                out.writeInt(o.seed)
            }
            out.writeBoolean(snapshot.muted)
            out.flush()
            fileOut.fd.sync()
        }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Returns the saved garden fitted to the given grid size, or null if there is none or it cannot be read. */
    @Synchronized
    fun load(gridWidth: Int, gridHeight: Int): GardenSnapshot? {
        val f = file
        if (!f.exists()) return null
        return try {
            DataInputStream(f.inputStream().buffered()).use { read(it, f.length(), gridWidth, gridHeight) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read saved garden; starting fresh", e)
            null
        }
    }

    private fun read(input: DataInputStream, fileLength: Long, gridWidth: Int, gridHeight: Int): GardenSnapshot? {
        if (input.readInt() != MAGIC) return invalid("wrong magic")
        val version = input.readInt()
        if (version != VERSION && version != VERSION_ORDINAL_TYPES) return invalid("unknown version")
        val width = input.readInt()
        val height = input.readInt()
        if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION || width.toLong() * height > MAX_CELLS) {
            return invalid("bad size")
        }
        val compressedSize = input.readInt()
        if (compressedSize !in 0..fileLength) return invalid("bad data length")
        val compressed = ByteArray(compressedSize).also { input.readFully(it) }
        val values = decompress(compressed, width * height) ?: return invalid("bad height data")

        val count = input.readInt()
        if (count !in 0..MAX_OBJECTS_IN_FILE) return invalid("bad object count")
        val objects = ArrayList<GardenObject>(count)
        repeat(count) {
            val id = input.readLong()
            val code = input.readInt()
            val type = (if (version == VERSION_ORDINAL_TYPES) ObjectType.entries.getOrNull(code) else typeForCode(code))
                ?: return invalid("bad object type")
            val x = input.readFloat()
            val y = input.readFloat()
            val scale = input.readFloat()
            val rotation = input.readFloat()
            val seed = input.readInt()
            // An object with a broken position or angle could never be drawn or moved; leave it out.
            if (!x.isFinite() || !y.isFinite() || !rotation.isFinite()) {
                Log.w(TAG, "Dropping saved object $id with a broken position or rotation")
                return@repeat
            }
            val safeScale = if (scale.isFinite()) scale.coerceIn(GardenObjects.MIN_SCALE, GardenObjects.MAX_SCALE) else 1f
            objects += GardenObject(id, type, x, y, safeScale, rotation, seed)
        }
        val muted = input.readBoolean()

        var heights = HeightMap(width, height, values)
        var fitted: List<GardenObject> = objects
        if (width != gridWidth || height != gridHeight) {
            heights = heights.resampledTo(gridWidth, gridHeight)
            val sx = gridWidth.toFloat() / width
            val sy = gridHeight.toFloat() / height
            fitted = objects.map { it.copy(x = it.x * sx, y = it.y * sy) }
        }
        return GardenSnapshot(heights, fitted, muted)
    }

    private fun invalid(reason: String): GardenSnapshot? {
        Log.w(TAG, "Saved garden is invalid ($reason); starting fresh")
        return null
    }

    private fun compress(values: FloatArray): ByteArray {
        val raw = ByteBuffer.allocate(values.size * 4).apply { asFloatBuffer().put(values) }.array()
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(raw)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    private fun decompress(data: ByteArray, count: Int): FloatArray? {
        val raw = ByteArray(count * 4)
        val inflater = Inflater()
        inflater.setInput(data)
        var read = 0
        while (read < raw.size && !inflater.finished()) {
            val n = inflater.inflate(raw, read, raw.size - read)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            read += n
        }
        val complete = read == raw.size && inflater.finished()
        inflater.end()
        if (!complete) return null
        val values = FloatArray(count)
        ByteBuffer.wrap(raw).asFloatBuffer().get(values)
        return values
    }

    companion object {
        /** File codes for object types; never change or reuse one, only add new ones. */
        private fun typeCode(type: ObjectType): Int = when (type) {
            ObjectType.STONE -> 1
            ObjectType.FERN -> 2
            ObjectType.HELICONIA -> 3
            ObjectType.POND -> 4
        }

        private fun typeForCode(code: Int): ObjectType? = ObjectType.entries.firstOrNull { typeCode(it) == code }

        private const val TAG = "GardenStore"
        private const val FILE_NAME = "garden.bin"
        private const val MAGIC = 0x54435A31 // "TCZ1"
        /** Version 2 stores object types as the fixed codes below. */
        private const val VERSION = 2
        /** Version 1 stored ObjectType.ordinal, which would change meaning if the enum were reordered. */
        private const val VERSION_ORDINAL_TYPES = 1
        private const val MAX_DIMENSION = 10_000
        /** Far above any real garden (360 × ~1100) and small enough to allocate safely on a phone. */
        private const val MAX_CELLS = 4_000_000L
        private const val MAX_OBJECTS_IN_FILE = 1_000
    }
}
