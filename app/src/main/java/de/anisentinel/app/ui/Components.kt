package de.anisentinel.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import de.anisentinel.app.data.image.CoverImageLoader
import de.anisentinel.app.domain.watcher.ReleaseStatusResolver
import de.anisentinel.app.domain.watcher.ReleaseDateClassifier
import de.anisentinel.app.domain.watcher.ReleaseDateRelation
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.Duration
import java.net.URL
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import de.anisentinel.app.R
import de.anisentinel.app.domain.model.Anime
import de.anisentinel.app.domain.model.ReleaseStatus
import de.anisentinel.app.ui.theme.Cyan400
import de.anisentinel.app.ui.theme.Orange400
import de.anisentinel.app.ui.theme.Red400
import de.anisentinel.app.ui.theme.Teal400
import de.anisentinel.app.ui.theme.Violet400
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AppHeader(onMenu: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.testTag(UiTags.MENU)) {
            Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.open_menu))
        }
        Icon(
            Icons.Outlined.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(34.dp)
        )
        Text(
            text = "Ani",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
        Text(
            text = "Sentinel",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        SentinelAvatar()
    }
}

@Composable
private fun SentinelAvatar() {
    val transition = rememberInfiniteTransition(label = "sentinel pulse")
    val glow by transition.animateFloat(
        initialValue = .45f,
        targetValue = .9f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "avatar glow"
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Cyan400.copy(alpha = glow), Violet400.copy(alpha = glow))
                )
            )
            .padding(2.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = stringResource(R.string.selected_sentinel),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun PageTitle(title: String, subtitle: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() }
    )
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(20.dp))
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (actionLabel != null && onActionClick != null) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onActionClick) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun AnimeCard(
    anime: Anime,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    auxiliaryLabel: String? = null,
    postponements: List<de.anisentinel.app.data.local.ReleasePostponementEntity> = emptyList(),
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .testTag(UiTags.ANIME_CARD_PREFIX + anime.id)
            .then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimeCover(
                anime,
                Modifier.size(
                    width = if (compact) 72.dp else 92.dp,
                    height = if (compact) 92.dp else 124.dp
                )
            )
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    anime.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                CompactPostponementNotice(postponements)
                Text(
                    stringResource(R.string.episode_number, anime.episode),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                auxiliaryLabel?.takeIf { it.isNotBlank() }?.let { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                val zonedRelease = anime.expectedReleaseAt?.atZone(ZoneId.systemDefault())
                val releaseText = if (zonedRelease == null) {
                    stringResource(R.string.no_next_release)
                } else if (anime.releaseTimePrecision == "DATE") {
                    zonedRelease.toLocalDate().format(
                        DateTimeFormatter.ofPattern("dd.MM.yyyy", LocalConfiguration.current.locales[0])
                    )
                } else {
                    val time = zonedRelease.format(DateTimeFormatter.ofPattern("HH:mm"))
                    when (
                        ReleaseDateClassifier.classify(
                            zonedRelease.toLocalDate(),
                            java.time.LocalDate.now()
                        )
                    ) {
                        ReleaseDateRelation.TODAY -> stringResource(R.string.release_today, time)
                        ReleaseDateRelation.TOMORROW -> stringResource(R.string.release_tomorrow, time)
                        ReleaseDateRelation.NEXT_WEEK -> stringResource(
                            R.string.release_next_week,
                            zonedRelease.dayOfWeek.getDisplayName(
                                java.time.format.TextStyle.FULL,
                                LocalConfiguration.current.locales[0]
                            ),
                            time
                        )
                        else -> zonedRelease.format(
                            DateTimeFormatter.ofPattern(
                                stringResource(R.string.release_date_time_pattern),
                                LocalConfiguration.current.locales[0]
                            )
                        )
                    }
                }
                Text(releaseText, style = MaterialTheme.typography.bodyMedium)
                if (anime.releaseTimePrecision != "DATE") ReleaseCountdownLabel(anime.expectedReleaseAt)
                ReleaseDelayLabel(anime.expectedReleaseAt, anime.status)
                anime.provider.takeIf { it.isNotBlank() }?.let { provider ->
                    Text(
                        stringResource(R.string.release_provider, provider),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                LiveStatusChip(anime)
            }
        }
    }
}

/** A catalog title is deliberately not rendered as an episode release. */
@Composable
fun CatalogAnimeCard(
    item: CatalogAnimeItem,
    modifier: Modifier = Modifier,
    postponements: List<de.anisentinel.app.data.local.ReleasePostponementEntity> = emptyList(),
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.testTag("catalog_card_${item.id}").then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CatalogCover(item, Modifier.size(width = 72.dp, height = 96.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                CompactPostponementNotice(postponements)
                Text(item.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (item.providers.isEmpty()) stringResource(R.string.catalog_no_streaming_provider)
                    else stringResource(R.string.catalog_providers, item.providers.joinToString(" · ")),
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun CompactPostponementNotice(
    postponements: List<de.anisentinel.app.data.local.ReleasePostponementEntity>
) {
    val row = postponements.filter { it.isActive }.maxByOrNull { it.detectedAt } ?: return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(
                stringResource(R.string.postponed_badge),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                row.newExpectedAt?.let {
                    stringResource(R.string.postponement_new, formatCompactPostponementTime(it))
                } ?: stringResource(R.string.postponement_new_unknown),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatCompactPostponementTime(epoch: Long): String = Instant.ofEpochSecond(epoch)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm"))

@Composable
private fun CatalogCover(item: CatalogAnimeItem, modifier: Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, item.coverUrl) {
        value = withContext(Dispatchers.IO) {
            item.coverUrl?.let { url -> runCatching { CoverImageLoader.load(context, url)?.asImageBitmap() }.getOrNull() }
        }
    }
    if (image != null) {
        Image(requireNotNull(image), stringResource(R.string.anime_cover, item.title), modifier.clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(item.title.take(2).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun LiveStatusChip(anime: Anime) {
    val now by produceState(initialValue = Instant.now(), key1 = anime.expectedReleaseAt, key2 = anime.status) {
        while (true) {
            value = Instant.now()
            delay(1_000 - (System.currentTimeMillis() % 1_000))
        }
    }
    val persistent = anime.status.takeIf {
        it == ReleaseStatus.CHECKING || it == ReleaseStatus.PENDING_CONFIRMATION ||
            it == ReleaseStatus.NOT_AVAILABLE_YET || it == ReleaseStatus.PROVIDER_CHECK_FAILED ||
            it == ReleaseStatus.AVAILABLE || it == ReleaseStatus.DELAYED_UNCONFIRMED ||
            it == ReleaseStatus.OFFICIALLY_POSTPONED
    }
    StatusChip(persistent ?: ReleaseStatusResolver().resolve(anime.expectedReleaseAt, now))
}

@Composable
internal fun ReleaseCountdownLabel(releaseAt: Instant?) {
    val now by produceState(initialValue = Instant.now(), key1 = releaseAt) {
        while (releaseAt != null && value.isBefore(releaseAt)) {
            value = Instant.now()
            delay(1_000 - (System.currentTimeMillis() % 1_000))
        }
    }
    releaseAt?.takeIf { it.isAfter(now) }?.let {
        Text(
            stringResource(
                R.string.release_countdown,
                formatCardCountdown(Duration.between(now, it).coerceAtLeast(Duration.ZERO))
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun ReleaseDelayLabel(releaseAt: Instant?, status: ReleaseStatus, detectedAt: Instant? = null) {
    val mode = de.anisentinel.app.domain.watcher.ReleaseDelayPolicy.mode(status, detectedAt)
    val end = detectedAt
    val now by produceState(initialValue = Instant.now(), key1 = releaseAt, key2 = status, key3 = detectedAt) {
        while (releaseAt != null && mode == de.anisentinel.app.domain.watcher.ReleaseDelayMode.RUNNING) {
            value = Instant.now()
            delay(1_000 - (System.currentTimeMillis() % 1_000))
        }
    }
    val start = releaseAt ?: return
    val effectiveEnd = end ?: now
    if (effectiveEnd.isBefore(start) || mode == de.anisentinel.app.domain.watcher.ReleaseDelayMode.HIDDEN) return
    val value = formatCardCountdown(Duration.between(start, effectiveEnd).coerceAtLeast(Duration.ZERO))
    Text(
        stringResource(if (end == null) R.string.release_delay_running else R.string.release_delay_final, value),
        style = MaterialTheme.typography.labelLarge,
        color = if (end == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    )
}

private fun formatCardCountdown(duration: Duration): String {
    var seconds = ((duration.toMillis().coerceAtLeast(0) + 999) / 1_000)
    val days = seconds / 86_400; seconds %= 86_400
    val hours = seconds / 3_600; seconds %= 3_600
    val minutes = seconds / 60; seconds %= 60
    return if (days > 0) "%dT %02d:%02d:%02d".format(days, hours, minutes, seconds)
    else "%02d:%02d:%02d".format(hours, minutes, seconds)
}

@Composable
internal fun AnimeCover(anime: Anime, modifier: Modifier = Modifier) {
    if (anime.coverUrl == null) {
        FakeCover(anime, modifier)
        return
    }
    val description = stringResource(R.string.anime_cover, anime.title)
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, anime.coverUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching { CoverImageLoader.load(context, anime.coverUrl)?.asImageBitmap() }
                .getOrNull()
        }
    }
    if (image == null) {
        FakeCover(anime, modifier)
    } else {
        Image(
            bitmap = requireNotNull(image),
            contentDescription = description,
            modifier = modifier.clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun FakeCover(anime: Anime, modifier: Modifier = Modifier) {
    val coverDescription = stringResource(R.string.placeholder_cover, anime.title)
    val palettes = listOf(
        listOf(Color(0xFF164E63), Color(0xFF6D28D9)),
        listOf(Color(0xFF7F1D1D), Color(0xFF1D4ED8)),
        listOf(Color(0xFF134E4A), Color(0xFF312E81)),
        listOf(Color(0xFF3F3F46), Color(0xFF7E22CE)),
        listOf(Color(0xFF78350F), Color(0xFF0F766E))
    )
    val colors = palettes[anime.accentSeed.mod(palettes.size)]
    Box(
        modifier = modifier
            .semantics {
                contentDescription = coverDescription
            }
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(colors))
            .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            drawCircle(Color.White.copy(alpha = .08f), size.minDimension * .42f, center)
            drawCircle(
                color = colors.first().copy(alpha = .75f),
                radius = size.minDimension * .22f,
                center = Offset(size.width * .32f, size.height * .34f)
            )
            drawCircle(
                color = colors.last().copy(alpha = .8f),
                radius = size.minDimension * .3f,
                center = Offset(size.width * .72f, size.height * .7f)
            )
            drawLine(
                color = Color.White.copy(alpha = .22f),
                start = Offset(size.width * .18f, size.height * .82f),
                end = Offset(size.width * .82f, size.height * .18f),
                strokeWidth = 8f
            )
        }
        Text(
            anime.title.split(" ").take(2).joinToString("") { it.take(1) },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun StatusChip(status: ReleaseStatus) {
    val colors = MaterialTheme.colorScheme
    val (label, color) = when (status) {
        ReleaseStatus.SCHEDULED -> R.string.status_scheduled to colors.primary
        ReleaseStatus.RELEASE_TIME_REACHED -> R.string.status_release_time_reached to colors.tertiary
        ReleaseStatus.PRECHECK -> R.string.status_precheck to colors.secondary
        ReleaseStatus.CHECKING -> R.string.status_checking to colors.tertiary
        ReleaseStatus.PENDING_CONFIRMATION -> R.string.status_pending_confirmation to colors.tertiary
        ReleaseStatus.NOT_AVAILABLE_YET -> R.string.status_not_available_yet to colors.error
        ReleaseStatus.PROVIDER_CHECK_FAILED -> R.string.status_provider_check_failed to MaterialTheme.colorScheme.onSurfaceVariant
        ReleaseStatus.AVAILABLE -> R.string.status_available to colors.tertiary
        ReleaseStatus.NOT_FOUND -> R.string.status_not_found to colors.error
        ReleaseStatus.DELAYED_UNCONFIRMED -> R.string.status_delayed to colors.error
        ReleaseStatus.POSSIBLY_POSTPONED -> R.string.status_possible_postponed to colors.tertiary
        ReleaseStatus.OFFICIALLY_POSTPONED -> R.string.status_officially_postponed to colors.primary
        ReleaseStatus.UNKNOWN -> R.string.status_unknown to MaterialTheme.colorScheme.onSurfaceVariant
        ReleaseStatus.STOPPED -> R.string.status_stopped to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = color.copy(alpha = .14f),
        contentColor = color,
        shape = RoundedCornerShape(50),
        modifier = Modifier.border(1.dp, color.copy(alpha = .5f), RoundedCornerShape(50))
    ) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
