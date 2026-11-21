package com.nuvio.app.features.cinesnappa

import com.nuvio.app.features.addons.AddonStorage
import com.nuvio.app.features.addons.httpGetTextWithHeaders

/** One playable entry from an M3U / M3U8 playlist. */
data class M3uChannel(
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class SavedPlaylist(val name: String, val url: String)

/** Free, public playlists from the iptv-org project on GitHub (free-to-air channels only). */
object FreePlaylists {
    const val GITHUB_URL = "https://github.com/iptv-org/iptv"
    private const val BASE = "https://iptv-org.github.io/iptv"

    val ALL: List<SavedPlaylist> = listOf(
        SavedPlaylist("UK channels", "$BASE/countries/uk.m3u"),
        SavedPlaylist("US channels", "$BASE/countries/us.m3u"),
        SavedPlaylist("Albania channels", "$BASE/countries/al.m3u"),
        SavedPlaylist("Italy channels", "$BASE/countries/it.m3u"),
        SavedPlaylist("News", "$BASE/categories/news.m3u"),
        SavedPlaylist("Movies", "$BASE/categories/movies.m3u"),
        SavedPlaylist("Sports", "$BASE/categories/sports.m3u"),
        SavedPlaylist("Kids", "$BASE/categories/kids.m3u"),
        SavedPlaylist("Music", "$BASE/categories/music.m3u"),
        SavedPlaylist("Documentary", "$BASE/categories/documentary.m3u"),
    )
}

/** Saved playlists, kept on the device. */
object M3uPlaylistStore {
    // Spare AddonStorage slot (negative ids are never real profiles). Lines are "name\turl".
    private const val SLOT = -7002

    fun load(): List<SavedPlaylist> =
        AddonStorage.loadInstalledAddonUrls(SLOT).mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            when {
                parts.size == 2 && parts[1].isNotBlank() -> SavedPlaylist(parts[0].ifBlank { parts[1] }, parts[1])
                parts.size == 1 && parts[0].isNotBlank() -> SavedPlaylist(parts[0], parts[0])
                else -> null
            }
        }

    fun save(playlists: List<SavedPlaylist>) {
        AddonStorage.saveInstalledAddonUrls(
            SLOT,
            playlists.distinctBy { it.url }.map { "${it.name.replace('\t', ' ').replace('\n', ' ')}\t${it.url.trim()}" },
        )
    }

    fun add(name: String, url: String): List<SavedPlaylist> {
        val cleanUrl = url.trim()
        val current = load().filterNot { it.url == cleanUrl }
        val updated = listOf(SavedPlaylist(name.trim().ifBlank { defaultName(cleanUrl) }, cleanUrl)) + current
        save(updated)
        return updated
    }

    fun remove(url: String): List<SavedPlaylist> {
        val updated = load().filterNot { it.url == url }
        save(updated)
        return updated
    }

    private fun defaultName(url: String): String =
        url.substringBefore('?').trimEnd('/').substringAfterLast('/').ifBlank { "Playlist" }
}

object M3uParser {
    private val attrRegex = Regex("""([a-zA-Z0-9-]+)="([^"]*)"""")

    suspend fun fetch(url: String): List<M3uChannel> {
        val text = httpGetTextWithHeaders(
            url = url.trim(),
            headers = mapOf("Accept" to "*/*", "User-Agent" to "CineSnappa"),
        )
        return parse(text)
    }

    /** Parses #EXTM3U playlists, including #EXTVLCOPT user-agent / referrer headers. A plain URL list also works. */
    fun parse(text: String): List<M3uChannel> {
        val channels = mutableListOf<M3uChannel>()
        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        val pendingHeaders = mutableMapOf<String, String>()

        text.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.isEmpty() || line.startsWith("#EXTM3U", ignoreCase = true) -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    pendingName = line.substringAfterLast(',', "").trim().ifBlank { attrs["tvg-name"] }
                    pendingLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    pendingGroup = attrs["group-title"]?.takeIf { it.isNotBlank() }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    pendingGroup = line.substringAfter(':').trim().ifBlank { null }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val option = line.substringAfter(':')
                    val key = option.substringBefore('=').trim().lowercase()
                    val value = option.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" -> pendingHeaders["User-Agent"] = value
                        "http-referrer", "http-referer" -> pendingHeaders["Referer"] = value
                        "http-origin" -> pendingHeaders["Origin"] = value
                    }
                }
                line.startsWith("#") -> Unit
                line.contains("://") -> {
                    channels += M3uChannel(
                        name = pendingName?.takeIf { it.isNotBlank() } ?: line.substringAfterLast('/').substringBefore('?'),
                        url = line,
                        logo = pendingLogo,
                        group = pendingGroup,
                        headers = pendingHeaders.toMap(),
                    )
                    pendingName = null
                    pendingLogo = null
                    pendingGroup = null
                    pendingHeaders.clear()
                }
            }
        }
        return channels
    }
}
