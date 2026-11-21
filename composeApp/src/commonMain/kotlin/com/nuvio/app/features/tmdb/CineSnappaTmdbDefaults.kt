package com.nuvio.app.features.tmdb

/**
 * CineSnappa: built-in TMDB key so artwork, trailers and cast work with no setup.
 * The value is injected at build time from the TMDB_API_KEY GitHub secret
 * (see .github/workflows/build-apk.yml) and stays empty in the source code.
 * A key the user enters in Settings > TMDB always takes priority.
 */
internal object CineSnappaTmdbDefaults {
    const val API_KEY = "__CINESNAPPA_TMDB_API_KEY__"

    val apiKey: String
        get() = API_KEY.takeUnless { it.startsWith("__") && it.endsWith("__") }.orEmpty()
}
