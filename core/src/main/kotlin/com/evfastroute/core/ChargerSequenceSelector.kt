package com.evfastroute.core

// Beam-search that picks candidate charging-stop SEQUENCES from chargers projected onto the
// route. Pure and deterministic. Faithful port of iOS RouteOptimizationService
// .selectChargerSequences (objective-aware). Road-verification of each sequence happens later in
// the :app routing layer; this only decides which chargers are worth trying.

object ChargerSequenceSelector {

    private data class SelectionState(
        val selected: List<ProjectedCharger>,
        val preferenceTotal: Double,
        val geographyPenalty: Double,
    ) {
        val averagePreference: Double
            get() = if (selected.isEmpty()) 0.0 else preferenceTotal / selected.size
    }

    fun selectChargerSequences(
        projected: List<ProjectedCharger>,
        totalDistanceKm: Double,
        vehicle: Vehicle,
        currentSOC: Double,
        arrivalBufferPercent: Double,
        prefer: (Charger) -> Double,
        objective: RouteObjective,
        maxSequences: Int = 12,
    ): List<List<Charger>> {
        val socPerKm = vehicle.efficiencyKwhPerKm / maxOf(1.0, vehicle.batteryCapacityKwh) * 100
        if (socPerKm <= 0.0 || maxSequences <= 0) return emptyList()

        val orderedProjected = projected.sortedWith(
            compareBy<ProjectedCharger> { it.progressKm }
                .thenBy { it.corridorKm }
                .thenBy { it.charger.id },
        )
        val maxStops = minOf(8, orderedProjected.size)
        if (maxStops <= 0) return emptyList()

        var frontier = listOf(SelectionState(emptyList(), 0.0, 0.0))
        val completed = mutableListOf<SelectionState>()

        fun ranksBefore(lhs: SelectionState, rhs: SelectionState): Boolean {
            if (objective != RouteObjective.FEWEST_STOPS && lhs.averagePreference != rhs.averagePreference) {
                return lhs.averagePreference > rhs.averagePreference
            }
            if (lhs.geographyPenalty != rhs.geographyPenalty) {
                return lhs.geographyPenalty < rhs.geographyPenalty
            }
            val leftProgress = lhs.selected.lastOrNull()?.progressKm ?: 0.0
            val rightProgress = rhs.selected.lastOrNull()?.progressKm ?: 0.0
            if (leftProgress != rightProgress) return leftProgress > rightProgress
            return ChargerScoring.sequenceKey(lhs.selected.map { it.charger.id }) <
                ChargerScoring.sequenceKey(rhs.selected.map { it.charger.id })
        }
        val comparator = Comparator<SelectionState> { a, b ->
            when {
                ranksBefore(a, b) -> -1
                ranksBefore(b, a) -> 1
                else -> 0
            }
        }

        var depth = 0
        while (depth < maxStops) {
            depth++
            val nextByLastCharger = mutableMapOf<String, MutableList<SelectionState>>()
            for (state in frontier) {
                val previousProgress = state.selected.lastOrNull()?.progressKm ?: 0.0
                val departureSOC = if (state.selected.isEmpty()) currentSOC else 95.0
                // First leg may use the 5% emergency floor; charger-to-charger legs keep the same
                // 10% reserve the road-route builders enforce.
                val minimumArrivalSOC = if (state.selected.isEmpty()) 5.0 else 10.0
                val reachableKm = maxOf(0.0, (departureSOC - minimumArrivalSOC) / socPerKm)
                val used = state.selected.map { it.charger.id }.toSet()
                val feasible = orderedProjected.filter { candidate ->
                    candidate.progressKm > previousProgress + 1 &&
                        candidate.progressKm - previousProgress <= reachableKm &&
                        candidate.charger.id !in used
                }
                for (candidate in feasible) {
                    val delta = candidate.progressKm - previousProgress
                    val unusedReach = maxOf(0.0, reachableKm - delta)
                    val nextState = SelectionState(
                        selected = state.selected + candidate,
                        preferenceTotal = state.preferenceTotal + prefer(candidate.charger),
                        geographyPenalty = state.geographyPenalty +
                            candidate.corridorKm * 2 +
                            candidate.charger.detourMinutes.toDouble() +
                            unusedReach * 0.03,
                    )
                    val labels = nextByLastCharger.getOrPut(candidate.charger.id) { mutableListOf() }
                    labels.add(nextState)
                    labels.sortWith(comparator)
                    while (labels.size > 3) labels.removeAt(labels.size - 1)
                }
            }

            // Paths ending at the same charger share future reachability after charging; keeping a
            // few labels preserves road-validation alternatives without an exploding global beam,
            // and the sorted keys keep output deterministic across launches.
            val next = nextByLastCharger.keys.sorted().flatMap { id ->
                (nextByLastCharger[id] ?: emptyList()).sortedWith(comparator).take(3)
            }
            for (state in next) {
                val progress = state.selected.lastOrNull()?.progressKm ?: continue
                val destinationRequirement = maxOf(0.0, totalDistanceKm - progress) * socPerKm + arrivalBufferPercent
                if (95.0 >= destinationRequirement) completed.add(state)
            }
            frontier = next
            if (frontier.isEmpty()) break
        }

        val completedComparator = Comparator<SelectionState> { a, b ->
            if (objective == RouteObjective.FEWEST_STOPS && a.selected.size != b.selected.size) {
                a.selected.size.compareTo(b.selected.size)
            } else if (objective != RouteObjective.FEWEST_STOPS && a.averagePreference != b.averagePreference) {
                b.averagePreference.compareTo(a.averagePreference) // higher preference first
            } else if (a.geographyPenalty != b.geographyPenalty) {
                a.geographyPenalty.compareTo(b.geographyPenalty)
            } else if (a.selected.size != b.selected.size) {
                a.selected.size.compareTo(b.selected.size)
            } else {
                ChargerScoring.sequenceKey(a.selected.map { it.charger.id })
                    .compareTo(ChargerScoring.sequenceKey(b.selected.map { it.charger.id }))
            }
        }
        completed.sortWith(completedComparator)

        // For time/cost/reliability modes, seed with the best sequence at each feasible stop count
        // before filling with same-count alternatives; verified road metrics later decide whether
        // two short sessions beat one tapered 95% session.
        val candidates = mutableListOf<SelectionState>()
        if (objective != RouteObjective.FEWEST_STOPS) {
            for (count in completed.map { it.selected.size }.toSortedSet()) {
                completed.firstOrNull { it.selected.size == count }?.let { candidates.add(it) }
            }
        }
        candidates.addAll(completed)

        val seen = mutableSetOf<String>()
        val result = mutableListOf<List<Charger>>()
        for (state in candidates) {
            val key = ChargerScoring.sequenceKey(state.selected.map { it.charger.id })
            if (seen.add(key)) {
                result.add(state.selected.map { it.charger })
                if (result.size >= maxSequences) break
            }
        }
        return result
    }
}
