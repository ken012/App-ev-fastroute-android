package com.evfastroute.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evfastroute.core.ChargePlanner
import com.evfastroute.core.RouteObjective

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StartupScreen()
                }
            }
        }
    }
}

@Composable
private fun StartupScreen() {
    // Proves the shared :core logic is linked into the Android app.
    val chargeMinutes = ChargePlanner.chargeMinutes(fromSOC = 20, toSOC = 80, capacityKwh = 75.0, effectiveKw = 150.0)
    val objectives = RouteObjective.plannerCases.joinToString(", ") { it.mode }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Arrangement.CenterVertically),
    ) {
        Text("EV FastRoute", style = MaterialTheme.typography.headlineMedium)
        Text("Android — shared core linked ✓", style = MaterialTheme.typography.titleMedium)
        Text("20→80% charge ≈ $chargeMinutes min (SOC-band taper)")
        Text("Route options: $objectives")
    }
}
