package com.nuvio.app.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.build.TrailerPlaybackMode
import com.nuvio.app.features.details.HeroTrailerAudioState
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.details.components.HeroTrailerPlayerSurface
import com.nuvio.app.features.details.selectHeroTrailer
import com.nuvio.app.features.details.youtubePlaybackUrl
import com.nuvio.app.features.trailer.TrailerPlaybackResolver
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import kotlinx.coroutines.withTimeoutOrNull
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.heroStretchHeight
import com.nuvio.app.core.ui.heroStretchZoom
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private const val HERO_BACKGROUND_PARALLAX = 0.055f
private const val HERO_BACKGROUND_SCALE = 1.14f
private const val HERO_CONTENT_PARALLAX = 0.18f
private const val HERO_SCROLL_PARALLAX = 0.3f
private const val HERO_SCROLL_DOWN_SCALE_MULTIPLIER = 0.0001f
private const val HERO_SCROLL_UP_SCALE_MULTIPLIER = 0.002f
private const val HERO_SCROLL_MAX_SCALE = 1.3f
private const val HERO_SWIPE_THRESHOLD_FRACTION = 0.16f
private const val HERO_SWIPE_VELOCITY_THRESHOLD = 300f
private const val HERO_AUTO_SCROLL_INTERVAL_MS = 8_000L
private const val HOME_TRAILER_START_DELAY_MS = 1_200L
private val HERO_CARD_HORIZONTAL_MARGIN = 10.dp
private val HERO_CARD_CORNER_RADIUS = 24.dp
private const val HOME_TRAILER_RESOLVE_TIMEOUT_MS = 10_000L
private const val HOME_TRAILER_AFTER_END_DELAY_MS = 1_500L
private const val MOBILE_HERO_VIEWPORT_RATIO = 0.82f
private const val MOBILE_HERO_MIN_HEIGHT_DP = 360f
private const val MOBILE_HERO_MAX_HEIGHT_DP = 760f

internal data class HomeHeroLayout(
    val isTablet: Boolean,
    val heroHeight: Dp,
    val contentMaxWidth: Dp,
    val contentWidthFraction: Float,
    val contentHorizontalPadding: Dp,
    val contentVerticalPadding: Dp,
    val bottomFadeHeight: Dp,
    val logoWidthFraction: Float,
)

@Composable
fun HomeHeroSection(
    items: List<MetaPreview>,
    modifier: Modifier = Modifier,
    viewportHeight: Dp? = null,
    mobileBelowSectionHeightHint: Dp? = null,
    listState: LazyListState? = null,
    stretchPx: () -> Float = { 0f },
    onItemClick: ((MetaPreview) -> Unit)? = null,
    onPlayClick: ((MetaPreview) -> Unit)? = null,
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val coroutineScope = rememberCoroutineScope()
    val autoScrollPage = pagerState.currentPage

    // CineSnappa: play a muted trailer behind the hero, then move on when it finishes.
    val metaScreenSettings by remember {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val homeTrailersEnabled = AppFeaturePolicy.heroTrailerPlaybackSupported &&
        AppFeaturePolicy.trailerPlaybackMode == TrailerPlaybackMode.IN_APP &&
        metaScreenSettings.heroTrailerPlayback
    val trailerMuted by HeroTrailerAudioState.muted.collectAsStateWithLifecycle()
    val settledItem = items[pagerState.settledPage.coerceIn(items.indices)]
    var trailerSource by remember { mutableStateOf<TrailerPlaybackSource?>(null) }
    var trailerItemId by remember { mutableStateOf<String?>(null) }
    var trailerLoading by remember { mutableStateOf(false) }
    var trailerReady by remember { mutableStateOf(false) }
    var trailerEnded by remember { mutableStateOf(false) }

    LaunchedEffect(settledItem.id, homeTrailersEnabled) {
        trailerSource = null
        trailerReady = false
        trailerEnded = false
        trailerItemId = settledItem.id
        trailerLoading = homeTrailersEnabled
        if (!homeTrailersEnabled) return@LaunchedEffect
        try {
            delay(HOME_TRAILER_START_DELAY_MS)
            val source = withTimeoutOrNull(HOME_TRAILER_RESOLVE_TIMEOUT_MS) {
                val trailer = runCatching {
                    MetaDetailsRepository.fetch(settledItem.type, settledItem.id)
                        ?.trailers
                        ?.let(::selectHeroTrailer)
                }.getOrNull() ?: return@withTimeoutOrNull null
                runCatching {
                    TrailerPlaybackResolver.resolveFromYouTubeUrl(trailer.youtubePlaybackUrl())
                }.getOrNull()
            }
            if (trailerItemId == settledItem.id) {
                trailerSource = source?.takeIf { it.videoUrl.isNotBlank() }
            }
        } finally {
            trailerLoading = false
        }
    }
    val trailerHoldsPage = trailerLoading || (trailerSource != null && !trailerEnded)

    LaunchedEffect(autoScrollPage, items.size, trailerHoldsPage) {
        if (items.size <= 1 || trailerHoldsPage) return@LaunchedEffect
        delay(if (trailerEnded) HOME_TRAILER_AFTER_END_DELAY_MS else HERO_AUTO_SCROLL_INTERVAL_MS)
        while (pagerState.isScrollInProgress) {
            delay(100L)
        }

        val nextPage = (pagerState.currentPage + 1) % items.size
        coroutineScope.launch {
            pagerState.animateScrollToPage(nextPage)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .homeHeroPagerGesture(
                pagerState = pagerState,
                itemCount = items.size,
                coroutineScope = coroutineScope,
            )
            // CineSnappa: floating rounded banner card (TryBox-style).
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = HERO_CARD_HORIZONTAL_MARGIN, vertical = 8.dp)
            .clip(RoundedCornerShape(HERO_CARD_CORNER_RADIUS)),
    ) {
        val layout = homeHeroLayout(
            maxWidthDp = maxWidth.value,
            viewportHeightDp = viewportHeight?.value,
            mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHint?.value,
        )
        val heroWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heroHeightPx = with(LocalDensity.current) { layout.heroHeight.toPx() }
        val scrollOffsetPx by remember(listState, heroHeightPx) {
            derivedStateOf {
                when {
                    listState == null -> 0f
                    listState.firstVisibleItemIndex > 0 -> heroHeightPx
                    else -> listState.firstVisibleItemScrollOffset.toFloat()
                }
            }
        }
        val heroScrollScale = heroBackgroundScrollScale(scrollOffsetPx)
        val heroScrollTranslationY = heroBackgroundScrollTranslationY(scrollOffsetPx)
        val currentPage = pagerState.currentPage.coerceIn(items.indices)
        val visiblePages = listOf(
            currentPage,
            (currentPage - 1).coerceIn(items.indices),
            (currentPage + 1).coerceIn(items.indices),
        ).distinct()
            .mapNotNull { index ->
                val pageOffset = heroPageOffset(pagerState, index)
                val visibility = (1f - abs(pageOffset)).coerceIn(0f, 1f)
                if (visibility <= 0f) {
                    null
                } else {
                    HeroPageLayer(
                        page = index,
                        visibility = visibility,
                        offset = pageOffset,
                    )
                }
            }
            .sortedBy(HeroPageLayer::visibility)
        val currentItem = visiblePages
            .lastOrNull()
            ?.page
            ?.let(items::get)
            ?: items[currentPage]

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heroStretchHeight(layout.heroHeight, stretchPx),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.01f },
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }

            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(layout.heroHeight)
                        .heroStretchZoom(stretchPx),
                ) {
                    visiblePages.forEach { layer ->
                        AsyncImage(
                            model = items[layer.page].banner ?: items[layer.page].poster,
                            contentDescription = items[layer.page].name,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = layer.visibility
                                    translationX = -layer.offset * heroWidthPx * HERO_BACKGROUND_PARALLAX
                                    translationY = heroScrollTranslationY
                                    scaleX = HERO_BACKGROUND_SCALE * heroScrollScale
                                    scaleY = HERO_BACKGROUND_SCALE * heroScrollScale
                                },
                            alignment = if (layout.isTablet) Alignment.TopCenter else Alignment.Center,
                            contentScale = ContentScale.Crop,
                        )
                    }

                    val activeTrailer = trailerSource?.takeIf {
                        homeTrailersEnabled &&
                            !trailerEnded &&
                            trailerItemId == currentItem.id &&
                            !pagerState.isScrollInProgress
                    }
                    if (activeTrailer != null) {
                        val trailerAlpha by animateFloatAsState(
                            targetValue = if (trailerReady) 1f else 0f,
                            label = "homeHeroTrailerAlpha",
                        )
                        HeroTrailerPlayerSurface(
                            sourceUrl = activeTrailer.videoUrl,
                            sourceAudioUrl = activeTrailer.audioUrl?.takeIf { it.isNotBlank() },
                            playWhenReady = scrollOffsetPx < heroHeightPx * 0.6f,
                            muted = trailerMuted,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = trailerAlpha
                                    translationY = heroScrollTranslationY
                                    scaleX = HERO_BACKGROUND_SCALE * heroScrollScale
                                    scaleY = HERO_BACKGROUND_SCALE * heroScrollScale
                                },
                            onReady = { trailerReady = true },
                            onEnded = {
                                trailerReady = false
                                trailerEnded = true
                            },
                            onError = {
                                trailerReady = false
                                trailerEnded = true
                            },
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.02f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.34f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                                ),
                            ),
                        ),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(layout.bottomFadeHeight)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        ),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            horizontal = layout.contentHorizontalPadding,
                            vertical = layout.contentVerticalPadding,
                        ),
                    horizontalAlignment = if (layout.isTablet) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(layout.contentWidthFraction)
                            .widthIn(max = layout.contentMaxWidth),
                        contentAlignment = if (layout.isTablet) Alignment.CenterStart else Alignment.Center,
                    ) {
                        visiblePages.forEach { layer ->
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    alpha = layer.visibility
                                    translationX = -layer.offset * heroWidthPx * HERO_CONTENT_PARALLAX
                                },
                            ) {
                                HeroContentBlock(
                                    item = items[layer.page],
                                    layout = layout,
                                    onItemClick = onItemClick,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Play: movies go straight to the sources list; shows open their page to pick an episode.
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(40.dp))
                                .background(Color.White)
                                .clickable(enabled = onPlayClick != null || onItemClick != null) {
                                    val play = onPlayClick
                                    if (play != null && currentItem.type == "movie") {
                                        play(currentItem)
                                    } else {
                                        onItemClick?.invoke(currentItem)
                                    }
                                }
                                .padding(start = 20.dp, end = 24.dp, top = 11.dp, bottom = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = "Play",
                                color = Color.Black,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = "More Info",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(40.dp))
                                .background(Color.White.copy(alpha = 0.22f))
                                .clickable(enabled = onItemClick != null) { onItemClick?.invoke(currentItem) }
                                .padding(horizontal = 22.dp, vertical = 11.dp),
                        )
                    }

                    if (items.size > 1) {
                        Spacer(modifier = Modifier.height(if (layout.isTablet) 14.dp else 12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items.forEachIndexed { index, _ ->
                                val activeFraction = heroPageVisibility(pagerState, index)
                                Box(
                                    modifier = Modifier
                                        .clickable {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onBackground)
                                        .graphicsLayer {
                                            alpha = 0.35f + (0.57f * activeFraction)
                                        }
                                        .width(8.dp + (24.dp * activeFraction))
                                        .height(8.dp),
                                )
                            }
                        }
                    }
                }

                if (trailerReady && trailerSource != null && !trailerEnded) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = layout.contentVerticalPadding)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable { HeroTrailerAudioState.toggleMuted() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (trailerMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = if (trailerMuted) "Unmute trailer" else "Mute trailer",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class HeroPageLayer(
    val page: Int,
    val visibility: Float,
    val offset: Float,
)

private fun heroPageOffset(
    pagerState: PagerState,
    page: Int,
): Float = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

private fun heroPageVisibility(
    pagerState: PagerState,
    page: Int,
): Float {
    return (1f - abs(heroPageOffset(pagerState, page))).coerceIn(0f, 1f)
}

@Composable
fun HomeHeroReservedSpace(
    modifier: Modifier = Modifier,
    viewportHeight: Dp? = null,
    mobileBelowSectionHeightHint: Dp? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = HERO_CARD_HORIZONTAL_MARGIN, vertical = 8.dp)
            .clip(RoundedCornerShape(HERO_CARD_CORNER_RADIUS)),
    ) {
        val layout = homeHeroLayout(
            maxWidthDp = maxWidth.value,
            viewportHeightDp = viewportHeight?.value,
            mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHint?.value,
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.heroHeight),
        )
    }
}

@Composable
private fun HeroContentBlock(
    item: MetaPreview,
    layout: HomeHeroLayout,
    onItemClick: ((MetaPreview) -> Unit)?,
) {
    var logoLoadError by remember(item.type, item.id, item.logo) {
        mutableStateOf(false)
    }
    val logoUrl = item.logo?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (layout.isTablet) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (logoUrl != null && !logoLoadError) {
            AsyncImage(
                model = logoUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxWidth(layout.logoWidthFraction)
                    .aspectRatio(2.6f)
                    .clickable(enabled = onItemClick != null) {
                        onItemClick?.invoke(item)
                    },
                alignment = if (layout.isTablet) Alignment.CenterStart else Alignment.Center,
                contentScale = ContentScale.Fit,
                onError = { logoLoadError = true },
            )
        } else {
            Text(
                text = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onItemClick != null) {
                        onItemClick?.invoke(item)
                    },
                style = if (layout.isTablet) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.displaySmall
                },
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                textAlign = if (layout.isTablet) TextAlign.Start else TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (layout.isTablet) {
                Arrangement.spacedBy(8.dp, Alignment.Start)
            } else {
                Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroMetaText(text = item.type.replaceFirstChar(Char::uppercase))
            item.genres.firstOrNull()?.let { genre ->
                HeroMetaDot()
                HeroMetaText(text = genre)
            }
            item.releaseInfo?.takeIf { it.isNotBlank() }?.let { info ->
                HeroMetaDot()
                HeroMetaText(text = formatReleaseDateForDisplay(info))
            }
        }
    }
}

@Composable
private fun HeroMetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun homeHeroLayout(
    maxWidthDp: Float,
    viewportHeightDp: Float? = null,
    mobileBelowSectionHeightHintDp: Float? = null,
): HomeHeroLayout =
    when {
        maxWidthDp >= 1200f -> HomeHeroLayout(
            isTablet = true,
            heroHeight = (maxWidthDp * 0.42f).dp.coerceIn(360.dp, 440.dp),
            contentMaxWidth = 640.dp,
            contentWidthFraction = 0.56f,
            contentHorizontalPadding = 56.dp,
            contentVerticalPadding = 22.dp,
            bottomFadeHeight = 190.dp,
            logoWidthFraction = 0.58f,
        )
        maxWidthDp >= 840f -> HomeHeroLayout(
            isTablet = true,
            heroHeight = (maxWidthDp * 0.46f).dp.coerceIn(340.dp, 420.dp),
            contentMaxWidth = 560.dp,
            contentWidthFraction = 0.62f,
            contentHorizontalPadding = 40.dp,
            contentVerticalPadding = 20.dp,
            bottomFadeHeight = 180.dp,
            logoWidthFraction = 0.56f,
        )
        maxWidthDp >= 600f -> HomeHeroLayout(
            isTablet = true,
            heroHeight = (maxWidthDp * 0.58f).dp.coerceIn(320.dp, 380.dp),
            contentMaxWidth = 520.dp,
            contentWidthFraction = 0.72f,
            contentHorizontalPadding = 32.dp,
            contentVerticalPadding = 18.dp,
            bottomFadeHeight = 170.dp,
            logoWidthFraction = 0.54f,
        )
        else -> HomeHeroLayout(
            isTablet = false,
            heroHeight = mobileHeroHeight(
                maxWidthDp = maxWidthDp,
                viewportHeightDp = viewportHeightDp,
                mobileBelowSectionHeightHintDp = mobileBelowSectionHeightHintDp,
            ),
            contentMaxWidth = 480.dp,
            contentWidthFraction = 1f,
            contentHorizontalPadding = 24.dp,
            contentVerticalPadding = 16.dp,
            bottomFadeHeight = 220.dp,
            logoWidthFraction = 0.62f,
        )
    }

private fun mobileHeroHeight(
    maxWidthDp: Float,
    viewportHeightDp: Float?,
    mobileBelowSectionHeightHintDp: Float?,
): Dp {
    val viewportDrivenHeight = viewportHeightDp?.let { (it * MOBILE_HERO_VIEWPORT_RATIO).dp }
    val widthFallbackHeight = (maxWidthDp * 1.16f).dp
    val baseHeight = viewportDrivenHeight ?: widthFallbackHeight

    val maxAllowedFromViewportDp = if (viewportHeightDp != null && mobileBelowSectionHeightHintDp != null) {
        viewportHeightDp - mobileBelowSectionHeightHintDp
    } else {
        null
    }
    val cappedHeight = if (maxAllowedFromViewportDp != null) {
        val maxAllowedFromViewport = maxAllowedFromViewportDp.dp
        baseHeight.coerceAtMost(maxAllowedFromViewport)
    } else {
        baseHeight
    }
    val minHeight = if (maxAllowedFromViewportDp != null) {
        minOf(MOBILE_HERO_MIN_HEIGHT_DP, maxAllowedFromViewportDp.coerceAtLeast(0f)).dp
    } else {
        MOBILE_HERO_MIN_HEIGHT_DP.dp
    }

    return cappedHeight.coerceIn(minHeight, MOBILE_HERO_MAX_HEIGHT_DP.dp)
}

@Composable
private fun HeroMetaDot() {
    Box(
        modifier = Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)),
    )
}

private fun heroBackgroundScrollScale(scrollOffsetPx: Float): Float {
    val scaleIncrease = if (scrollOffsetPx < 0f) {
        abs(scrollOffsetPx) * HERO_SCROLL_UP_SCALE_MULTIPLIER
    } else {
        scrollOffsetPx * HERO_SCROLL_DOWN_SCALE_MULTIPLIER
    }
    return (1f + scaleIncrease).coerceAtMost(HERO_SCROLL_MAX_SCALE)
}

private fun heroBackgroundScrollTranslationY(scrollOffsetPx: Float): Float {
    return scrollOffsetPx * HERO_SCROLL_PARALLAX
}

private fun Modifier.homeHeroPagerGesture(
    pagerState: PagerState,
    itemCount: Int,
    coroutineScope: CoroutineScope,
): Modifier {
    if (itemCount <= 1) return this

    return pointerInput(pagerState, itemCount) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val widthPx = size.width.toFloat().takeIf { it > 0f } ?: return@awaitEachGesture
            val velocityTracker = VelocityTracker().apply {
                addPosition(down.uptimeMillis, down.position)
            }
            val startPage = pagerState.currentPage
            var totalDx = 0f
            var totalDy = 0f
            var dragging = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)

                if (!change.pressed) {
                    if (dragging) {
                        val targetPage = resolveHeroTargetPage(
                            startPage = startPage,
                            itemCount = itemCount,
                            totalDx = totalDx,
                            velocityX = velocityTracker.calculateVelocity().x,
                            widthPx = widthPx,
                        )
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                    break
                }

                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y

                if (!dragging) {
                    val horizontalDrag =
                        abs(totalDx) > viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy)
                    val verticalDrag =
                        abs(totalDy) > viewConfiguration.touchSlop && abs(totalDy) > abs(totalDx)

                    when {
                        verticalDrag -> break
                        horizontalDrag -> dragging = true
                        else -> continue
                    }
                }

                pagerState.dispatchRawDelta(-delta.x)
                change.consume()
            }
        }
    }
}

private fun resolveHeroTargetPage(
    startPage: Int,
    itemCount: Int,
    totalDx: Float,
    velocityX: Float,
    widthPx: Float,
): Int {
    val thresholdPassed = abs(totalDx) > widthPx * HERO_SWIPE_THRESHOLD_FRACTION ||
        abs(velocityX) > HERO_SWIPE_VELOCITY_THRESHOLD
    if (!thresholdPassed) return startPage

    val currentPage = startPage.coerceIn(0, itemCount - 1)
    return when {
        totalDx > 0f -> if (currentPage == 0) itemCount - 1 else currentPage - 1
        totalDx < 0f -> if (currentPage == itemCount - 1) 0 else currentPage + 1
        else -> currentPage
    }
}
