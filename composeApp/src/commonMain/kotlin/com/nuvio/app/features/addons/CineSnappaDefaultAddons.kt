package com.nuvio.app.features.addons

/**
 * CineSnappa: addons installed automatically the first time a profile opens the app.
 * Users can still disable or remove them; once removed they are not re-added.
 *
 * - Cinemeta: Stremio's official, free Movies & TV catalog and metadata addon.
 * - OpenSubtitles v3: Stremio's official subtitles addon.
 */
object CineSnappaDefaultAddons {
    val MANIFEST_URLS: List<String> = listOf(
        "https://v3-cinemeta.strem.io/manifest.json",
        "https://opensubtitles-v3.strem.io/manifest.json",
    )

    // A spare storage slot (no real profile uses a negative id) remembers which
    // profiles were already seeded, so a user who removes the defaults keeps them removed.
    private const val SEEDED_SLOT = -7001

    fun shouldSeed(profileId: Int): Boolean =
        profileId.toString() !in AddonStorage.loadInstalledAddonUrls(SEEDED_SLOT)

    fun markSeeded(profileId: Int) {
        val seeded = AddonStorage.loadInstalledAddonUrls(SEEDED_SLOT)
        if (profileId.toString() !in seeded) {
            AddonStorage.saveInstalledAddonUrls(SEEDED_SLOT, seeded + profileId.toString())
        }
    }
}
