package com.nuvio.app.features.cinesnappa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSurfaceCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val MAX_VISIBLE_CHANNELS = 400

/**
 * CineSnappa Live TV: paste an M3U / M3U8 playlist link (or pick a free one from
 * iptv-org on GitHub), browse its channels and play them in the app's player.
 */
@Composable
fun LiveTvScreen(
    onBack: () -> Unit,
    onPlayChannel: (M3uChannel) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var saved by remember { mutableStateOf(M3uPlaylistStore.load()) }
    var urlInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var active by remember { mutableStateOf<SavedPlaylist?>(null) }
    var channels by remember { mutableStateOf<List<M3uChannel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }

    fun open(playlist: SavedPlaylist) {
        active = playlist
        channels = emptyList()
        group = null
        search = ""
        message = null
        loading = true
        scope.launch {
            try {
                val result = M3uParser.fetch(playlist.url)
                channels = result
                message = if (result.isEmpty()) "No playable channels found in this playlist." else null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message = "Couldn't load this playlist. Check the link and your connection."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (active == null) saved.firstOrNull()?.let(::open)
    }

    val groups = remember(channels) { channels.mapNotNull { it.group }.distinct().sorted() }
    val filtered = remember(channels, search, group) {
        channels.filter { channel ->
            (group == null || channel.group == group) &&
                (search.isBlank() || channel.name.contains(search.trim(), ignoreCase = true))
        }
    }

    NuvioScreen(modifier = Modifier.fillMaxSize()) {
        stickyHeader {
            NuvioScreenHeader(title = "Live TV (M3U)", onBack = onBack)
        }

        item {
            NuvioSurfaceCard {
                Text("Add a playlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                NuvioInputField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = "Paste an M3U / M3U8 link",
                    trailingContent = {
                        Text(
                            text = "Paste",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clickable { clipboard.getText()?.text?.let { urlInput = it.trim() } }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                NuvioInputField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = "Name (optional)",
                )
                Spacer(Modifier.height(12.dp))
                NuvioPrimaryButton(
                    text = "Add & open",
                    enabled = urlInput.contains("://"),
                    onClick = {
                        val url = urlInput.trim()
                        saved = M3uPlaylistStore.add(nameInput, url)
                        urlInput = ""
                        nameInput = ""
                        saved.firstOrNull { it.url == url }?.let(::open)
                    },
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Text("Free playlists (iptv-org on GitHub)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Publicly available free-to-air channels. Some may be offline or geo-blocked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ChipRow(
                labels = FreePlaylists.ALL.map { it.name } + "More on GitHub ↗",
                selected = active?.let { a -> FreePlaylists.ALL.firstOrNull { it.url == a.url }?.name },
                onClick = { label ->
                    val preset = FreePlaylists.ALL.firstOrNull { it.name == label }
                    if (preset == null) {
                        runCatching { uriHandler.openUri(FreePlaylists.GITHUB_URL) }
                    } else {
                        saved = M3uPlaylistStore.add(preset.name, preset.url)
                        open(preset)
                    }
                },
            )
        }

        if (saved.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                Text("My playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ChipRow(
                    labels = saved.map { it.name },
                    selected = active?.let { a -> saved.firstOrNull { it.url == a.url }?.name },
                    onClick = { label -> saved.firstOrNull { it.name == label }?.let(::open) },
                )
                active?.let { current ->
                    Text(
                        text = "Remove \"${current.name}\"",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable {
                                saved = M3uPlaylistStore.remove(current.url)
                                active = null
                                channels = emptyList()
                            }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }

        active?.let { current ->
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (loading) "Loading ${current.name}…" else "${current.name} · ${channels.size} channels",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (channels.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    NuvioInputField(value = search, onValueChange = { search = it }, placeholder = "Search channels")
                    if (groups.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        ChipRow(
                            labels = listOf("All") + groups,
                            selected = group ?: "All",
                            onClick = { label -> group = if (label == "All") null else label },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            items(filtered.take(MAX_VISIBLE_CHANNELS), key = { "${it.url}|${it.name}" }) { channel ->
                ChannelRow(channel = channel, onClick = { onPlayChannel(channel) })
            }
            if (filtered.size > MAX_VISIBLE_CHANNELS) {
                item {
                    Text(
                        "Showing the first $MAX_VISIBLE_CHANNELS of ${filtered.size}. Search or pick a group to narrow it down.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun ChipRow(
    labels: List<String>,
    selected: String?,
    onClick: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(labels, key = { it }) { label ->
            val isSelected = label == selected
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.10f))
                    .clickable { onClick(label) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ChannelRow(
    channel: M3uChannel,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(channel.name.take(2).uppercase(), style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            channel.group?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Text("▶", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
    }
}

/** Home-screen entry card for Live TV. */
@Composable
fun LiveTvEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sectionPadding: androidx.compose.ui.unit.Dp = 16.dp,
) {
    Row(
        modifier = modifier
            .padding(horizontal = sectionPadding)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color(0xFF3A0508), Color(0xFF140203)),
                ),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📺", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Live TV", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Play M3U playlists or free channels",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    }
}

/** Material "live_tv" glyph, used for the Live TV tab in the menu bar. */
val LiveTvIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "LiveTv",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.addPathNodes(
            "M21,6h-7.59l3.29,-3.29L16,2l-4,4 -4,-4 -0.71,0.71L10.59,6H3c-1.1,0 -2,0.89 -2,2v12c0,1.1 0.9,2 2,2h18" +
                "c1.1,0 2,-0.9 2,-2V8c0,-1.11 -0.9,-2 -2,-2zM21,20H3V8h18v12zM9,10v8l7,-4z",
        ),
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
    ).build()
}
