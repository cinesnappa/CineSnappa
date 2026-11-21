package com.nuvio.app.features.cinesnappa

import com.nuvio.app.features.addons.httpGetTextWithHeaders

/**
 * CineSnappa tie-in: pulls the latest uploads from the Cine Snappa Official
 * YouTube channel using YouTube's public Atom feed (no API key needed).
 *
 * To point the row at a different channel, change [CineSnappaBrand.CHANNEL_ID].
 */
object CineSnappaBrand {
    const val CHANNEL_NAME = "Cine Snappa Official"
    const val CHANNEL_HANDLE = "@CineSnappa"
    const val CHANNEL_ID = "UC6-lcVsGttBGeOKXKLXbe1Q"
    const val CHANNEL_URL = "https://www.youtube.com/@CineSnappa/shorts"
    const val SUBSCRIBE_URL = "https://www.youtube.com/@CineSnappa?sub_confirmation=1"
    const val FEED_URL = "https://www.youtube.com/feeds/videos.xml?channel_id=$CHANNEL_ID"
    const val MAX_ITEMS = 15
}

data class CineSnappaVideo(
    val videoId: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val published: String?,
)

object CineSnappaFeed {
    private var cache: List<CineSnappaVideo>? = null

    /** Returns the latest videos, or an empty list if the feed can't be reached. */
    suspend fun latest(forceRefresh: Boolean = false): List<CineSnappaVideo> {
        if (!forceRefresh) cache?.let { return it }
        val videos = runCatching {
            parse(
                httpGetTextWithHeaders(
                    url = CineSnappaBrand.FEED_URL,
                    headers = mapOf("Accept" to "application/atom+xml, application/xml, text/xml, */*"),
                ),
            )
        }
            .getOrDefault(emptyList())
        if (videos.isNotEmpty()) cache = videos
        return videos
    }

    private val entryRegex = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
    private val videoIdRegex = Regex("<yt:videoId>(.*?)</yt:videoId>")
    private val titleRegex = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val linkRegex = Regex("<link[^>]*rel=\"alternate\"[^>]*href=\"([^\"]+)\"")
    private val publishedRegex = Regex("<published>(.*?)</published>")

    internal fun parse(xml: String): List<CineSnappaVideo> =
        entryRegex.findAll(xml).mapNotNull { match ->
            val entry = match.groupValues[1]
            val id = videoIdRegex.find(entry)?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val title = titleRegex.find(entry)?.groupValues?.get(1)?.trim()?.unescapeXml().orEmpty()
            val link = linkRegex.find(entry)?.groupValues?.get(1)?.unescapeXml()
                ?: "https://www.youtube.com/shorts/$id"
            CineSnappaVideo(
                videoId = id,
                title = title,
                url = link,
                thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                published = publishedRegex.find(entry)?.groupValues?.get(1),
            )
        }.take(CineSnappaBrand.MAX_ITEMS).toList()

    private fun String.unescapeXml(): String = this
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
