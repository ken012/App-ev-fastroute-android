package com.evfastroute.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evfastroute.android.map.ChargerBrowseMap
import com.evfastroute.android.net.ChargerBrowseRepository
import com.evfastroute.android.net.OcmBounds
import com.evfastroute.android.net.OcmClient
import com.evfastroute.android.net.ServiceResult
import com.evfastroute.android.net.userMessage
import com.evfastroute.core.Charger
import com.evfastroute.core.ChargerStatus
import com.evfastroute.core.ConnectorType
import com.evfastroute.core.LatLon
import kotlinx.coroutines.delay

@Composable
internal fun ChargerBrowseScreen(
    title: String,
    initialCenter: LatLon,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val repository = remember { ChargerBrowseRepository() }
    var chargers by remember { mutableStateOf<List<Charger>>(emptyList()) }
    var requestedBounds by remember(initialCenter) { mutableStateOf(initialBrowseBounds(initialCenter)) }
    var fetchedBounds by remember { mutableStateOf<OcmBounds?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var failureMessage by remember { mutableStateOf<String?>(null) }
    var selectedCharger by remember { mutableStateOf<Charger?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(requestedBounds, retryToken) {
        if (!OcmClient.isConfigured) return@LaunchedEffect
        if (fetchedBounds != null) delay(350)
        isLoading = true
        failureMessage = null
        when (val result = repository.chargers(requestedBounds)) {
            is ServiceResult.Success -> {
                chargers = result.value
                fetchedBounds = requestedBounds
            }
            is ServiceResult.Failure -> {
                failureMessage = result.error.userMessage("Charging-station data")
            }
        }
        isLoading = false
    }

    selectedCharger?.let { charger ->
        ChargerBrowseDetailDialog(charger = charger, onDismiss = { selectedCharger = null })
    }

    Box(modifier = Modifier.fillMaxSize().testTag("charging_map")) {
        ChargerBrowseMap(
            chargers = chargers,
            initialCenter = initialCenter,
            onViewportChanged = { bounds ->
                if (fetchedBounds == null || browseBoundsMovedMeaningfully(bounds, fetchedBounds!!)) {
                    requestedBounds = bounds
                }
            },
            onChargerSelected = { selectedCharger = it },
            modifier = Modifier.fillMaxSize(),
        )

        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            color = EvChrome.copy(alpha = 0.96f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { retryToken += 1 }, enabled = !isLoading && OcmClient.isConfigured) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload stations", tint = EvMint)
                }
            }
        }

        BrowseStatusBanner(
            configured = OcmClient.isConfigured,
            isLoading = isLoading,
            failureMessage = failureMessage,
            stationCount = chargers.size,
            onRetry = { retryToken += 1 },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp, start = 12.dp, end = 12.dp),
        )
    }
}

@Composable
private fun BrowseStatusBanner(
    configured: Boolean,
    isLoading: Boolean,
    failureMessage: String?,
    stationCount: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = EvChrome.copy(alpha = 0.92f),
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            when {
                !configured -> Text(
                    "Add the protected Open Charge Map key to browse live stations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp, color = EvMint)
                    Spacer(Modifier.width(9.dp))
                    Text("Loading stations…", style = MaterialTheme.typography.bodySmall)
                }
                failureMessage != null -> {
                    Text(
                        failureMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TextButton(onClick = onRetry) { Text("Retry", color = EvMint) }
                }
                stationCount == 0 -> Text(
                    "No stations reported here. Pan or zoom out.",
                    style = MaterialTheme.typography.bodySmall,
                )
                else -> {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = EvMint)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$stationCount ${if (stationCount == 1) "station" else "stations"} • operating status only",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChargerBrowseDetailDialog(charger: Charger, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(charger.network.uppercase(), style = MaterialTheme.typography.labelMedium, color = EvMint)
                Text(charger.name)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("Status", charger.status.browseLabel)
                DetailLine("Power", if (charger.maxKw > 0) "${charger.maxKw} kW" else "Power not reported")
                DetailLine("Plugs", charger.connectorTypes.joinToString(", ") { it.browseLabel })
                DetailLine("Stalls", charger.numberOfStalls.toString())
                charger.usageCostText?.let { DetailLine("Cost", it) }
                Text(
                    "Station data: ${charger.dataProviderTitle ?: "Open Charge Map"}" +
                        charger.dataProviderLicense?.let { " • $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Confirm live availability, access and pricing in the operator's app before relying on this station.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = EvMint) } },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

internal fun initialBrowseBounds(center: LatLon): OcmBounds = OcmBounds(
    minLat = (center.latitude - 0.25).coerceAtLeast(-90.0),
    minLon = (center.longitude - 0.25).coerceAtLeast(-180.0),
    maxLat = (center.latitude + 0.25).coerceAtMost(90.0),
    maxLon = (center.longitude + 0.25).coerceAtMost(180.0),
)

internal fun browseBoundsMovedMeaningfully(current: OcmBounds, previous: OcmBounds): Boolean {
    val previousLatSpan = (previous.maxLat - previous.minLat).coerceAtLeast(0.0001)
    val previousLonSpan = (previous.maxLon - previous.minLon).coerceAtLeast(0.0001)
    val currentLatSpan = (current.maxLat - current.minLat).coerceAtLeast(0.0001)
    val currentLonSpan = (current.maxLon - current.minLon).coerceAtLeast(0.0001)
    val currentCenterLat = (current.minLat + current.maxLat) / 2.0
    val currentCenterLon = (current.minLon + current.maxLon) / 2.0
    val previousCenterLat = (previous.minLat + previous.maxLat) / 2.0
    val previousCenterLon = (previous.minLon + previous.maxLon) / 2.0
    return kotlin.math.abs(currentCenterLat - previousCenterLat) > previousLatSpan * 0.25 ||
        kotlin.math.abs(currentCenterLon - previousCenterLon) > previousLonSpan * 0.25 ||
        kotlin.math.abs(currentLatSpan - previousLatSpan) > previousLatSpan * 0.30 ||
        kotlin.math.abs(currentLonSpan - previousLonSpan) > previousLonSpan * 0.30
}

private val ChargerStatus.browseLabel: String
    get() = when (this) {
        ChargerStatus.AVAILABLE -> "Operational"
        ChargerStatus.BUSY -> "Busy"
        ChargerStatus.LIMITED -> "Status unknown"
        ChargerStatus.OFFLINE -> "Offline"
    }

private val ConnectorType.browseLabel: String
    get() = when (this) {
        ConnectorType.CCS -> "CCS1"
        ConnectorType.CCS2 -> "CCS2"
        ConnectorType.NACS -> "NACS/Tesla"
        ConnectorType.CHADEMO -> "CHAdeMO"
        ConnectorType.TYPE2 -> "Type 2"
        ConnectorType.J1772 -> "J1772"
    }
