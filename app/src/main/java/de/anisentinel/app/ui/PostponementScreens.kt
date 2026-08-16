package de.anisentinel.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.anisentinel.app.R
import de.anisentinel.app.data.local.ReleasePostponementEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PostponementsScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    vm: PostponementsViewModel = viewModel()
) {
    val rows by vm.items.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    AniSentinelPullToRefresh(refreshing, vm::refresh, Modifier.fillMaxSize().padding(padding)) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PostponementHeader(stringResource(R.string.postponements_title), onBack) }
        item { Text(stringResource(R.string.postponements_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (rows.isEmpty()) item { Text(stringResource(R.string.postponements_empty)) }
        items(rows, key = { it.postponementId }) { row ->
            PostponementCard(row, Modifier.fillMaxWidth().clickable { onItemClick(row.postponementId) })
        }
    }
    }
}

@Composable
fun PostponementDetailScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    vm: PostponementDetailViewModel = viewModel()
) {
    val row by vm.item.collectAsState()
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PostponementHeader(stringResource(R.string.postponement_detail_title), onBack) }
        row?.let { detail ->
            item { PostponementCard(detail, Modifier.fillMaxWidth()) }
            item {
                Text(
                    stringResource(R.string.postponement_checked_at, formatPostponementTime(detail.lastCheckedAt)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Button(
                    onClick = { runCatching { uriHandler.openUri(detail.evidenceUrl ?: detail.sourceUrl) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.postponement_open_original)) }
            }
        } ?: item { Text(stringResource(R.string.postponement_not_found)) }
    }
}

@Composable
private fun PostponementHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun PostponementCard(row: ReleasePostponementEntity, modifier: Modifier = Modifier) {
    val cadence = de.anisentinel.app.domain.watcher.ReleaseCadencePolicy.classify(
        row.originalExpectedAt, row.newExpectedAt
    )
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.postponed_badge), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            if (cadence.kind != de.anisentinel.app.domain.watcher.ScheduleInterruptionKind.ONE_OFF_SHIFT) {
                Text(stringResource(R.string.hiatus_badge), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                Text(
                    row.newExpectedAt?.let { stringResource(R.string.hiatus_return_known, formatPostponementTime(it)) }
                        ?: stringResource(R.string.hiatus_return_unknown)
                )
            }
            Text(row.title, style = MaterialTheme.typography.titleLarge)
            val identity = listOfNotNull(
                row.seasonNumber?.let { "S$it" }, row.episodeNumber?.let { "E$it" }, row.releaseLanguage
            ).joinToString(" · ")
            if (identity.isNotBlank()) Text(identity)
            row.originalExpectedAt?.let { Text(stringResource(R.string.postponement_original, formatPostponementTime(it))) }
            Text(
                row.newExpectedAt?.let { stringResource(R.string.postponement_new, formatPostponementTime(it)) }
                    ?: stringResource(R.string.postponement_new_unknown),
                color = MaterialTheme.colorScheme.error
            )
            row.reason?.takeIf(String::isNotBlank)?.let { Text(stringResource(R.string.postponement_reason, it)) }
            Text(stringResource(R.string.postponement_source), color = MaterialTheme.colorScheme.secondary)
            Text(
                stringResource(
                    if (row.confirmationStatus == "MULTI_SOURCE_CONFIRMED") R.string.postponement_multi_source
                    else R.string.postponement_single_source
                ),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatPostponementTime(epoch: Long): String {
    val value = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault())
    return if (value.toLocalTime() == java.time.LocalTime.MIDNIGHT) {
        value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    } else value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm"))
}
