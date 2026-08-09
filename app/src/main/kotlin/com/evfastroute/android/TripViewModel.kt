package com.evfastroute.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evfastroute.android.net.PhotonClient
import com.evfastroute.core.ConnectorType
import com.evfastroute.core.LatLon
import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.PlaceRanker
import com.evfastroute.core.RouteOption
import com.evfastroute.core.Vehicle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TripViewModel : ViewModel() {

    // A sensible default vehicle until a garage/catalog screen exists (parity item for later).
    private val vehicle = Vehicle(
        batteryCapacityKwh = 75.0,
        efficiencyKwhPerKm = 0.17,
        maxDcChargingKw = 250,
        connectorTypes = listOf(ConnectorType.CCS, ConnectorType.CCS2, ConnectorType.NACS),
    )
    private val planner = TripPlanner()

    var startText by mutableStateOf("")
        private set
    var destinationText by mutableStateOf("")
        private set
    var start: PlaceCandidate? by mutableStateOf(null)
        private set
    var destination: PlaceCandidate? by mutableStateOf(null)
        private set

    var startSuggestions by mutableStateOf<List<PlaceCandidate>>(emptyList())
        private set
    var destinationSuggestions by mutableStateOf<List<PlaceCandidate>>(emptyList())
        private set

    var currentSocPercent by mutableStateOf(80f)
    var arrivalBufferPercent by mutableStateOf(10f)

    var options by mutableStateOf<List<RouteOption>>(emptyList())
        private set
    var selectedIndex by mutableStateOf(0)
        private set
    val selectedOption: RouteOption?
        get() = options.getOrNull(selectedIndex)
    var isPlanning by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set

    private var startSearchJob: Job? = null
    private var destinationSearchJob: Job? = null

    fun onStartTextChange(text: String) {
        startText = text
        start = null
        startSearchJob?.cancel()
        if (text.isBlank()) { startSuggestions = emptyList(); return }
        startSearchJob = viewModelScope.launch {
            startSuggestions = search(text)
        }
    }

    fun onDestinationTextChange(text: String) {
        destinationText = text
        destination = null
        destinationSearchJob?.cancel()
        if (text.isBlank()) { destinationSuggestions = emptyList(); return }
        destinationSearchJob = viewModelScope.launch {
            destinationSuggestions = search(text)
        }
    }

    fun selectStart(candidate: PlaceCandidate) {
        start = candidate
        startText = candidate.placeName
        startSuggestions = emptyList()
    }

    fun selectDestination(candidate: PlaceCandidate) {
        destination = candidate
        destinationText = candidate.placeName
        destinationSuggestions = emptyList()
    }

    fun selectOption(index: Int) {
        if (index in options.indices) selectedIndex = index
    }

    fun plan() {
        val from = start
        val to = destination
        if (from == null || to == null) {
            errorMessage = "Pick a start and destination from search."
            return
        }
        if (isPlanning) return
        isPlanning = true
        errorMessage = null
        options = emptyList()
        viewModelScope.launch {
            val result = planner.plan(
                start = LatLon(from.latitude, from.longitude),
                destination = LatLon(to.latitude, to.longitude),
                vehicle = vehicle,
                currentSOC = currentSocPercent.toDouble(),
                arrivalBufferPercent = arrivalBufferPercent.toDouble(),
            )
            when (result) {
                is TripPlanner.Result.Success -> {
                    options = result.options
                    selectedIndex = 0
                }
                is TripPlanner.Result.Error -> errorMessage = result.message
            }
            isPlanning = false
        }
    }

    private suspend fun search(query: String): List<PlaceCandidate> {
        val anchor = start ?: destination
        val raw = PhotonClient.search(query, anchor?.latitude, anchor?.longitude)
        return PlaceRanker.rank(raw, query, haveAnchor = anchor != null).take(6)
    }
}
