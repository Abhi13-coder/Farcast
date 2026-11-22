package com.farcast.agent.darkalgorithm.features

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * #p-keys. The website stores these in plain `localStorage` — readable by
 * any XSS, any browser extension, anyone with the machine unlocked. Android
 * gives us something genuinely better for free: EncryptedSharedPreferences,
 * backed by the Android Keystore (hardware-backed on most devices), so this
 * is another upgrade-not-port, same as CaseDatabase.
 */
class SecureKeyVault(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "farcast_api_keys",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(service: String, key: String) = prefs.edit().putString(service, key).apply()
    fun get(service: String): String? = prefs.getString(service, null)
    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        const val URLSCAN = "urlscan"
        const val ABUSEIPDB = "abuse"
        const val WINDY = "windy"
        const val GOOGLE_VISION = "google_vision"

        /** Matches the site's own "keyless" note (index.html #p-keys hint) —
         *  these never need a key, nothing to store for them. */
        val KEYLESS_SERVICES = listOf(
            "Aviation (adsb.lol fallback)", "CelesTrak", "USGS", "ISS",
            "TfL cams", "GeoJS", "Shodan InternetDB", "URLhaus", "ThreatFox",
            "GreyNoise community", "crt.sh", "RDAP", "DNS (Google/Cloudflare/Quad9)", "NASA GIBS"
        )
    }
}
