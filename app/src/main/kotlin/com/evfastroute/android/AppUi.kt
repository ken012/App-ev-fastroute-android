package com.evfastroute.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evfastroute.android.map.RouteMap
import com.evfastroute.android.nav.NavLauncher
import com.evfastroute.core.EvCatalog
import com.evfastroute.core.EvPreset
import com.evfastroute.core.LatLon
import com.evfastroute.core.NavigationApp
import com.evfastroute.core.NavigationLinks
import com.evfastroute.core.NavigationPoint
import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.RouteOption

@Composable
fun PlannerApp(vm: TripViewModel = viewModel()) {
    if (vm.isPickingVehicle) {
        VehiclePicker(
            current = vm.selectedPreset,
            onSelect = vm::selectPreset,
            onClose = vm::hideVehiclePicker,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("EV FastRoute", style = MaterialTheme.typography.headlineMedium) }

        item { VehicleCard(preset = vm.selectedPreset, onClick = vm::showVehiclePicker) }

        item {
            AddressField(
                label = "Start",
                text = vm.startText,
                suggestions = vm.startSuggestions,
                onTextChange = vm::onStartTextChange,
                onSelect = vm::selectStart,
            )
        }
        item {
            AddressField(
                label = "Destination",
                text = vm.destinationText,
                suggestions = vm.destinationSuggestions,
                onTextChange = vm::onDestinationTextChange,
                onSelect = vm::selectDestination,
            )
        }

        item {
            SliderRow("Current battery", vm.currentSocPercent, 5f..100f) { vm.currentSocPercent = it }
        }
        item {
            SliderRow("Arrival buffer", vm.arrivalBufferPercent, 5f..40f) { vm.arrivalBufferPercent = it }
        }

        item {
            Button(
                onClick = vm::plan,
                enabled = !vm.isPlanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (vm.isPlanning) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("Planning…")
                } else {
                    Text("Find Route")
                }
            }
        }

        vm.errorMessage?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        vm.selectedOption?.let { selected ->
            item {
                RouteMap(
                    routeGeometry = selected.geometry,
                    chargers = selected.chargingStops.map { LatLon(it.latitude, it.longitude) },
                    start = vm.start?.let { LatLon(it.latitude, it.longitude) },
                    destination = vm.destination?.let { LatLon(it.latitude, it.longitude) },
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
            }
            vm.destination?.let { dest ->
                item { DirectionsRow(option = selected, destination = dest) }
            }
        }

        itemsIndexed(vm.options) { index, option ->
            RouteCard(option, selected = index == vm.selectedIndex, onClick = { vm.selectOption(index) })
        }
    }
}

@Composable
private fun AddressField(
    label: String,
    text: String,
    suggestions: List<PlaceCandidate>,
    onTextChange: (String) -> Unit,
    onSelect: (PlaceCandidate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        suggestions.forEach { candidate ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(candidate) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Text(candidate.placeName, style = MaterialTheme.typography.bodyLarge)
                if (candidate.fullAddress.isNotBlank()) {
                    Text(
                        candidate.fullAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${value.toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun RouteCard(option: RouteOption, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(option.title, style = MaterialTheme.typography.titleMedium)
                Text(formatMinutes(option.totalEtaMinutes), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "${option.chargingStops.size} charge stop${if (option.chargingStops.size == 1) "" else "s"} · " +
                    "${option.drivingMinutes}m drive + ${option.chargingMinutes}m charge · arrive ${option.arrivalBatteryPercent}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            option.estimatedChargingCostValue?.takeIf { it > 0 }?.let { cost ->
                Text("Est. charging cost ~${"%.2f".format(cost)}", style = MaterialTheme.typography.bodySmall)
            }
            option.itinerary.forEach { stop ->
                Text(
                    "• ${stop.name} — ${formatMinutes(stop.arrivalMinutesFromStart)} in, ${stop.arrivalBatteryPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DirectionsRow(option: RouteOption, destination: PlaceCandidate) {
    val context = LocalContext.current
    var note by remember(option, destination) { mutableStateOf<String?>(null) }

    val stops = option.chargingStops.map {
        NavigationPoint(it.latitude, it.longitude, it.name, NavigationPoint.Kind.CHARGING)
    }
    val destPoint = NavigationPoint(
        destination.latitude, destination.longitude, destination.placeName, NavigationPoint.Kind.DESTINATION,
    )

    fun launch(app: NavigationApp) {
        val plan = NavigationLinks.handoff(app, origin = null, stops = stops, destination = destPoint)
        if (plan == null) { note = "Couldn't build a ${app.displayName} link."; return }
        val opened = NavLauncher.open(context, plan.url)
        note = if (!opened) "No app on this device could open ${app.displayName}." else plan.note
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Directions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { launch(NavigationApp.GOOGLE_MAPS) }, modifier = Modifier.weight(1f)) { Text("Google") }
            OutlinedButton(onClick = { launch(NavigationApp.WAZE) }, modifier = Modifier.weight(1f)) { Text("Waze") }
            OutlinedButton(onClick = { launch(NavigationApp.DEFAULT) }, modifier = Modifier.weight(1f)) { Text("Default") }
        }
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VehicleCard(preset: EvPreset, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Vehicle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Change", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(preset.displayName, style = MaterialTheme.typography.titleMedium)
            Text(presetSpecLine(preset), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VehiclePicker(current: EvPreset, onSelect: (EvPreset) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { EvCatalog.search(query) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Choose your car", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onClose) { Text("Close") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search ${EvCatalog.makeCount} makes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (results.isEmpty()) {
                item {
                    Text(
                        "No matching vehicles. Try a make or model.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            itemsIndexed(results) { _, preset ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(preset) },
                    colors = if (preset.catalogIdentifier == current.catalogIdentifier) {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        CardDefaults.cardColors()
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(preset.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(presetSpecLine(preset), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun presetSpecLine(preset: EvPreset): String {
    val battery = "${trimDecimal(preset.batteryCapacityKwh)} kWh"
    val power = "${preset.maxDcChargingKw} kW DC"
    val connectors = preset.connectorTypes.joinToString("/") { it.name }
    val range = preset.ratedRangeKm?.let { " · ~${it.toInt()} km" } ?: ""
    return "$battery · $power · $connectors$range"
}

private fun trimDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun formatMinutes(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    return "${minutes / 60}h ${minutes % 60}m"
}
