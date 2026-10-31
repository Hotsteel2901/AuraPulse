package com.example.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import com.example.audio.DeviceEqualizer
import com.example.audio.EqualizerPreset
import com.example.data.cloud.GoogleAccount
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MusicViewModel, activity: MainActivity, onAbout: () -> Unit, focusEqualizer: Boolean, onFocusHandled: () -> Unit) {
  val user by vm.user.collectAsStateWithLifecycle()
  val busy by vm.busy.collectAsStateWithLifecycle()
  val eq by vm.equalizer.collectAsStateWithLifecycle()
  val signInIssue by vm.signInIssue.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  LaunchedEffect(focusEqualizer) { if (focusEqualizer) { listState.animateScrollToItem(3); onFocusHandled() } }
  var backupConfirm by rememberSaveable { mutableStateOf(false) }
  var restoreConfirm by rememberSaveable { mutableStateOf(false) }
  var languageMenu by remember { mutableStateOf(false) }
  var presetMenu by remember { mutableStateOf(false) }
  var diagnosticsOpen by rememberSaveable { mutableStateOf(false) }
  val currentLanguage = activity.getSharedPreferences("language", android.content.Context.MODE_PRIVATE).getString("locale", "system") ?: "system"
  val languages = listOf("system" to stringResource(R.string.follow_system), "en" to "English", "zh-CN" to "简体中文", "zh-TW" to "繁體中文", "ja" to "日本語", "ko" to "한국어", "es" to "Español", "de" to "Deutsch")

  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(Modifier.fillMaxWidth().widthIn(max = 820.dp), state = listState,
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 36.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp)) {
      item { PageHeader(stringResource(R.string.settings_eyebrow), stringResource(R.string.settings_title), stringResource(R.string.settings_subtitle)) }
      item {
        SettingsCard {
          SectionTitle(stringResource(R.string.account_cloud_title), stringResource(R.string.account_cloud_subtitle))
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.AccountCircle, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
              Text(if (user == null) stringResource(R.string.local_mode)
                else user?.displayName?.takeIf { it.isNotBlank() } ?: user?.email ?: stringResource(R.string.signed_in_account),
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(when {
                user == null -> stringResource(R.string.local_mode_description)
                user?.isAnonymous == true -> stringResource(R.string.anonymous_account_warning)
                else -> user?.email ?: stringResource(R.string.signed_in_account)
              }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
          }
          if (!vm.firebaseConfigured) Text(stringResource(R.string.firebase_config_help), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
          if (user == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Button(onClick = { vm.signIn(activity) }, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.sign_in_google), modifier = Modifier.padding(start = if (busy) 8.dp else 0.dp))
              }
              OutlinedButton(onClick = { vm.signIn(activity, useAccountSelector = true) }, enabled = !busy) {
                Text(stringResource(R.string.sign_in_account_selector))
              }
            }
            Text(stringResource(R.string.sign_in_alternative_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          } else {
            OutlinedButton(onClick = { vm.signOut(activity) }, enabled = !busy) { Text(stringResource(R.string.sign_out)) }
          }
          signInIssue?.let { issue ->
            Text(stringResource(R.string.sign_in_latest_issue), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            Text(issue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          TextButton(onClick = { diagnosticsOpen = true }) { Text(stringResource(R.string.sign_in_diagnostics)) }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Text(stringResource(R.string.cloud_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { backupConfirm = true }, enabled = user != null && !busy) {
              Icon(Icons.Default.CloudUpload, null, Modifier.size(18.dp))
              Text(stringResource(R.string.backup), modifier = Modifier.padding(start = 5.dp), maxLines = 1)
            }
            OutlinedButton(onClick = { restoreConfirm = true }, enabled = user != null && !busy) {
              Icon(Icons.Default.CloudDownload, null, Modifier.size(18.dp))
              Text(stringResource(R.string.restore), modifier = Modifier.padding(start = 5.dp), maxLines = 1)
            }
          }
          if (busy) Text(stringResource(R.string.working), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      item {
        SettingsCard {
          SectionTitle(stringResource(R.string.language_title), stringResource(R.string.language_subtitle))
          Box {
            OutlinedButton(onClick = { languageMenu = true }) {
              Text(languages.firstOrNull { it.first == currentLanguage }?.second ?: stringResource(R.string.follow_system))
              Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
            }
            DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
              languages.forEach { (code, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { languageMenu = false; if (code != currentLanguage) activity.changeLanguage(code) })
              }
            }
          }
        }
      }
      item {
        SettingsCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SectionTitle(stringResource(R.string.equalizer_title), stringResource(R.string.equalizer_subtitle)) }
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          }
          when {
            !eq.checked -> Text(stringResource(R.string.equalizer_waiting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            !eq.available -> Text(stringResource(R.string.equalizer_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            else -> {
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.enable_equalizer), modifier = Modifier.weight(1f))
                Switch(checked = eq.enabled, onCheckedChange = DeviceEqualizer::setEnabled)
              }
              Box {
                OutlinedButton(onClick = { presetMenu = true }, enabled = eq.enabled) {
                  Text(stringResource(eq.preset?.let(::presetLabel) ?: R.string.eq_custom))
                  Icon(Icons.Default.ExpandMore, null, Modifier.padding(start = 4.dp))
                }
                DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                  EqualizerPreset.entries.forEach { preset ->
                    DropdownMenuItem(text = { Text(stringResource(presetLabel(preset))) },
                      onClick = { presetMenu = false; DeviceEqualizer.applyPreset(preset) })
                  }
                }
              }
              Text(stringResource(R.string.eq_preset_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              eq.bands.forEach { band ->
                Column {
                  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val frequency = if (band.frequencyHz >= 1000) String.format(Locale.getDefault(), "%.1f kHz", band.frequencyHz / 1000.0) else "${band.frequencyHz} Hz"
                    Text(frequency, style = MaterialTheme.typography.labelMedium)
                    Text(String.format(Locale.getDefault(), "%+.1f dB", band.levelMb / 100.0), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  val adjustable = eq.minimumMb < eq.maximumMb
                  Slider(value = band.levelMb.toFloat(), onValueChange = { DeviceEqualizer.setBand(band.index, it.toInt()) },
                    valueRange = if (adjustable) eq.minimumMb.toFloat()..eq.maximumMb.toFloat() else -1f..1f,
                    enabled = eq.enabled && adjustable)
                }
              }
              TextButton(onClick = DeviceEqualizer::reset, enabled = eq.enabled && eq.minimumMb < eq.maximumMb) { Text(stringResource(R.string.reset_equalizer)) }
            }
          }
        }
      }
      item {
        SettingsCard {
          SectionTitle(stringResource(R.string.about_title), stringResource(R.string.about_body))
          Text("AuraPulse ${BuildConfig.VERSION_NAME} · AGPL-3.0-or-later", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          OutlinedButton(onClick = onAbout) { Text(stringResource(R.string.open_about)) }
        }
      }
    }
  }
  if (diagnosticsOpen) SignInDiagnosticsDialog(activity, signInIssue) { diagnosticsOpen = false }
  if (backupConfirm) AlertDialog(
    onDismissRequest = { backupConfirm = false }, title = { Text(stringResource(R.string.backup_confirm_title)) },
    text = { Text(stringResource(R.string.backup_confirm_body)) },
    confirmButton = { TextButton(onClick = { backupConfirm = false; vm.uploadBackup() }) { Text(stringResource(R.string.backup)) } },
    dismissButton = { TextButton(onClick = { backupConfirm = false }) { Text(stringResource(R.string.cancel)) } },
  )
  if (restoreConfirm) AlertDialog(
    onDismissRequest = { restoreConfirm = false }, title = { Text(stringResource(R.string.restore_confirm_title)) },
    text = { Text(stringResource(R.string.restore_confirm_body)) },
    confirmButton = { TextButton(onClick = { restoreConfirm = false; vm.restoreBackup() }) { Text(stringResource(R.string.restore)) } },
    dismissButton = { TextButton(onClick = { restoreConfirm = false }) { Text(stringResource(R.string.cancel)) } },
  )
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
  }
}


@androidx.annotation.StringRes
private fun presetLabel(preset: EqualizerPreset): Int = when (preset) {
  EqualizerPreset.FLAT -> R.string.eq_flat
  EqualizerPreset.BASS_BOOST -> R.string.eq_bass_boost
  EqualizerPreset.BASS_REDUCER -> R.string.eq_bass_reducer
  EqualizerPreset.TREBLE_BOOST -> R.string.eq_treble_boost
  EqualizerPreset.VOCAL -> R.string.eq_vocal
  EqualizerPreset.CLASSICAL -> R.string.eq_classical
  EqualizerPreset.DANCE -> R.string.eq_dance
  EqualizerPreset.JAZZ -> R.string.eq_jazz
  EqualizerPreset.ROCK -> R.string.eq_rock
  EqualizerPreset.ELECTRONIC -> R.string.eq_electronic
  EqualizerPreset.ACOUSTIC -> R.string.eq_acoustic
  EqualizerPreset.POP -> R.string.eq_pop
}

@Composable
private fun SignInDiagnosticsDialog(activity: MainActivity, issue: String?, onDismiss: () -> Unit) {
  val diagnostics = remember(activity) { GoogleAccount.diagnostics(activity) }
  val clipboard = LocalClipboardManager.current
  val unknown = stringResource(R.string.diagnostics_unknown)
  val status = diagnostics.playServicesStatus?.let { if (it == 0) stringResource(R.string.diagnostics_available) else "${it}" } ?: unknown
  val sha1 = diagnostics.sha1.joinToString("\n").ifBlank { unknown }
  val sha256 = diagnostics.sha256.joinToString("\n").ifBlank { unknown }
  AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.sign_in_diagnostics)) },
    text = {
      Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.diagnostics_explanation), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.diagnostics_package, diagnostics.packageName))
        Text(stringResource(R.string.diagnostics_project, diagnostics.projectId ?: unknown))
        Text(stringResource(R.string.diagnostics_web_client, stringResource(if (diagnostics.webClientConfigured) R.string.diagnostics_available else R.string.diagnostics_missing)))
        Text(stringResource(R.string.diagnostics_play_services, status))
        Text("SHA-1\n$sha1", style = MaterialTheme.typography.bodySmall)
        Text("SHA-256\n$sha256", style = MaterialTheme.typography.bodySmall)
        if (issue != null) Text(issue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.diagnostics_firebase_steps), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString("SHA-1: $sha1\nSHA-256: $sha256")) }) {
          Text(stringResource(R.string.copy_fingerprints))
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
  )
}
