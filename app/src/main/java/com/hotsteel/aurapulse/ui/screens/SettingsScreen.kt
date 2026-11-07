package com.hotsteel.aurapulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotsteel.aurapulse.BuildConfig
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.core.fmt
import com.hotsteel.aurapulse.data.Contrast
import com.hotsteel.aurapulse.data.MotionLevel
import com.hotsteel.aurapulse.data.Palette
import com.hotsteel.aurapulse.data.ThemeMode
import com.hotsteel.aurapulse.data.github.AuthState
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.components.LabeledRow
import com.hotsteel.aurapulse.ui.components.PageHeader
import com.hotsteel.aurapulse.ui.components.SectionHeader
import com.hotsteel.aurapulse.ui.components.SurfaceCard
import com.hotsteel.aurapulse.ui.theme.hueGradient

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
  vm: AuraViewModel,
  onOpenAccount: () -> Unit,
  onOpenEqualizer: () -> Unit,
  onOpenAbout: () -> Unit,
  onChangeLanguage: (String) -> Unit,
) {
  val settings by vm.settings.collectAsStateWithLifecycle()
  val auth by vm.auth.collectAsStateWithLifecycle()
  val storage by vm.storage.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var languageDialog by remember { mutableStateOf(false) }
  var clearHistoryDialog by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) { vm.refreshStorage() }

  LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
    item("header") {
      PageHeader(
        eyebrow = stringResource(R.string.app_name),
        title = stringResource(R.string.nav_settings),
        subtitle = stringResource(R.string.settings_subtitle),
      )
    }

    item("account") {
      SurfaceCard(Modifier.padding(horizontal = 20.dp).clickable(onClick = onOpenAccount)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          val user = (auth as? AuthState.SignedIn)?.session?.user
          Box(
            Modifier.size(46.dp).clip(CircleShape)
              .background(if (user != null) Brush.linearGradient(hueGradient(140f)) else Brush.linearGradient(hueGradient(240f))),
            contentAlignment = Alignment.Center,
          ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
          }
          Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
              user?.login ?: stringResource(R.string.account_title),
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              if (user != null) stringResource(R.string.account_connected) else stringResource(R.string.account_sign_in_github),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    item("appearance") {
      SectionHeader(stringResource(R.string.settings_appearance))
      SurfaceCard(Modifier.padding(horizontal = 20.dp)) {
        Text(stringResource(R.string.settings_theme_mode), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        // A weighted Row of toggle buttons, not ButtonGroup: the experimental
        // group's overflow path copies incoming constraints with a smaller
        // maxWidth, and fillMaxWidth left minWidth at the full width. With the
        // longer Chinese labels the group overflowed, the copy was invalid and
        // opening Settings crashed in ButtonGroupMeasurePolicy.
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          listOf(
            ThemeMode.SYSTEM to R.string.theme_system,
            ThemeMode.LIGHT to R.string.theme_light,
            ThemeMode.DARK to R.string.theme_dark,
            ThemeMode.BLACK to R.string.theme_black,
          ).forEach { (mode, label) ->
            ToggleButton(
              checked = settings.theme == mode,
              onCheckedChange = { vm.updateSettings { it.copy(theme = mode) } },
              shapes = ToggleButtonDefaults.shapes(),
              modifier = Modifier.weight(1f).height(40.dp),
            ) {
              Text(
                stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        Spacer(Modifier.height(16.dp))
        LabeledRow(
          title = stringResource(R.string.settings_dynamic),
          subtitle = stringResource(R.string.settings_dynamic_desc),
          trailing = {
            Switch(
              checked = settings.dynamicColor,
              onCheckedChange = { enabled -> vm.updateSettings { it.copy(dynamicColor = enabled) } },
              enabled = android.os.Build.VERSION.SDK_INT >= 31,
            )
          },
        )

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_palette), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Palette.entries.forEach { palette ->
            val selected = settings.palette == palette
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Box(
                Modifier.size(44.dp).clip(CircleShape)
                  .background(Brush.linearGradient(hueGradient(paletteHue(palette))))
                  .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                  .clickable { vm.updateSettings { it.copy(palette = palette, dynamicColor = false) } },
              )
              Text(
                stringResource(paletteLabel(palette)),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_contrast), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(
            Contrast.LOW to R.string.contrast_low,
            Contrast.STANDARD to R.string.contrast_standard,
            Contrast.HIGH to R.string.contrast_high,
          ).forEach { (contrast, label) ->
            Surface(
              shape = CircleShape,
              color = if (settings.contrast == contrast) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
              modifier = Modifier.clickable { vm.updateSettings { it.copy(contrast = contrast) } },
            ) {
              Text(
                stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
              )
            }
          }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(stringResource(R.string.settings_corners), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
          Text("${(settings.cornerScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        Slider(
          value = settings.cornerScale,
          onValueChange = { value -> vm.updateSettings { it.copy(cornerScale = value) } },
          valueRange = 0.4f..1.6f,
          steps = 5,
        )

        Text(stringResource(R.string.settings_motion), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(
            MotionLevel.FULL to R.string.motion_full,
            MotionLevel.REDUCED to R.string.motion_reduced,
            MotionLevel.SYSTEM to R.string.motion_system,
          ).forEach { (motion, label) ->
            Surface(
              shape = CircleShape,
              color = if (settings.motion == motion) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
              modifier = Modifier.clickable { vm.updateSettings { it.copy(motion = motion) } },
            ) {
              Text(
                stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
              )
            }
          }
        }

        Spacer(Modifier.height(10.dp))
        LabeledRow(
          title = stringResource(R.string.settings_haptics),
          subtitle = stringResource(R.string.settings_haptics_desc),
          trailing = {
            Switch(checked = settings.haptics, onCheckedChange = { enabled -> vm.updateSettings { it.copy(haptics = enabled) } })
          },
        )
        LabeledRow(
          title = stringResource(R.string.settings_language),
          trailing = {
            TextButton(onClick = { languageDialog = true }) {
              Icon(Icons.Default.Language, null, Modifier.size(16.dp))
              Text(languageName(settings.language), Modifier.padding(start = 6.dp))
            }
          },
        )
      }
    }

    item("playback") {
      SectionHeader(stringResource(R.string.settings_playback))
      SurfaceCard(Modifier.padding(horizontal = 20.dp)) {
        LabeledRow(
          title = stringResource(R.string.settings_resume),
          subtitle = stringResource(R.string.settings_resume_desc),
          trailing = {
            Switch(checked = settings.resumeOnLaunch, onCheckedChange = { enabled -> vm.updateSettings { it.copy(resumeOnLaunch = enabled) } })
          },
        )
        LabeledRow(
          title = stringResource(R.string.equalizer_title),
          subtitle = stringResource(R.string.equalizer_bands),
          trailing = {
            TextButton(onClick = onOpenEqualizer) {
              Icon(Icons.Default.Equalizer, null, Modifier.size(18.dp))
            }
          },
        )
        val enhancements by vm.enhancements.collectAsStateWithLifecycle()
        LabeledRow(
          title = stringResource(R.string.speed_presets),
          subtitle = "0.75× · 1× · 1.25× · 1.5× · 2×",
          trailing = { Text("${fmt.speed(enhancements.speed)}×", style = MaterialTheme.typography.labelMedium) },
        )
      }
    }

    item("library") {
      SectionHeader(stringResource(R.string.settings_library))
      SurfaceCard(Modifier.padding(horizontal = 20.dp)) {
        LabeledRow(
          title = stringResource(R.string.settings_rescan),
          subtitle = stringResource(R.string.settings_rescan_desc),
          trailing = {
            TextButton(onClick = vm::rescan) { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)) }
          },
        )
        LabeledRow(
          title = stringResource(R.string.settings_storage),
          subtitle = stringResource(R.string.settings_storage_value, fmt.bytes(storage)),
          trailing = { Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
        TextButton(onClick = vm::clearArtworkCache) { Text(stringResource(R.string.settings_clear_cache)) }
        TextButton(onClick = { clearHistoryDialog = true }) { Text(stringResource(R.string.settings_clear_history)) }
      }
    }

    item("about") {
      SectionHeader(stringResource(R.string.settings_about))
      SurfaceCard(Modifier.padding(horizontal = 20.dp).clickable(onClick = onOpenAbout)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
          Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
            Text(
              stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Spacer(Modifier.height(8.dp))
      SurfaceCard(Modifier.padding(horizontal = 20.dp)) {
        Text(stringResource(R.string.settings_diagnostics), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        val report = diagnostics(context)
        Text(
          report,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 8,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = {
          val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
          clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("AuraPulse diagnostics", report))
        }) { Text(stringResource(R.string.copy_diagnostics)) }
      }
    }
  }

  if (languageDialog) {
    AlertDialog(
      onDismissRequest = { languageDialog = false },
      title = { Text(stringResource(R.string.settings_language)) },
      text = {
        Column {
          // Only the two shipped resource sets, plus "follow system". The old
          // list offered five locales that have no translations, so choosing
          // them silently fell back to English.
          listOf("system" to R.string.language_system, "en" to null, "zh-CN" to null).forEach { (tag, label) ->
            Row(
              Modifier.fillMaxWidth().clickable {
                onChangeLanguage(tag)
                languageDialog = false
              }.padding(vertical = 12.dp, horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                label?.let { stringResource(it) } ?: languageName(tag),
                style = MaterialTheme.typography.bodyLarge,
                color = if (settings.language == tag) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { languageDialog = false }) { Text(stringResource(R.string.close)) } },
    )
  }

  if (clearHistoryDialog) {
    AlertDialog(
      onDismissRequest = { clearHistoryDialog = false },
      title = { Text(stringResource(R.string.insights_clear_title)) },
      text = { Text(stringResource(R.string.insights_clear_body)) },
      confirmButton = {
        TextButton(onClick = { vm.clearListening(); clearHistoryDialog = false }) { Text(stringResource(R.string.delete)) }
      },
      dismissButton = { TextButton(onClick = { clearHistoryDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
}

private fun paletteHue(palette: Palette): Float = when (palette) {
  Palette.AURA -> 140f
  Palette.EMBER -> 16f
  Palette.TIDE -> 196f
  Palette.BLOOM -> 318f
  Palette.GRAPHITE -> 210f
  Palette.SOLAR -> 44f
}

private fun paletteLabel(palette: Palette): Int = when (palette) {
  Palette.AURA -> R.string.palette_aura
  Palette.EMBER -> R.string.palette_ember
  Palette.TIDE -> R.string.palette_tide
  Palette.BLOOM -> R.string.palette_bloom
  Palette.GRAPHITE -> R.string.palette_graphite
  Palette.SOLAR -> R.string.palette_solar
}

private fun languageName(tag: String): String = when (tag) {
  "zh-CN" -> "简体中文"
  "zh-TW" -> "繁體中文"
  "en" -> "English"
  "ja" -> "日本語"
  "ko" -> "한국어"
  "es" -> "Español"
  "de" -> "Deutsch"
  else -> "System"
}

private fun diagnostics(context: android.content.Context): String = buildString {
  appendLine("AuraPulse ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
  appendLine("Package ${BuildConfig.APPLICATION_ID}")
  appendLine("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
  appendLine("Device ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
  appendLine("ABI ${android.os.Build.SUPPORTED_ABIS.firstOrNull()}")
  appendLine("GitHub OAuth client ${if (BuildConfig.GITHUB_CLIENT_ID.isBlank()) "not configured" else "configured"}")
  appendLine("Locale ${java.util.Locale.getDefault().toLanguageTag()}")
  append("Audio effects ${if (com.hotsteel.aurapulse.playback.EqualizerController.state.value.available) "available" else "unavailable"}")
}
