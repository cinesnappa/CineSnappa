package com.nuvio.app.features.cinesnappa

import com.nuvio.app.features.debrid.DebridProviders

/** Sign-up links shown under Settings > Debrid. Referral links earn CineSnappa a commission. */
object CineSnappaDebridSignup {
    data class Link(val url: String, val isReferral: Boolean)

    private val links: Map<String, Link> = mapOf(
        DebridProviders.TORBOX_ID to Link(
            url = "https://torbox.app/subscription?referral=9631e913-766d-4d68-8ee3-0ebb4ebbc76a",
            isReferral = true,
        ),
        DebridProviders.REAL_DEBRID_ID to Link(
            url = "https://real-debrid.com/?id=3161467",
            isReferral = true,
        ),
        DebridProviders.ALL_DEBRID_ID to Link(
            url = "https://alldebrid.com/register/",
            isReferral = false,
        ),
        DebridProviders.PREMIUMIZE_ID to Link(
            url = "https://www.premiumize.me/register",
            isReferral = false,
        ),
    )

    fun linkFor(providerId: String): Link? = links[providerId]

    const val DISCLOSURE =
        "Some sign-up links are referral links: CineSnappa may earn a commission at no extra cost to you."
}

/** Free, legal addons offered in the Addons screen. One tap installs them. */
object CineSnappaRecommendedAddons {
    data class Addon(val name: String, val description: String, val manifestUrl: String)

    val ALL: List<Addon> = listOf(
        Addon(
            name = "Cinemeta",
            description = "Official Movies & TV catalogs and details",
            manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
        ),
        Addon(
            name = "WatchHub",
            description = "Shows where to watch legally (Netflix, Prime, Disney+ and more)",
            manifestUrl = "https://watchhub.strem.io/manifest.json",
        ),
        Addon(
            name = "YouTube",
            description = "Your favourite YouTube channels, with new-upload alerts",
            manifestUrl = "https://v3-channels.strem.io/manifest.json",
        ),
        Addon(
            name = "OpenSubtitles",
            description = "Subtitles in dozens of languages",
            manifestUrl = "https://opensubtitles-v3.strem.io/manifest.json",
        ),
    )
}
