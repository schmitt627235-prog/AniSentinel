package de.anisentinel.app.ui

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.anisentinel.app.MainActivity
import de.anisentinel.app.R
import de.anisentinel.app.data.settings.BackupSection
import de.anisentinel.app.domain.provider.ProviderVisibilityPolicy

@Composable
private fun SettingsPage(scaffoldPadding: PaddingValues, title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(scaffoldPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.settings_back)) }
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        content()
    }
}

@Composable
private fun ToggleSetting(title: String, explanation: String, checked: Boolean, enabled: Boolean = true, onChange: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = { onChange() })
        }
    }
}

@Composable
fun CalendarSettingsScreen(scaffoldPadding: PaddingValues, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    SettingsPage(scaffoldPadding, stringResource(R.string.settings_calendar), onBack) {
        Text(stringResource(R.string.calendar_settings_explanation))
        ToggleSetting(stringResource(R.string.calendar_show_sub), stringResource(R.string.calendar_show_sub_hint), settings.calendarShowSub,
            enabled = settings.calendarShowDub, onChange = vm::toggleCalendarSub)
        ToggleSetting(stringResource(R.string.calendar_show_dub), stringResource(R.string.calendar_show_dub_hint), settings.calendarShowDub,
            enabled = settings.calendarShowSub, onChange = vm::toggleCalendarDub)
        ToggleSetting(stringResource(R.string.calendar_show_past), stringResource(R.string.calendar_show_past_hint), settings.calendarShowPast, onChange = vm::toggleCalendarPast)
        ToggleSetting(stringResource(R.string.calendar_favorites_only), stringResource(R.string.calendar_favorites_only_hint), settings.calendarFavoritesOnly, onChange = vm::toggleCalendarFavoritesOnly)
    }
}

@Composable
fun ProviderSettingsScreen(scaffoldPadding: PaddingValues, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    SettingsPage(scaffoldPadding, stringResource(R.string.providers), onBack) {
        Text(stringResource(R.string.providers_settings_explanation))
        ProviderVisibilityPolicy.supportedProviderIds.forEach { id ->
            ToggleSetting(
                ProviderVisibilityPolicy.displayName(id),
                stringResource(R.string.provider_visibility_hint),
                id !in settings.disabledProviderIds,
                onChange = { vm.toggleProviderVisibility(id) }
            )
        }
    }
}

@Composable
fun BackupSettingsScreen(scaffoldPadding: PaddingValues, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val state by vm.backupState.collectAsState()
    val activity = LocalView.current.context.findActivity() as? MainActivity
    SettingsPage(scaffoldPadding, stringResource(R.string.settings_backup), onBack) {
        Text(stringResource(R.string.backup_explanation))
        Text(stringResource(R.string.backup_what_save), style = MaterialTheme.typography.titleMedium)
        BackupSection.entries.forEach { section ->
            ToggleSetting(backupSectionLabel(section), stringResource(R.string.backup_include_hint), section in state.exportSections) { vm.toggleExportSection(section) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ vm.selectAllExportSections(true) }, Modifier.weight(1f)) { Text(stringResource(R.string.backup_select_all)) }
            OutlinedButton({ vm.selectAllExportSections(false) }, Modifier.weight(1f)) { Text(stringResource(R.string.backup_deselect_all)) }
        }
        Button(onClick = { activity?.createBackup { it?.let(vm::exportBackup) } }, enabled = !state.busy && activity != null && state.exportSections.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.backup_create))
        }
        OutlinedButton(onClick = { activity?.openBackup { it?.let(vm::importBackup) } }, enabled = !state.busy && activity != null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.backup_restore))
        }
        state.message?.let { Text(backupMessage(it), color = if (it.startsWith("BACKUP_")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error) }
        state.lastBackupAt?.let { Text("Letztes Backup: ${java.time.Instant.ofEpochMilli(it)}") }
        state.preview?.let { preview ->
            Text(stringResource(R.string.backup_from, preview.createdAt), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.backup_restore_what))
            preview.sections.sortedBy { it.ordinal }.forEach { section ->
                ToggleSetting(backupSectionLabel(section), stringResource(R.string.backup_contained_hint), section in state.restoreSections) { vm.toggleRestoreSection(section) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.selectAllRestoreSections(true) }, Modifier.weight(1f)) { Text(stringResource(R.string.backup_select_all)) }
                OutlinedButton({ vm.selectAllRestoreSections(false) }, Modifier.weight(1f)) { Text(stringResource(R.string.backup_deselect_all)) }
            }
            Button(vm::restoreSelectedBackup, enabled = !state.busy && state.restoreSections.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_restore_selected)) }
        }
    }
}

@Composable
private fun backupSectionLabel(section: BackupSection) = stringResource(when (section) {
    BackupSection.FAVORITES -> R.string.backup_section_favorites
    BackupSection.GENERAL -> R.string.backup_section_general
    BackupSection.NOTIFICATIONS -> R.string.backup_section_notifications
    BackupSection.CALENDAR -> R.string.backup_section_calendar
    BackupSection.PROVIDERS -> R.string.backup_section_providers
    BackupSection.WATCH_PROFILE -> R.string.backup_section_watch
    BackupSection.THEME_LANGUAGE -> R.string.backup_section_theme_language
})

private fun backupMessage(code: String): String = when {
    code.startsWith("BACKUP_CREATED:") -> "Backup erfolgreich erstellt (${code.substringAfter(':')} Favoriten)."
    code.startsWith("BACKUP_RESTORED:") -> "Backup erfolgreich wiederhergestellt (${code.substringAfter(':')} Favoriten)."
    code == "LOCAL_DATA_DELETED" -> "Lokale Nutzerdaten wurden gelöscht."
    code == "UNSUPPORTED_SCHEMA" -> "Dieses Backupformat wird nicht unterstützt. Bestehende Daten blieben unverändert."
    else -> "Backup konnte nicht verarbeitet werden ($code). Bestehende Daten blieben unverändert."
}

@Composable
fun PrivacySettingsScreen(scaffoldPadding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel()
    var confirmDelete by remember { mutableStateOf(false) }
    var notificationsGranted by remember { mutableStateOf(notificationPermissionGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) notificationsGranted = notificationPermissionGranted(context) }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SettingsPage(scaffoldPadding, stringResource(R.string.settings_privacy), onBack) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.privacy_local_data), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.privacy_local_data_explanation))
            }
        }
        Text(stringResource(if (notificationsGranted) R.string.privacy_notifications_allowed else R.string.privacy_notifications_denied))
        OutlinedButton(onClick = {
            openAniSentinelAppSettings(context)
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_open_permissions)) }
        Text(stringResource(R.string.privacy_backup_hint))
        Button(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_delete)) }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.privacy_delete_title)) },
        text = { Text(stringResource(R.string.privacy_delete_explanation)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteLocalUserData() }) { Text(stringResource(R.string.privacy_delete_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

internal fun notificationPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

internal fun openAniSentinelAppSettings(context: Context): Boolean = runCatching {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    requireNotNull(intent.resolveActivity(context.packageManager))
    context.startActivity(intent)
    true
}.getOrDefault(false)
