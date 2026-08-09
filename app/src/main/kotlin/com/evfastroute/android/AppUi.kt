package com.evfastroute.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.RouteOption

@Composable
fun PlannerApp(vm: TripViewModel = viewModel()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("EV FastRoute", style = MaterialTheme.typography.headlineMedium) }

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

        items(vm.options) { option ->
            RouteCard(option)
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
private fun RouteCard(option: RouteOption) {
    Card(modifier = Modifier.fillMaxWidth()) {
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

private fun formatMinutes(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    return "${minutes / 60}h ${minutes % 60}m"
}
