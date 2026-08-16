package com.example.ui.components

/**
 * Central place for country-aware tax planning. Only India has a real
 * calculator implemented right now (Old vs. New regime, Section 80C/80D,
 * 87A rebate). Every other country shows a "coming soon" state instead of
 * silently applying Indian tax law to a non-Indian user's numbers.
 *
 * To add real support for another country later: implement its slab logic
 * (see calculateOldRegimeTax/calculateNewRegimeTax in TaxPlannerTab.kt for
 * the India pattern), then add its code to `supportedCountries` with
 * `isImplemented = true`.
 */
object TaxCountryHelper {
    data class TaxCountryOption(
        val code: String,
        val displayName: String,
        val flag: String,
        val isImplemented: Boolean
    )

    val supportedCountries = listOf(
        TaxCountryOption("IN", "India", "🇮🇳", isImplemented = true),
        TaxCountryOption("US", "United States", "🇺🇸", isImplemented = false),
        TaxCountryOption("GB", "United Kingdom", "🇬🇧", isImplemented = false),
        TaxCountryOption("DE", "Germany", "🇩🇪", isImplemented = false),
        TaxCountryOption("FR", "France", "🇫🇷", isImplemented = false),
        TaxCountryOption("CA", "Canada", "🇨🇦", isImplemented = false),
        TaxCountryOption("AU", "Australia", "🇦🇺", isImplemented = false),
        TaxCountryOption("AE", "United Arab Emirates", "🇦🇪", isImplemented = false),
        TaxCountryOption("SG", "Singapore", "🇸🇬", isImplemented = false)
    )

    private val fallback = TaxCountryOption("OTHER", "Your Region", "🌍", isImplemented = false)

    fun optionFor(code: String): TaxCountryOption {
        return supportedCountries.find { it.code == code } ?: fallback
    }
}
