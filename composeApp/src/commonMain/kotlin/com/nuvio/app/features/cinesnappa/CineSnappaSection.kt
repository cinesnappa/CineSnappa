package com.nuvio.app.features.cinesnappa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** CineSnappa brand red, used for the accent bar and the Subscribe pill. */
private val CineSnappaRed = Color(0xFFE50914)

/**
 * Home-screen row showing the latest Cine Snappa Official Shorts.
 * Tapping a card opens it in the YouTube app (or the browser).
 * The row hides itself if the feed can't be loaded, so it never shows an error.
 */
@Composable
fun CineSnappaSection(
    modifier: Modifier = Modifier,
    sectionPadding: Dp = 16.dp,
) {
    val uriHandler = LocalUriHandler.current
    val videos by produceState<List<CineSnappaVideo>?>(initialValue = null) {
        value = CineSnappaFeed.latest()
    }
    val videoList = videos ?: return
    if (videoList.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sectionPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .aspectRatio(0.2f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CineSnappaRed),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CineSnappa Shorts",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Latest from ${CineSnappaBrand.CHANNEL_HANDLE}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = "Subscribe",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CineSnappaRed)
                    .clickable { runCatching { uriHandler.openUri(CineSnappaBrand.SUBSCRIBE_URL) } }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            contentPadding = PaddingValues(horizontal = sectionPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(videoList, key = { it.videoId }) { video ->
                CineSnappaShortCard(
                    video = video,
                    onClick = { runCatching { uriHandler.openUri(video.url) } },
                )
            }
            item(key = "cinesnappa_more") {
                CineSnappaMoreCard(
                    onClick = { runCatching { uriHandler.openUri(CineSnappaBrand.CHANNEL_URL) } },
                )
            }
        }
    }
}

@Composable
private fun CineSnappaShortCard(
    video: CineSnappaVideo,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun CineSnappaMoreCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .background(CineSnappaRed.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "See all\non YouTube",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
