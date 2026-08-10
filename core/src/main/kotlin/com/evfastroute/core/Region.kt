package com.evfastroute.core

// Single source of truth for everything region-dependent: display name, currency, default units,
// and connector standards. Faithful port of the iOS Region enum (the fields the Android app uses).

enum class Region(val code: String, val displayName: String) {
    US("US", "United States"),
    CA("CA", "Canada"),
    GB("GB", "United Kingdom"),
    DE("DE", "Germany"),
    FR("FR", "France"),
    NL("NL", "Netherlands"),
    NO("NO", "Norway"),
    SE("SE", "Sweden"),
    CH("CH", "Switzerland"),
    IT("IT", "Italy"),
    ES("ES", "Spain");

    /** Regional-indicator emoji built from the ISO code (US → 🇺🇸). */
    val flag: String get() = buildString { code.forEach { c -> appendCodePoint(127397 + c.code) } }

    /** Currency symbol used for per-kWh pricing display. */
    val currencySymbol: String get() = when (this) {
        US, CA -> "$"
        GB -> "£"
        DE, FR, NL, IT, ES -> "€"
        NO, SE -> "kr"
        CH -> "CHF"
    }

    /** Whether the region conventionally uses miles for road distances. */
    val usesImperialByDefault: Boolean get() = this == US || this == GB

    val isEuropean: Boolean get() = when (this) {
        US, CA -> false
        else -> true
    }

    /** Primary DC connector standards for the region (used to seed a new vehicle). */
    val defaultConnectors: List<ConnectorType> get() = when (this) {
        US, CA -> listOf(ConnectorType.CCS, ConnectorType.NACS)
        else -> listOf(ConnectorType.CCS2, ConnectorType.TYPE2)
    }

    /** Charging networks used to seed a fresh regional profile. Keep this byte-for-byte aligned
     * with iOS `Region.defaultNetworks`: preferred-network scoring can otherwise select a
     * different charging sequence for the same trip. */
    val defaultNetworks: Set<String> get() = when (this) {
        US, CA -> setOf("Tesla Supercharger", "Electrify America", "ChargePoint", "EVgo")
        GB -> setOf("Tesla", "Ionity", "Shell Recharge", "Fastned", "InstaVolt", "Gridserve")
        DE, FR, NL -> setOf("Ionity", "Fastned", "Allego", "Tesla", "Shell Recharge")
        NO, SE -> setOf("Ionity", "Tesla", "Fastned", "Allego", "Recharge")
        CH -> setOf("Ionity", "Tesla", "Shell Recharge", "Fastned")
        IT, ES -> setOf("Ionity", "Tesla", "Enel X", "Iberdrola", "Allego")
    }

    /** Regional search fallback used only until the device supplies a location or trip anchor. */
    val searchCenter: LatLon get() = when (this) {
        US -> LatLon(39.8283, -98.5795)
        CA -> LatLon(45.4215, -75.6972)
        GB -> LatLon(52.3555, -1.1743)
        DE -> LatLon(51.1657, 10.4515)
        FR -> LatLon(46.2276, 2.2137)
        NL -> LatLon(52.1326, 5.2913)
        NO -> LatLon(60.4720, 8.4689)
        SE -> LatLon(60.1282, 18.6435)
        CH -> LatLon(46.8182, 8.2275)
        IT -> LatLon(41.8719, 12.5674)
        ES -> LatLon(40.4637, -3.7492)
    }

    companion object {
        /** Maps an ISO 3166-1 alpha-2 code to a supported Region, defaulting to US. */
        fun from(isoCountryCode: String?): Region =
            isoCountryCode?.let { code -> entries.firstOrNull { it.code == code.uppercase() } } ?: US

        fun supports(isoCountryCode: String?): Boolean =
            isoCountryCode == null || entries.any { it.code == isoCountryCode.uppercase() }
    }
}

/** Distance formatting in the user's chosen units. Mirrors iOS SettingsService.formatDistance. */
object Units {
    fun label(usesMiles: Boolean): String = if (usesMiles) "mi" else "km"

    fun formatDistance(km: Double, usesMiles: Boolean): String =
        if (usesMiles) "${(km * 0.62137).toInt()} mi" else "${km.toInt()} km"

    fun formatConsumption(kwhPer100Km: Double, usesMiles: Boolean): String =
        if (usesMiles) {
            String.format(java.util.Locale.US, "%.1f kWh/100 mi", kwhPer100Km * 1.60934)
        } else {
            String.format(java.util.Locale.US, "%.1f kWh/100 km", kwhPer100Km)
        }
}
