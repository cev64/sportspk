package com.personal.sportspk.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.sportspk.LocalAppContainer
import com.personal.sportspk.data.local.ThemeMode
import com.personal.sportspk.data.local.UserSettings
import com.personal.sportspk.data.repository.BackupRepository
import com.personal.sportspk.domain.model.League
import com.personal.sportspk.ui.components.SectionHeader
import com.personal.sportspk.ui.components.TeamBadge
import kotlinx.coroutines.launch

/**
 * Settings, including the My Teams editor and data export/import.
 *
 * COMPACT: one scrolling column of grouped settings.
 * EXPANDED: the same column, shown beside the navigation rail. Settings is one of the few
 * screens where a second pane would be padding rather than information, so it deliberately
 * stays single-column and simply gains width.
 * TRANSITION: all state is either in DataStore/Room or in the ViewModel, so folding changes
 * nothing here.
 */
@Composable
fun SettingsScreen(
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            container.preferencesStore,
            container.favoritesRepository,
            container.scoreboardRepository,
            container.backupRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Notifications permission is requested HERE, when the user turns alerts on - never at launch.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.setStatusMessage(
                "Alerts stay off until notification permission is granted in Android settings."
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupRepository.MIME_TYPE)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val payload = viewModel.exportBackup()
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray()) }
            }.fold(
                onSuccess = { viewModel.setStatusMessage("Backup saved.") },
                onFailure = { viewModel.setStatusMessage("Could not write the backup file.") },
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val contents = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (contents == null) {
                viewModel.setStatusMessage("Could not read that file.")
            } else {
                viewModel.importBackup(contents)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item {
            Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
        }

        uiState.statusMessage?.let { status ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.setStatusMessage(null) }) { Text("OK") }
                }
            }
        }

        // --- My Teams ------------------------------------------------------
        item { SectionHeader("My Teams") }
        item {
            Text(
                text = "These teams drive the My Teams feed, the widget, and alerts. " +
                    "Edits are saved on this device and survive app updates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        itemsIndexed(uiState.favorites, key = { _, item -> item.key }) { index, favorite ->
            ListItem(
                leadingContent = {
                    Text(
                        text = favorite.league.shortName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                headlineContent = {
                    Text(favorite.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                trailingContent = {
                    Row {
                        IconButton(
                            onClick = { viewModel.moveFavorite(index, index - 1) },
                            enabled = index > 0,
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { viewModel.moveFavorite(index, index + 1) },
                            enabled = index < uiState.favorites.lastIndex,
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
                        }
                        IconButton(onClick = { viewModel.removeFavorite(favorite) }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove ${favorite.displayName}",
                            )
                        }
                    }
                },
            )
        }

        item {
            Text(
                text = "Add a team",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(League.entries) { league ->
                    AssistChip(
                        onClick = { viewModel.openPicker(league) },
                        label = { Text(league.shortName) },
                    )
                }
            }
        }

        // --- Appearance ----------------------------------------------------
        item { SectionHeader("Appearance") }
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    ) {
                        Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }
        item {
            SwitchRow(
                title = "Dynamic color",
                subtitle = "Match the palette to your wallpaper.",
                checked = uiState.settings.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )
        }

        // --- Startup -------------------------------------------------------
        item { SectionHeader("Open on") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.settings.startDestination == UserSettings.START_MY_TEAMS,
                    onClick = { viewModel.setStartDestination(UserSettings.START_MY_TEAMS) },
                    label = { Text("My Teams") },
                )
                FilterChip(
                    selected = uiState.settings.startDestination == UserSettings.START_SCORES,
                    onClick = { viewModel.setStartDestination(UserSettings.START_SCORES) },
                    label = { Text("Scores") },
                )
            }
        }

        // --- Alerts --------------------------------------------------------
        item { SectionHeader("Alerts") }
        item {
            SwitchRow(
                title = "Game start",
                subtitle = "Notify when one of my teams starts playing.",
                checked = uiState.settings.notifyGameStart,
                onCheckedChange = { enabled ->
                    viewModel.setNotifyGameStart(enabled)
                    if (enabled) requestNotificationPermission(notificationPermissionLauncher::launch)
                },
            )
        }
        item {
            SwitchRow(
                title = "Final score",
                subtitle = "Notify when one of my teams finishes.",
                checked = uiState.settings.notifyFinalScore,
                onCheckedChange = { enabled ->
                    viewModel.setNotifyFinalScore(enabled)
                    if (enabled) requestNotificationPermission(notificationPermissionLauncher::launch)
                },
            )
        }
        item {
            SwitchRow(
                title = "Background refresh",
                subtitle = "Check scores periodically while the app is closed. " +
                    "Turning this off also stops alerts.",
                checked = uiState.settings.backgroundRefresh,
                onCheckedChange = viewModel::setBackgroundRefresh,
            )
        }

        // --- Data ----------------------------------------------------------
        item { SectionHeader("Data") }
        item {
            Text(
                text = "A normal app update keeps your teams and settings automatically. " +
                    "Export is an extra safety net, for moving to a new device or rolling back a mistake.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                TextButton(onClick = { exportLauncher.launch(BackupRepository.suggestedFileName()) }) {
                    Text("Export backup")
                }
                TextButton(onClick = { importLauncher.launch(arrayOf(BackupRepository.MIME_TYPE)) }) {
                    Text("Import backup")
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            TextButton(onClick = onOpenAbout) { Text("About Sportspk") }
        }
    }

    uiState.pickerLeague?.let { league ->
        TeamPickerDialog(
            league = league,
            teams = uiState.pickerTeams,
            isLoading = uiState.isLoadingPicker,
            existingKeys = uiState.favorites.map { it.key }.toSet(),
            onPick = { team ->
                viewModel.addFavorite(team)
                viewModel.closePicker()
            },
            onDismiss = viewModel::closePicker,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

@Composable
private fun TeamPickerDialog(
    league: League,
    teams: List<com.personal.sportspk.domain.model.Team>,
    isLoading: Boolean,
    existingKeys: Set<String>,
    onPick: (com.personal.sportspk.domain.model.Team) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(teams, query) {
        if (query.isBlank()) teams
        else teams.filter { it.displayName.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Add a ${league.shortName} team") },
        text = {
            Column(modifier = Modifier.height(420.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                } else {
                    LazyColumn {
                        items(filtered, key = { it.id }) { team ->
                            val already = team.key in existingKeys
                            ListItem(
                                modifier = if (already) Modifier else Modifier.clickable { onPick(team) },
                                leadingContent = { TeamBadge(team = team, size = 30.dp) },
                                headlineContent = {
                                    Text(team.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                trailingContent = if (already) {
                                    { Text("Added", style = MaterialTheme.typography.labelSmall) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** POST_NOTIFICATIONS only exists from API 33; below that, alerts work with no prompt. */
private fun requestNotificationPermission(launch: (String) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
