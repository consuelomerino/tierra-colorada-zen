package com.consuelomerino.tierracolorada

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.consuelomerino.tierracolorada.ui.theme.SandBase
import com.consuelomerino.tierracolorada.ui.theme.TierraColoradaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TierraColoradaTheme {
                Box(Modifier.fillMaxSize().background(SandBase))
            }
        }
    }
}
