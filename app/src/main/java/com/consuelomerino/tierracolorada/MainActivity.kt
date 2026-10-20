package com.consuelomerino.tierracolorada

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.consuelomerino.tierracolorada.ui.GardenScreen
import com.consuelomerino.tierracolorada.ui.GardenViewModel
import com.consuelomerino.tierracolorada.ui.theme.TierraColoradaTheme

class MainActivity : ComponentActivity() {
    internal val viewModel: GardenViewModel by viewModels { GardenViewModel.factory(filesDir) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TierraColoradaTheme {
                GardenScreen(viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveNow()
    }
}
