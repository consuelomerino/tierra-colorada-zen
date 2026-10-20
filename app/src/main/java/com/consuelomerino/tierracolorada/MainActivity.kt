package com.consuelomerino.tierracolorada

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.consuelomerino.tierracolorada.audio.GardenAudio
import com.consuelomerino.tierracolorada.audio.SoundEffect
import com.consuelomerino.tierracolorada.ui.GardenEvent
import com.consuelomerino.tierracolorada.ui.GardenScreen
import com.consuelomerino.tierracolorada.ui.GardenViewModel
import com.consuelomerino.tierracolorada.ui.theme.TierraColoradaTheme

class MainActivity : ComponentActivity() {
    internal val viewModel: GardenViewModel by viewModels { GardenViewModel.factory(filesDir) }
    private lateinit var audio: GardenAudio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        audio = GardenAudio(this)
        setContent {
            TierraColoradaTheme {
                // Audio starts muted and follows the saved setting once the garden has loaded.
                val loaded = viewModel.heights != null
                LaunchedEffect(loaded, viewModel.muted) {
                    if (loaded) audio.setMuted(viewModel.muted)
                }
                GardenScreen(viewModel, onEvent = ::playFor)
            }
        }
    }

    private fun playFor(event: GardenEvent) {
        when (event) {
            GardenEvent.StonePlaced -> audio.play(SoundEffect.STONE)
            GardenEvent.WaterTap -> audio.play(SoundEffect.WATER)
            GardenEvent.Sweep -> audio.play(SoundEffect.SWEEP)
            // A speed still queued from a stroke that has ended must not restart the scrape.
            is GardenEvent.RakeSpeed -> audio.setRakeSpeed(if (viewModel.raking) event.cellsPerSecond else 0f)
            GardenEvent.HapticTick -> Unit
        }
    }

    override fun onStart() {
        super.onStart()
        audio.resume()
    }

    override fun onStop() {
        super.onStop()
        audio.pause()
        viewModel.saveNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.release()
    }
}
