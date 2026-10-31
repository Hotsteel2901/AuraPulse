package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.CustomMusicSource
import com.example.data.model.OfflineTrackEntity
import com.example.data.model.SyncState
import com.example.data.remote.CustomMusicSourceBridge
import com.example.data.remote.KnownApiTemplate
import com.example.ui.auth.AuraAccountSession
import com.example.ui.auth.OptionalAccountSyncCard
import com.example.ui.i18n.LocalAuraStrings
import com.example.ui.viewmodel.PlayerUiState
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun CloudOfflineScreen(
  playerState: PlayerUiState,
  allTracks: List<OfflineTrackEntity>,
  customSourcesState: UiState<List<CustomMusicSource>>,
  syncStatesState: UiState<List<SyncState>>,
  onSelectGlobalRegion: (String) -> Unit,
  onAddCustomSource: (String, String, String, String, String, String, String, String) -> Unit,
  onAddKnownTemplate: (KnownApiTemplate, String) -> Unit,
  onToggleCustomSourceEnabled: (CustomMusicSource) -> Unit,
  onDeleteCustomSource: (String) -> Unit,
  onTestCustomSource: (CustomMusicSource) -> Unit,
  onQueryCustomSources: (String, String?) -> Unit,
  onPublishSyncState: () -> Unit,
  onResumeFromSyncedDevice: (SyncState) -> Unit,
  onToggleOfflineDownload: (OfflineTrackEntity) -> Unit,
  onPlayTrack: (OfflineTrackEntity) -> Unit,
  onSessionUpdated: (AuraAccountSession) -> Unit,
  onSignOut: () -> Unit,
  onShowStatusMessage: (String) -> Unit,
  onBackToDeck: () -> Unit,
) {
  BackHandler { onBackToDeck() }
  val strings = LocalAuraStrings.current
  var showAddSourceDialog by remember { mutableStateOf(false) }
  var showTemplatesDialog by remember { mutableStateOf(false) }
  var customRegionInput by remember { mutableStateOf("") }
  var cloudQuery by remember { mutableStateOf("") }

  val customSources = (customSourcesState as? UiState.Success)?.data ?: emptyList()
  val offlineTracks = allTracks.filter { it.isDownloadedOffline }
  val totalOfflineMb = offlineTracks.sumOf { it.offlineSizeMb }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("cloud_offline_lazy_column"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 0. Optional Account Sync Card (Local-First Mode + Google Login + GitHub Login)
    item {
      OptionalAccountSyncCard(
        session = playerState.accountSession,
        onSessionUpdated = onSessionUpdated,
        onSignOut = onSignOut,
        onStatusMessage = onShowStatusMessage,
      )
    }

    // 1. Global Country & Area Routing Bar (All Countries Worldwide)
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              Icons.Filled.Public,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = strings.regionRouterHeader,
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text =
                  "${playerState.selectedGlobalRegion} • ${strings.regionRouterSubtitle}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CustomMusicSourceBridge.SUPPORTED_REGIONS) { (code, label) ->
              FilterChip(
                selected = playerState.selectedGlobalRegion == code,
                onClick = { onSelectGlobalRegion(code) },
                label = { Text(label) },
                modifier = Modifier.testTag("region_chip_$code"),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedTextField(
              value = customRegionInput,
              onValueChange = { customRegionInput = it.take(8) },
              label = { Text(strings.customIsoLabel) },
              singleLine = true,
              modifier = Modifier.weight(1f).testTag("custom_iso_region_input"),
            )
            FilledTonalButton(
              onClick = {
                if (customRegionInput.isNotBlank()) {
                  onSelectGlobalRegion(customRegionInput)
                  customRegionInput = ""
                }
              },
              enabled = customRegionInput.isNotBlank(),
              modifier = Modifier.testTag("apply_custom_region_button"),
            ) {
              Text(strings.applyRegionButton)
            }
          }
        }
      }
    }

    // 2. Universal Custom Music Source APIs Hub
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = strings.cloudSyncTitle,
                style = MaterialTheme.typography.headlineMedium,
              )
              Text(
                text = strings.cloudSyncSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = { showAddSourceDialog = true },
              modifier = Modifier.weight(1f).testTag("add_custom_api_button"),
            ) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(strings.addApiButton)
            }
            FilledTonalButton(
              onClick = { showTemplatesDialog = true },
              modifier = Modifier.weight(1f).testTag("known_api_templates_button"),
            ) {
              Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(strings.presetsHeader)
            }
          }

          // Search across all enabled Custom Music APIs
          OutlinedTextField(
            value = cloudQuery,
            onValueChange = { cloudQuery = it },
            label = {
              Text("${strings.searchCustomApisHeader} (${playerState.selectedGlobalRegion})")
            },
            placeholder = { Text(strings.searchCustomApisPlaceholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("custom_api_search_input"),
          )

          Button(
            onClick = { onQueryCustomSources(cloudQuery, null) },
            enabled = !playerState.isCloudSearching && cloudQuery.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag("query_all_custom_apis_button"),
          ) {
            if (playerState.isCloudSearching) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Querying Global Endpoints...")
            } else {
              Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                "Fetch & Import from Enabled Sources (${customSources.count { it.isEnabled }})"
              )
            }
          }
        }
      }
    }

    // 3. User's Configured Custom Music APIs List
    items(customSources, key = { it.id }) { source ->
      val diag = playerState.lastEndpointDiagnostics[source.id]
      Card(
        modifier = Modifier.fillMaxWidth().testTag("custom_source_card_${source.id}"),
        shape = MaterialTheme.shapes.medium,
        colors =
          CardDefaults.cardColors(
            containerColor =
              if (source.isEnabled) MaterialTheme.colorScheme.surfaceVariant
              else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
          ),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Surface(
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                  shape = RoundedCornerShape(6.dp),
                ) {
                  Text(
                    text = "${source.protocolType} • ${source.regionCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }
                Text(
                  text = source.preferredCodec,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.tertiary,
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = source.name,
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "${source.baseUrl}${source.searchPath}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Switch(
              checked = source.isEnabled,
              onCheckedChange = { onToggleCustomSourceEnabled(source) },
              modifier = Modifier.testTag("toggle_source_${source.id}"),
            )
          }

          if (diag != null) {
            Surface(
              color =
                if (diag.reachable) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = diag.summary,
                style = MaterialTheme.typography.labelLarge,
                color =
                  if (diag.reachable) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilledTonalButton(
                onClick = { onTestCustomSource(source) },
                modifier = Modifier.testTag("ping_source_${source.id}"),
              ) {
                Icon(
                  Icons.Filled.NetworkCheck,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test API")
              }
              if (cloudQuery.isNotBlank()) {
                FilledTonalButton(
                  onClick = { onQueryCustomSources(cloudQuery, source.id) },
                  modifier = Modifier.testTag("query_single_source_${source.id}"),
                ) {
                  Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Query Only This")
                }
              }
            }

            IconButton(
              onClick = { onDeleteCustomSource(source.id) },
              modifier = Modifier.testTag("delete_source_${source.id}"),
            ) {
              Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "Delete Custom API",
              )
            }
          }
        }
      }
    }

    // 4. Cross-Platform Sync & Playback Handoff
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Cross-Platform Playback Handoff",
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text = "Local & cloud session handoff across devices",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Button(
              onClick = onPublishSyncState,
              modifier = Modifier.testTag("broadcast_sync_state_button"),
            ) {
              Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Sync Now")
            }
          }

          val syncList = (syncStatesState as? UiState.Success)?.data ?: emptyList()
          syncList.forEach { session ->
            Card(
              colors =
                CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(
                    Icons.Filled.Devices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Column {
                    Text(text = session.deviceName, style = MaterialTheme.typography.titleMedium)
                    Text(
                      text =
                        "${session.activeSourceName} [${session.regionCode}] • ${session.audioQualityBadge} • ${formatDurationMs(session.positionMs)}",
                      style = MaterialTheme.typography.labelLarge,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
                FilledTonalButton(
                  onClick = { onResumeFromSyncedDevice(session) },
                  modifier = Modifier.testTag("handoff_resume_${session.id}"),
                ) {
                  Text("Handoff")
                }
              }
            }
          }
        }
      }
    }

    // 5. Offline Hi-Res FLAC/WAV Vault
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              Icons.Filled.FolderSpecial,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Column {
              Text(
                text = "Offline Lossless Storage Vault",
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text =
                  String.format(
                    Locale.US,
                    "%d tracks cached on device (%.1f MB lossless PCM/FLAC)",
                    offlineTracks.size,
                    totalOfflineMb,
                  ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }

    items(offlineTracks, key = { it.trackId }) { track ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
          ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = track.title, style = MaterialTheme.typography.titleMedium)
            Text(
              text = "${track.codec} • ${track.offlineSizeMb} MB local file ready",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Row {
            IconButton(onClick = { onPlayTrack(track) }) {
              Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play offline track",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
            IconButton(onClick = { onToggleOfflineDownload(track) }) {
              Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "Remove offline file",
              )
            }
          }
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(24.dp))
    }
  }

  if (showAddSourceDialog) {
    AddCustomSourceDialog(
      defaultRegion = playerState.selectedGlobalRegion,
      onDismiss = { showAddSourceDialog = false },
      onAdd = { name, baseUrl, searchPath, protocol, region, headerName, headerVal, codec ->
        onAddCustomSource(
          name,
          baseUrl,
          searchPath,
          protocol,
          region,
          headerName,
          headerVal,
          codec,
        )
        showAddSourceDialog = false
      },
    )
  }

  if (showTemplatesDialog) {
    KnownGlobalTemplatesDialog(
      activeRegion = playerState.selectedGlobalRegion,
      onDismiss = { showTemplatesDialog = false },
      onSelectTemplate = { template ->
        onAddKnownTemplate(template, playerState.selectedGlobalRegion)
        showTemplatesDialog = false
      },
    )
  }
}

@Composable
private fun AddCustomSourceDialog(
  defaultRegion: String,
  onDismiss: () -> Unit,
  onAdd: (String, String, String, String, String, String, String, String) -> Unit,
) {
  BackHandler { onDismiss() }
  var name by remember { mutableStateOf("") }
  var baseUrl by remember { mutableStateOf("https://") }
  var searchPath by remember { mutableStateOf("/search?q={query}&country={region}") }
  var protocolType by remember { mutableStateOf("OPEN_REST_JSON") }
  var regionCode by remember { mutableStateOf(defaultRegion) }
  var headerName by remember { mutableStateOf("") }
  var headerValue by remember { mutableStateOf("") }
  var preferredCodec by remember { mutableStateOf("FLAC 24-Bit/96kHz") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Custom Music Source API") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Source Name (e.g., Tokyo Subsonic / Custom API)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("custom_source_name_input"),
        )
        OutlinedTextField(
          value = baseUrl,
          onValueChange = { baseUrl = it },
          label = { Text("Base Endpoint URL (https://...)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("custom_source_url_input"),
        )
        OutlinedTextField(
          value = searchPath,
          onValueChange = { searchPath = it },
          label = { Text("Search Path ({query} & {region} supported)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("custom_source_path_input"),
        )

        Text("Protocol Adapter", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(CustomMusicSourceBridge.PROTOCOL_OPTIONS) { (protoKey, protoLabel) ->
            FilterChip(
              selected = protocolType == protoKey,
              onClick = { protocolType = protoKey },
              label = { Text(protoLabel) },
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = regionCode,
            onValueChange = { regionCode = it.uppercase().take(12) },
            label = { Text("Country/Area (GLOBAL, US, JP, KR...)") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("custom_source_region_input"),
          )
          OutlinedTextField(
            value = preferredCodec,
            onValueChange = { preferredCodec = it },
            label = { Text("Codec Badge") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = headerName,
            onValueChange = { headerName = it },
            label = { Text("Optional Auth Header") },
            placeholder = { Text("Authorization / X-API-Key") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = headerValue,
            onValueChange = { headerValue = it },
            label = { Text("Header Token/Value") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onAdd(
            name.ifBlank { "Custom Music Node" },
            baseUrl.ifBlank { "https://de1.api.radio-browser.info" },
            searchPath,
            protocolType,
            regionCode.ifBlank { "GLOBAL" },
            headerName,
            headerValue,
            preferredCodec.ifBlank { "FLAC Hi-Res" },
          )
        },
        modifier = Modifier.testTag("confirm_add_custom_source_button"),
      ) {
        Text("Save & Sync API")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun KnownGlobalTemplatesDialog(
  activeRegion: String,
  onDismiss: () -> Unit,
  onSelectTemplate: (KnownApiTemplate) -> Unit,
) {
  BackHandler { onDismiss() }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Known Global & Self-Hosted API Templates") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text =
            "Tap any template to add it with your active region ($activeRegion). You can also customize the URL or headers anytime.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CustomMusicSourceBridge.KNOWN_GLOBAL_TEMPLATES.forEachIndexed { idx, tpl ->
          Card(
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onSelectTemplate(tpl) }
                .testTag("known_template_$idx"),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
              ),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(text = tpl.name, style = MaterialTheme.typography.titleMedium)
              Text(
                text = "${tpl.protocolType} • ${tpl.preferredCodec}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = tpl.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    },
  )
}
