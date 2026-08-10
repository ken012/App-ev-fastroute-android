package com.evfastroute.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evfastroute.android.nav.NavLauncher
import com.evfastroute.core.NavigationApp
import com.evfastroute.core.NavigationLinks
import com.evfastroute.core.NavigationPoint
import com.evfastroute.core.NavigationSession
import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.RouteOption
import com.evfastroute.core.orderedNavigationPoints

@Composable
internal fun SavedTripsCard(
    trips: List<SavedTripSnapshot>,
    message: String?,
    onSave: () -> Unit,
    onLoad: (SavedTripSnapshot) -> Unit,
    onDelete: (SavedTripSnapshot) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Saved trips", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onSave) { Text("Save current") }
            }
            trips.forEach { trip ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onLoad(trip) }, modifier = Modifier.weight(1f)) {
                        Text(trip.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { onDelete(trip) }) { Text("Remove") }
                }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun DirectionsRow(
    option: RouteOption,
    start: PlaceCandidate?,
    destination: PlaceCandidate,
    preferredNav: NavigationApp,
    onStartGuided: () -> Unit,
) {
    val context = LocalContext.current
    var note by remember(option, destination) { mutableStateOf<String?>(null) }
    val stops = option.orderedNavigationPoints()
    val destinationPoint = NavigationPoint(
        destination.latitude, destination.longitude, destination.placeName, NavigationPoint.Kind.DESTINATION,
    )
    val originPoint = start?.let {
        NavigationPoint(it.latitude, it.longitude, it.placeName, NavigationPoint.Kind.VISIT)
    }

    fun launch(app: NavigationApp) {
        val plan = NavigationLinks.handoff(app, originPoint, stops, destinationPoint)
        if (plan == null) {
            note = "Couldn't build a ${app.displayName} link."
            return
        }
        note = if (NavLauncher.open(context, plan.url)) plan.note
        else "No app on this device could open ${app.displayName}."
    }

    val apps = listOf(preferredNav) + (NavigationApp.entries - preferredNav)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Directions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.forEach { app ->
                if (app == preferredNav) {
                    Button(onClick = { launch(app) }, modifier = Modifier.weight(1f)) { Text(shortNavLabel(app)) }
                } else {
                    OutlinedButton(onClick = { launch(app) }, modifier = Modifier.weight(1f)) { Text(shortNavLabel(app)) }
                }
            }
        }
        Text(
            NavigationLinks.actionLabel(preferredNav, intermediateStopCount = stops.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (stops.isNotEmpty()) {
            OutlinedButton(onClick = onStartGuided, modifier = Modifier.fillMaxWidth()) {
                Text("Start guided trip (stop by stop)")
            }
        }
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun GuidedTripBanner(
    session: NavigationSession,
    arrivalSuggested: Boolean,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onHandoffRecorded: () -> Unit,
    onArrived: () -> Unit,
    onEnd: () -> Unit,
) {
    val context = LocalContext.current
    val point = session.currentPoint ?: return
    val position = session.completedPointCount + 1
    val isFinal = session.remainingPointCount <= 1

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Guided trip · stop $position of ${session.totalPointCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Next: ${point.name}", style = MaterialTheme.typography.titleMedium)
            if (arrivalSuggested) {
                Text("You seem to have arrived at ${point.name}. Confirm to continue.")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val plan = NavigationLinks.handoff(session.app, null, emptyList(), point)
                        if (plan != null && NavLauncher.open(context, plan.url)) onHandoffRecorded()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Open in ${session.app.displayName}") }
                if (arrivalSuggested) {
                    Button(onClick = onArrived, modifier = Modifier.weight(1f)) {
                        Text(if (isFinal) "Arrived" else "Arrived · next")
                    }
                } else {
                    OutlinedButton(onClick = onArrived, modifier = Modifier.weight(1f)) {
                        Text(if (isFinal) "Arrived" else "Arrived · next")
                    }
                }
            }
            if (!hasLocationPermission) {
                TextButton(onClick = onRequestLocationPermission) {
                    Text("Enable location for automatic arrival")
                }
            }
            TextButton(onClick = onEnd) { Text("End guided trip") }
        }
    }
}

private fun shortNavLabel(app: NavigationApp): String = when (app) {
    NavigationApp.GOOGLE_MAPS -> "Google"
    NavigationApp.WAZE -> "Waze"
    NavigationApp.DEFAULT -> "Default"
}
