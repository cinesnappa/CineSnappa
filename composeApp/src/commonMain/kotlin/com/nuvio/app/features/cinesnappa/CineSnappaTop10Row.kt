package com.nuvio.app.features.cinesnappa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview

/**
 * Picks today's Top 10 from the home catalogs: Cinemeta's popular movies and
 * series interleaved, falling back to the first catalog with enough items.
 */
fun pickTop10(sections: List<HomeCatalogSection>): List<MetaPreview> {
    fun topOf(type: String): List<MetaPreview> =
        (sections.firstOrNull { it.key == "com.linvo.cinemeta:$type:top" }
            ?: sections.firstOrNull { it.key.endsWith(":$type:top") })
            ?.items
            .orEmpty()

    val movies = topOf("movie")
    val series = topOf("series")
    val mixed = buildList {
        val max = maxOf(movies.size, series.size)
        for (i in 0 until max) {
            movies.getOrNull(i)?.let(::add)
            series.getOrNull(i)?.let(::add)
        }
    }.distinctBy { "${it.type}:${it.id}" }

    val picked = if (mixed.size >= 10) mixed else sections.firstOrNull { it.items.size >= 10 }?.items.orEmpty()
    return picked.filter { !it.poster.isNullOrBlank() }.take(10)
}

/** "Top 10 Today" shelf: big outlined rank numbers with the poster tucked beside them. */
@Composable
fun CineSnappaTop10Row(
    items: List<MetaPreview>,
    modifier: Modifier = Modifier,
    sectionPadding: Dp = 16.dp,
    onItemClick: ((MetaPreview) -> Unit)? = null,
) {
    if (items.size < 3) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Top 10 Today",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = sectionPadding),
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            contentPadding = PaddingValues(horizontal = sectionPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(items, key = { _, item -> "top10:${item.type}:${item.id}" }) { index, item ->
                Top10Card(
                    rank = index + 1,
                    item = item,
                    onClick = { onItemClick?.invoke(item) },
                )
            }
        }
    }
}

@Composable
private fun Top10Card(
    rank: Int,
    item: MetaPreview,
    onClick: () -> Unit,
) {
    val numberWidth = if (rank >= 10) 96.dp else 62.dp
    Box(
        modifier = Modifier
            .width(numberWidth + PosterWidth - 18.dp)
            .height(PosterHeight)
            .clickable(onClick = onClick),
    ) {
        val numberStyle = TextStyle(
            fontSize = 132.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-8).sp,
        )
        // Dark fill + light outline, like a streaming "Top 10" badge.
        Text(
            text = rank.toString(),
            style = numberStyle,
            color = Color(0xFF0A0A0A),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = 18.dp),
        )
        Text(
            text = rank.toString(),
            style = numberStyle.copy(drawStyle = Stroke(width = 5f)),
            color = Color(0xFF9A9A9A),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(y = 18.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(PosterWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private val PosterWidth = 112.dp
private val PosterHeight = 166.dp
