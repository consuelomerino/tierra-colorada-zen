package com.consuelomerino.tierracolorada.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.consuelomerino.tierracolorada.life.PondRipples
import com.consuelomerino.tierracolorada.objects.GardenObject
import com.consuelomerino.tierracolorada.objects.GardenObjects
import com.consuelomerino.tierracolorada.objects.ObjectType
import com.consuelomerino.tierracolorada.sand.ClearSweep
import com.consuelomerino.tierracolorada.sand.HeightMap
import com.consuelomerino.tierracolorada.sand.Rake
import com.consuelomerino.tierracolorada.sand.SandTools
import com.consuelomerino.tierracolorada.storage.GardenSnapshot
import com.consuelomerino.tierracolorada.storage.GardenStore
import java.io.File
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tool { RAKE, PLACE, SMOOTH }

/** One-off things the screen turns into sound or vibration. */
sealed interface GardenEvent {
    data object StonePlaced : GardenEvent
    data object WaterTap : GardenEvent
    data object Sweep : GardenEvent
    data object HapticTick : GardenEvent
    data class RakeSpeed(val cellsPerSecond: Float) : GardenEvent
}

/** Holds the garden and turns gestures (in grid-cell coordinates) into changes to it. */
class GardenViewModel(
    private val store: GardenStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    var tool by mutableStateOf(Tool.RAKE)
        private set
    var objects by mutableStateOf(emptyList<GardenObject>())
        private set
    var muted by mutableStateOf(false)
        private set
    var confirmingClear by mutableStateOf(false)
        private set
    var clearing by mutableStateOf(false)
        private set
    var sandFrame by mutableIntStateOf(0)
        private set
    var heights: HeightMap? by mutableStateOf(null)
        private set

    val pondRipples = PondRipples()

    private val _events = MutableSharedFlow<GardenEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<GardenEvent> = _events

    private lateinit var garden: GardenObjects
    private lateinit var rake: Rake
    private var sweep: ClearSweep? = null
    private var rakeTravel = 0f
    @Volatile private var unsaved = false

    init {
        viewModelScope.launch {
            while (true) {
                delay(AUTOSAVE_INTERVAL_MS)
                saveInBackground()
            }
        }
    }

    fun initialize(gridWidth: Int, gridHeight: Int) {
        if (heights != null) return
        val snapshot = store.load(gridWidth, gridHeight)
        val map = snapshot?.heights ?: HeightMap(gridWidth, gridHeight)
        garden = GardenObjects(gridWidth, gridHeight, snapshot?.objects.orEmpty())
        rake = Rake(map) { x, y -> garden.isBlocked(x, y) }
        objects = garden.items
        muted = snapshot?.muted ?: false
        map.markAllDirty()
        heights = map
        sandFrame++
    }

    fun selectTool(tool: Tool) {
        this.tool = tool
    }

    fun strokeStart(x: Float, y: Float) {
        val map = heights ?: return
        when (tool) {
            Tool.RAKE -> {
                rakeTravel = 0f
                rake.beginStroke(x, y)
            }
            Tool.SMOOTH -> SandTools.smooth(map, x, y) { cx, cy -> garden.isBlocked(cx, cy) }
            Tool.PLACE -> return
        }
        sandChanged()
    }

    fun strokeMove(x: Float, y: Float, dtMs: Long) {
        val map = heights ?: return
        when (tool) {
            Tool.RAKE -> {
                val travelled = rake.moveTo(x, y)
                rakeTravel += travelled
                while (rakeTravel >= HAPTIC_TICK_CELLS) {
                    rakeTravel -= HAPTIC_TICK_CELLS
                    _events.tryEmit(GardenEvent.HapticTick)
                }
                val speed = if (dtMs > 0) travelled * 1000f / dtMs else 0f
                _events.tryEmit(GardenEvent.RakeSpeed(speed))
            }
            Tool.SMOOTH -> SandTools.smooth(map, x, y) { cx, cy -> garden.isBlocked(cx, cy) }
            Tool.PLACE -> return
        }
        sandChanged()
    }

    fun strokeEnd() {
        if (tool == Tool.RAKE && heights != null) {
            rake.endStroke()
            _events.tryEmit(GardenEvent.RakeSpeed(0f))
        }
    }

    /** A touch without dragging: ripples the pond if it was touched. */
    fun tap(x: Float, y: Float) {
        if (heights == null) return
        val pond = garden.pond() ?: return
        if (pond.covers(x, y)) {
            pondRipples.tap(x, y, clock())
            _events.tryEmit(GardenEvent.WaterTap)
        }
    }

    val gardenFull: Boolean get() = objects.size >= GardenObjects.MAX_OBJECTS

    fun canAdd(type: ObjectType): Boolean =
        !gardenFull && (type != ObjectType.POND || objects.none { it.type == ObjectType.POND })

    fun addObject(type: ObjectType, x: Float, y: Float) {
        if (heights == null) return
        val added = garden.add(type, x, y, Random.nextInt()) ?: return
        objectsChanged()
        if (added.type == ObjectType.STONE) pressIntoSand(added)
    }

    fun moveObject(id: Long, x: Float, y: Float) {
        if (heights == null) return
        garden.move(id, x, y) ?: return
        objectsChanged()
    }

    /** Called when the finger lets go of a moved object; a stone settles and presses its rings. */
    fun endMoveObject(id: Long) {
        if (heights == null) return
        garden.items.firstOrNull { it.id == id && it.type == ObjectType.STONE }?.let { pressIntoSand(it) }
    }

    fun transformObject(id: Long, scale: Float, rotation: Float) {
        if (heights == null) return
        garden.transform(id, scale, rotation) ?: return
        objectsChanged()
    }

    fun deleteObject(id: Long) {
        if (heights == null) return
        garden.remove(id)
        objectsChanged()
    }

    private fun pressIntoSand(stone: GardenObject) {
        val map = heights ?: return
        SandTools.stampRipples(map, stone.x, stone.y, stone.radius) { x, y -> garden.isBlocked(x, y) }
        sandChanged()
        _events.tryEmit(GardenEvent.StonePlaced)
    }

    private fun objectsChanged() {
        objects = garden.items
        unsaved = true
    }

    fun requestClear() {
        confirmingClear = true
    }

    fun cancelClear() {
        confirmingClear = false
    }

    fun confirmClear() {
        val map = heights ?: return
        confirmingClear = false
        sweep = ClearSweep(map)
        clearing = true
        _events.tryEmit(GardenEvent.Sweep)
    }

    fun advanceClear(elapsedMs: Long) {
        val current = sweep ?: return
        if (current.advance(elapsedMs)) {
            sweep = null
            clearing = false
        }
        sandChanged()
    }

    fun toggleMute() {
        muted = !muted
        unsaved = true
    }

    /** Saves right away if anything changed since the last save. */
    fun saveNow() {
        snapshotIfUnsaved()?.let { trySave(it) }
    }

    private suspend fun saveInBackground() {
        val snapshot = snapshotIfUnsaved() ?: return
        withContext(Dispatchers.IO) { trySave(snapshot) }
    }

    /** A failed save (e.g. a full disk) must never crash the game; the change stays pending for the next try. */
    private fun trySave(snapshot: GardenSnapshot) {
        try {
            store.save(snapshot)
        } catch (e: IOException) {
            Log.w(TAG, "Could not save the garden; will retry", e)
            unsaved = true
        }
    }

    private fun snapshotIfUnsaved(): GardenSnapshot? {
        val map = heights ?: return null
        if (!unsaved) return null
        unsaved = false
        val copy = HeightMap(map.width, map.height, map.values.copyOf())
        return GardenSnapshot(copy, garden.items, muted)
    }

    private fun sandChanged() {
        unsaved = true
        sandFrame++
    }

    companion object {
        private const val TAG = "GardenViewModel"
        const val AUTOSAVE_INTERVAL_MS = 30_000L
        const val HAPTIC_TICK_CELLS = 24f

        fun factory(filesDir: File): ViewModelProvider.Factory = viewModelFactory {
            initializer { GardenViewModel(GardenStore(filesDir)) }
        }
    }
}
