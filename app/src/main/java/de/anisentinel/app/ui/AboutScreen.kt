package de.anisentinel.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.anisentinel.app.BuildConfig
import de.anisentinel.app.R

@Composable
fun AboutScreen(scaffoldPadding: PaddingValues, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .testTag(UiTags.ABOUT)
            .padding(scaffoldPadding)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            modifier = Modifier.testTag(UiTags.ABOUT_LIST),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PageTitle(
                    stringResource(R.string.app_name),
                    stringResource(R.string.about_subtitle)
                )
            }
            item {
                AboutCard(stringResource(R.string.about_version)) {
                    Text("${BuildConfig.VERSION_NAME} · ${stringResource(R.string.about_build)} ${BuildConfig.VERSION_CODE}")
                }
            }
            item {
                AboutCard(stringResource(R.string.about_status)) {
                    Text(stringResource(R.string.about_status_value))
                }
            }
            item {
                AboutCard(stringResource(R.string.about_privacy)) {
                    Text(stringResource(R.string.about_privacy_text))
                }
            }
            item {
                AboutCard(stringResource(R.string.about_sources)) {
                    Text(stringResource(R.string.about_sources_text))
                }
            }
            item {
                AboutCard(stringResource(R.string.about_media)) {
                    Text(stringResource(R.string.about_media_text))
                }
            }
            item {
                AboutCard(stringResource(R.string.about_libraries)) {
                    Text(stringResource(R.string.about_libraries_text))
                }
            }
            item {
                AboutCard(stringResource(R.string.about_changelog)) {
                    Text(
                        "${BuildConfig.VERSION_NAME} · " +
                            stringResource(R.string.about_changelog_text)
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
