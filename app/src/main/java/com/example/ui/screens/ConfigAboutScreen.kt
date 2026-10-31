package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAuraStrings
import com.example.ui.theme.parseHexColor
import com.example.ui.viewmodel.PlayerUiState
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

data class TechStackNode(
  val id: String,
  val title: String,
  val badge: String,
  val detail: String,
  val accentHex: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigAboutScreen(
  playerState: PlayerUiState,
  spectrumBins: List<Float>,
  onSelectLanguage: (String) -> Unit,
  onSelectTargetSampleRate: ( Int) -> Unit,
  onSelectBufferSizeMs: (Int) -> Unit,
  onUpdateCrossfadeSeconds: (Int) -> Unit,
  onToggleReplayGain: () -> Unit,
  onToggleAutoPinOffline: () -> Unit,
  onSelectCustomThemeAccent: (String?) -> Unit,
  onToggleDynamicWallpaper: () -> Unit,
  onSelectVisualizerStyle: (String) -> Unit,
  onSendDeveloperPulse: () -> Unit,
  onShowStatusMessage: (String) -> Unit,
  onBackToDeck: () -> Unit,
) {
  BackHandler { onBackToDeck() }
  val strings = LocalAuraStrings.current
  val context = LocalContext.current
  var showFullLicenseDialog by remember { mutableStateOf(false) }
  var selectedTechIndex by remember { mutableIntStateOf(0) }
  var localBurstTrigger by remember { mutableIntStateOf(0) }

  val techNodes =
    remember(strings.languageCode) {
      when {
        strings.languageCode.startsWith("zh-CN", ignoreCase = true) ->
          listOf(
            TechStackNode(
              id = "dsp",
              title = "24-Bit / 192kHz 双二阶参量引擎",
              badge = "AUDIO DSP",
              detail = "由 github@Hotsteel2901 研发的 64 位浮点 Biquad 级联滤波器与实时 28 频段 FFT 频谱分析管线，支持硬件 AudioTrack 低延迟直通。",
              accentHex = "#00F5D4",
            ),
            TechStackNode(
              id = "api",
              title = "全球无国界自定义音源网关",
              badge = "GLOBAL API",
              detail = "支持 REST JSON、OpenSubsonic/Navidrome、全球 Radio-Browser、Internet Archive 无损库与 Jellyfin，覆盖全球 200+ 国家与地区。",
              accentHex = "#FF9F1C",
            ),
            TechStackNode(
              id = "m3",
              title = "Material 3 Expressive 灵动架构",
              badge = "COMPOSE M3",
              detail = "采用 Jetpack Compose 响应式状态流、多语言零延迟切换引擎与自适应黑胶全息画布，兼顾手机、折叠屏与平板大屏体验。",
              accentHex = "#9B5DE5",
            ),
            TechStackNode(
              id = "agpl",
              title = "AGPL-3.0-or-later 自由开源保障",
              badge = "LIBRE COPYLEFT",
              detail = "遵循 GNU Affero GPL v3.0+ 许可协议，确保网络端与客户端源码完全透明，捍卫发烧友对音频工具的永久自主权。",
              accentHex = "#10B981",
            ),
          )
        strings.languageCode.startsWith("zh", ignoreCase = true) ->
          listOf(
            TechStackNode(
              id = "dsp",
              title = "24-Bit / 192kHz 雙二階參數引擎",
              badge = "AUDIO DSP",
              detail = "由 github@Hotsteel2901 研發的 64 位元浮點 Biquad 串聯濾波器與即時 28 頻段 FFT 頻譜分析管線，支援硬體 AudioTrack 低延遲直通。",
              accentHex = "#00F5D4",
            ),
            TechStackNode(
              id = "api",
              title = "全球無國界自訂音源閘道",
              badge = "GLOBAL API",
              detail = "支援 REST JSON、OpenSubsonic/Navidrome、全球 Radio-Browser、Internet Archive 無損庫與 Jellyfin，涵蓋全球 200+ 國家與地區。",
              accentHex = "#FF9F1C",
            ),
            TechStackNode(
              id = "m3",
              title = "Material 3 Expressive 靈動架構",
              badge = "COMPOSE M3",
              detail = "採用 Jetpack Compose 響應式狀態流、多語言零延遲切換引擎與自適應黑膠全息畫布，兼顧手機、摺疊機與平板大螢幕體驗。",
              accentHex = "#9B5DE5",
            ),
            TechStackNode(
              id = "agpl",
              title = "AGPL-3.0-or-later 自由開源保障",
              badge = "LIBRE COPYLEFT",
              detail = "遵循 GNU Affero GPL v3.0+ 授權條款，確保網路端與用戶端原始碼完全透明，捍衛發燒友對音訊工具的永久自主權。",
              accentHex = "#10B981",
            ),
          )
        else ->
          listOf(
            TechStackNode(
              id = "dsp",
              title = "24-Bit / 192kHz Biquad DSP Engine",
              badge = "AUDIO DSP",
              detail = "Engineered by github@Hotsteel2901 with 64-bit floating-point cascaded biquad filters, 28-bin real-time FFT spectrum telemetry, and low-latency AudioTrack PCM output.",
              accentHex = "#00F5D4",
            ),
            TechStackNode(
              id = "api",
              title = "Borderless Universal Music APIs",
              badge = "GLOBAL API",
              detail = "Protocol-agnostic bridge supporting Open REST JSON, OpenSubsonic/Navidrome, Global Radio-Browser, Internet Archive FLAC, and Jellyfin across 200+ countries/regions.",
              accentHex = "#FF9F1C",
            ),
            TechStackNode(
              id = "m3",
              title = "Material 3 Expressive Architecture",
              badge = "COMPOSE M3",
              detail = "Built with Jetpack Compose reactive state flows, instant zero-restart multi-language localization, and adaptive layouts for phones, foldables, and tablets.",
              accentHex = "#9B5DE5",
            ),
            TechStackNode(
              id = "agpl",
              title = "AGPL-3.0-or-later Libre Copyleft",
              badge = "LIBRE COPYLEFT",
              detail = "Licensed under GNU Affero GPL v3.0 or later, guaranteeing complete source code transparency across local and network-connected audio deployments.",
              accentHex = "#10B981",
            ),
          )
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("config_about_lazy_column"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Header
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = strings.configAboutTitle,
          style = MaterialTheme.typography.headlineMedium,
        )
        Text(
          text = strings.configAboutSubtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // 1. Dynamic Developer Showcase Card (github@Hotsteel2901)
    item {
      DeveloperHotsteelShowcaseCard(
        strings = strings,
        spectrumBins = spectrumBins,
        pulseCount = playerState.developerPulseCount,
        burstTrigger = localBurstTrigger,
        techNodes = techNodes,
        selectedTechIndex = selectedTechIndex,
        onSelectTechIndex = { selectedTechIndex = it },
        onTriggerPulse = {
          localBurstTrigger++
          onSendDeveloperPulse()
        },
        onOpenGithub = {
          try {
            val intent =
              Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Hotsteel2901")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
            context.startActivity(intent)
          } catch (e: Exception) {
            onShowStatusMessage("GitHub URL: https://github.com/Hotsteel2901")
          }
        },
        onCopyHandle = {
          val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
          clipboard?.setPrimaryClip(
            ClipData.newPlainText("Developer GitHub", "github@Hotsteel2901")
          )
          onShowStatusMessage("Copied developer handle: github@Hotsteel2901 (https://github.com/Hotsteel2901)")
        },
      )
    }

    // 2. Multi-Language & Localization Config Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth().testTag("language_config_card"),
        shape = MaterialTheme.shapes.large,
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Filled.Language,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = strings.languageSectionTitle,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Text(
            text = strings.languageSectionSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            AppLanguage.entries.forEach { lang ->
              val isSelected =
                playerState.appLanguageCode.equals(lang.code, ignoreCase = true)
              FilterChip(
                selected = isSelected,
                onClick = { onSelectLanguage(lang.code) },
                label = {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = "[${lang.badge}]",
                      style = MaterialTheme.typography.labelSmall,
                      color =
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = lang.nativeLabel,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                  }
                },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  ),
                modifier = Modifier.testTag("lang_chip_${lang.code}"),
              )
            }
          }
        }
      }
    }

    // 3. Hi-Fi DSP & Audio Engine Config Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth().testTag("audio_engine_config_card"),
        shape = MaterialTheme.shapes.large,
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Filled.SettingsSuggest,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = strings.audioEngineConfigTitle,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
          }

          // Target Sample Rate
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = strings.sampleRateTargetLabel,
              style = MaterialTheme.typography.titleSmall,
            )
            val sampleRates =
              listOf(
                44 to "44.1 kHz Redbook",
                96 to "96.0 kHz Studio",
                192 to "192.0 kHz Master",
                384 to "384.0 kHz DXD",
              )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(sampleRates) { (rate, label) ->
                FilterChip(
                  selected = playerState.targetSampleRateKhz == rate,
                  onClick = { onSelectTargetSampleRate(rate) },
                  label = { Text(label) },
                  modifier = Modifier.testTag("sample_rate_chip_$rate"),
                )
              }
            }
          }

          // Audio Buffer Size
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = strings.audioBufferSizeLabel,
              style = MaterialTheme.typography.titleSmall,
            )
            val bufferSizes =
              listOf(
                64 to "64 ms (Ultra-Low Latency)",
                128 to "128 ms (Balanced Studio)",
                256 to "256 ms (Bit-Perfect Safe)",
              )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(bufferSizes) { (ms, label) ->
                FilterChip(
                  selected = playerState.audioBufferSizeMs == ms,
                  onClick = { onSelectBufferSizeMs(ms) },
                  label = { Text(label) },
                  modifier = Modifier.testTag("buffer_size_chip_$ms"),
                )
              }
            }
          }

          // Crossfade Duration Slider
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = strings.crossfadeSecondsLabel,
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text =
                  if (playerState.crossfadeSeconds == 0) "0s (Gapless Direct)"
                  else "${playerState.crossfadeSeconds}s Crossfade",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Slider(
              value = playerState.crossfadeSeconds.toFloat(),
              onValueChange = { onUpdateCrossfadeSeconds(it.toInt()) },
              valueRange = 0f..12f,
              steps = 11,
              modifier = Modifier.testTag("crossfade_slider"),
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          // ReplayGain Toggle
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = strings.replayGainTitle,
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = strings.replayGainSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = playerState.replayGainEnabled,
              onCheckedChange = { onToggleReplayGain() },
              modifier = Modifier.testTag("replay_gain_switch"),
            )
          }

          // Auto Offline Pin Toggle
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = strings.autoOfflinePinTitle,
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = strings.autoOfflinePinSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = playerState.autoPinOfflineOnPlay,
              onCheckedChange = { onToggleAutoPinOffline() },
              modifier = Modifier.testTag("auto_pin_offline_switch"),
            )
          }
        }
      }
    }

    // 4. Expressive Theme & Spectrum Visualizer Config Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth().testTag("theme_visualizer_config_card"),
        shape = MaterialTheme.shapes.large,
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Filled.Palette,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = strings.themeAccentSectionTitle,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Text(
            text = strings.themeAccentSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          val accentSwatches =
            listOf(
              null to "Auto Track Palette",
              "#00F5D4" to "Neon Cyan",
              "#FF9F1C" to "Tube Amber",
              "#9B5DE5" to "Electric Violet",
              "#FF2A85" to "Studio Crimson",
              "#10B981" to "Emerald Master",
              "#3B82F6" to "Cobalt Reference",
            )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(accentSwatches) { (hex, label) ->
              val isSelected = playerState.customThemeAccentHex == hex
              FilterChip(
                selected = isSelected,
                onClick = { onSelectCustomThemeAccent(hex) },
                leadingIcon =
                  if (hex != null) {
                    {
                      Box(
                        modifier =
                          Modifier.size(12.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(hex, MaterialTheme.colorScheme.primary))
                      )
                    }
                  } else null,
                label = { Text(label) },
                modifier = Modifier.testTag("theme_accent_chip_${hex ?: "auto"}"),
              )
            }
          }

          Text(
            text = strings.visualizerStyleLabel,
            style = MaterialTheme.typography.titleSmall,
          )
          val visualizerModes =
            listOf(
              "VINYL_FFT" to "Vinyl Platter + 28-Band FFT",
              "STUDIO_METER" to "Precision Peak/RMS Studio Meter",
              "OLED_PURE" to "Minimalist OLED Pure",
            )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visualizerModes) { (mode, label) ->
              FilterChip(
                selected = playerState.visualizerStyle == mode,
                onClick = { onSelectVisualizerStyle(mode) },
                label = { Text(label) },
                modifier = Modifier.testTag("visualizer_mode_$mode"),
              )
            }
          }
        }
      }
    }

    // 5. AGPL-3.0-or-later License Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth().testTag("agpl_license_card"),
        shape = MaterialTheme.shapes.large,
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                Icons.Filled.Gavel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = strings.licenseSectionTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(8.dp),
            ) {
              Text(
                text = strings.licenseSpdxBadge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }

          Text(
            text = strings.licenseSummary,
            style = MaterialTheme.typography.bodyMedium,
          )

          LicenseFreedomRow(
            icon = Icons.Filled.Code,
            title = strings.licenseFreedom1Title,
            description = strings.licenseFreedom1Desc,
          )
          LicenseFreedomRow(
            icon = Icons.Filled.Public,
            title = strings.licenseFreedom2Title,
            description = strings.licenseFreedom2Desc,
          )
          LicenseFreedomRow(
            icon = Icons.Filled.Security,
            title = strings.licenseFreedom3Title,
            description = strings.licenseFreedom3Desc,
          )

          OutlinedButton(
            onClick = { showFullLicenseDialog = true },
            modifier = Modifier.fillMaxWidth().testTag("view_full_agpl_license_button"),
          ) {
            Icon(Icons.Filled.Gavel, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(strings.viewFullLicenseButton)
          }
        }
      }
    }

    // 6. GitHub Actions CI/CD & Latest Component Toolchain Matrix Card
    item {
      val isZhCn = strings.languageCode.startsWith("zh-CN", ignoreCase = true)
      val isZhTw =
        !isZhCn && strings.languageCode.startsWith("zh", ignoreCase = true)
      Card(
        modifier = Modifier.fillMaxWidth().testTag("github_ci_toolchain_card"),
        shape = MaterialTheme.shapes.large,
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                Icons.Filled.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text =
                  when {
                    isZhCn -> "GITHUB ACTIONS 自动化 CI 与最新组件矩阵"
                    isZhTw -> "GITHUB ACTIONS 自動化 CI 與最新元件矩陣"
                    else -> "GITHUB ACTIONS CI & LATEST COMPONENT MATRIX"
                  },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(8.dp),
            ) {
              Text(
                text = "CI: PASSING",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }

          Text(
            text =
              when {
                isZhCn ->
                  "已配置遵循 GitHub 官方规范的自动化构建工作流 (.github/workflows/android-ci.yml) 与 Dependabot 自动版本更新 (.github/dependabot.yml)，确保所有组件持续使用最新稳定版本构建。"
                isZhTw ->
                  "已設定符合 GitHub 官方標準的自動化建置工作流程 (.github/workflows/android-ci.yml) 與 Dependabot 自動版本更新 (.github/dependabot.yml)，確保所有元件持續使用最新穩定版本建置。"
                else ->
                  "Configured with GitHub-standard automated CI (.github/workflows/android-ci.yml) and weekly Dependabot version tracking (.github/dependabot.yml) to ensure all components build with the latest versions."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          val ciComponents =
            listOf(
              "Android Gradle Plugin" to "9.1.1",
              "Gradle Distribution" to "9.3.1",
              "Kotlin / Compose Compiler" to "2.2.10",
              "KSP Symbol Processor" to "2.3.5",
              "Android Compile / Target SDK" to "API 36",
              "Firebase BoM" to "34.17.0",
              "Room Local Vault" to "2.7.0",
              "Robolectric JVM Runner" to "4.16.1",
              "GitHub Actions Checkout / Java / Gradle" to "v4 (JDK 21 / Node 22)",
              "Dependabot Ecosystem Monitor" to "v2 Weekly (Gradle + Actions)",
            )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ciComponents.forEach { (name, ver) ->
              Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = "$name:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = ver,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (showFullLicenseDialog) {
    AlertDialog(
      onDismissRequest = { showFullLicenseDialog = false },
      title = {
        Column {
          Text("GNU Affero General Public License v3.0+")
          Text(
            text = "SPDX-License-Identifier: AGPL-3.0-or-later • Copyright (C) 2026 Hotsteel2901",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      },
      text = {
        Column(
          modifier = Modifier.height(360.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = AGPL_3_OR_LATER_FULL_SUMMARY_TEXT,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          )
        }
      },
      confirmButton = {
        Button(onClick = { showFullLicenseDialog = false }) { Text("Close") }
      },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperHotsteelShowcaseCard(
  strings: com.example.ui.i18n.AuraStrings,
  spectrumBins: List<Float>,
  pulseCount: Int,
  burstTrigger: Int,
  techNodes: List<TechStackNode>,
  selectedTechIndex: Int,
  onSelectTechIndex: (Int) -> Unit,
  onTriggerPulse: () -> Unit,
  onOpenGithub: () -> Unit,
  onCopyHandle: () -> Unit,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "hotsteelOrbital")
  val orbitalPhase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 12000, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "orbitalPhase",
    )
  val pulseWave by
    infiniteTransition.animateFloat(
      initialValue = 0.25f,
      targetValue = 1.0f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "pulseWave",
    )
  val burstScale by
    animateFloatAsState(
      targetValue = if (burstTrigger % 2 == 1) 1.18f else 1.0f,
      animationSpec = tween(durationMillis = 360),
      label = "burstScale",
    )

  val activeNode = techNodes.getOrElse(selectedTechIndex) { techNodes.first() }
  val nodeAccent = parseHexColor(activeNode.accentHex, MaterialTheme.colorScheme.primary)

  Card(
    modifier = Modifier.fillMaxWidth().testTag("developer_hotsteel_card"),
    shape = RoundedCornerShape(28.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            Brush.linearGradient(
              colors =
                listOf(
                  nodeAccent.copy(alpha = 0.22f),
                  MaterialTheme.colorScheme.surfaceVariant,
                  MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                )
            )
          )
          .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Top header badge row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = nodeAccent.copy(alpha = 0.18f),
          shape = RoundedCornerShape(50),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              Icons.Filled.Verified,
              contentDescription = null,
              tint = nodeAccent,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = strings.aboutDeveloperHeader,
              style = MaterialTheme.typography.labelSmall,
              color = nodeAccent,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          shape = RoundedCornerShape(50),
        ) {
          Text(
            text = "AGPL-3.0-or-later",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      // Interactive Holo-Resonance Matrix + Developer Identity Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Interactive Canvas Holo-Avatar for github@Hotsteel2901
        Box(
          modifier =
            Modifier.size(116.dp)
              .clip(CircleShape)
              .clickable { onTriggerPulse() }
              .testTag("developer_holo_matrix_canvas"),
          contentAlignment = Alignment.Center,
        ) {
          val primaryColor = MaterialTheme.colorScheme.primary
          val secondaryColor = MaterialTheme.colorScheme.secondary
          Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = (size.minDimension / 2f) * 0.94f

            // Outer glowing aura
            drawCircle(
              brush =
                Brush.radialGradient(
                  colors =
                    listOf(
                      nodeAccent.copy(alpha = 0.35f * pulseWave),
                      secondaryColor.copy(alpha = 0.12f),
                      Color.Transparent,
                    ),
                  center = center,
                  radius = maxRadius,
                ),
              radius = maxRadius,
            )

            // Rotating harmonic telemetry rings
            rotate(degrees = orbitalPhase, pivot = center) {
              drawCircle(
                color = nodeAccent.copy(alpha = 0.75f),
                radius = maxRadius * 0.84f * burstScale.coerceIn(0.9f, 1.08f),
                style = Stroke(width = 2.dp.toPx()),
              )
              // 6 orbital satellite nodes representing audio + open source pillars
              for (i in 0 until 6) {
                val angleRad = Math.toRadians((i * 60.0))
                val r = maxRadius * 0.84f
                val nodePos =
                  Offset(
                    x = center.x + (r * cos(angleRad)).toFloat(),
                    y = center.y + (r * sin(angleRad)).toFloat(),
                  )
                drawCircle(
                  color = if (i % 2 == 0) nodeAccent else primaryColor,
                  radius = 4.dp.toPx(),
                  center = nodePos,
                )
              }
            }

            // Counter-rotating inner acoustic wave ring
            rotate(degrees = -orbitalPhase * 1.4f, pivot = center) {
              val path = Path()
              val points = 36
              val baseR = maxRadius * 0.56f
              for (i in 0..points) {
                val theta = (i.toDouble() / points) * 2.0 * Math.PI
                val binVal =
                  spectrumBins.getOrElse(i % spectrumBins.size.coerceAtLeast(1)) { 0.3f }
                val modR = baseR + (binVal * 8.dp.toPx() * pulseWave)
                val px = center.x + (modR * cos(theta)).toFloat()
                val py = center.y + (modR * sin(theta)).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
              }
              path.close()
              drawPath(
                path = path,
                color = secondaryColor.copy(alpha = 0.8f),
                style = Stroke(width = 2.dp.toPx()),
              )
            }

            // Core emblem disc
            drawCircle(
              color = Color(0xFF0B0E17),
              radius = maxRadius * 0.42f,
            )
            drawCircle(
              color = nodeAccent,
              radius = maxRadius * 0.42f,
              style = Stroke(width = 2.dp.toPx()),
            )
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "HS",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.ExtraBold,
              color = nodeAccent,
            )
            Text(
              text = "2901",
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "Hotsteel2901",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
          )
          Text(
            text = "github@Hotsteel2901",
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
            color = nodeAccent,
          )
          Text(
            text = strings.developerRoleBadge,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
          Text(
            text = strings.developerTagline,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Text(
        text = strings.interactiveMatrixHint,
        style = MaterialTheme.typography.labelSmall,
        color = nodeAccent,
      )

      Text(
        text = strings.developerBioParagraph1,
        style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        text = strings.developerBioParagraph2,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Interactive Tech Stack Radar Selector
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Text(
        text = strings.techRadarTitle,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        techNodes.forEachIndexed { index, node ->
          val selected = index == selectedTechIndex
          val chipAccent = parseHexColor(node.accentHex, MaterialTheme.colorScheme.primary)
          FilterChip(
            selected = selected,
            onClick = { onSelectTechIndex(index) },
            label = {
              Text(
                text = "${node.badge} • ${node.title}",
                style = MaterialTheme.typography.labelMedium,
              )
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = chipAccent.copy(alpha = 0.22f),
                selectedLabelColor = chipAccent,
              ),
            modifier = Modifier.testTag("tech_radar_chip_${node.id}"),
          )
        }
      }

      // Highlighted Tech Detail Box
      Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp),
        modifier =
          Modifier.fillMaxWidth()
            .border(1.dp, nodeAccent.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "${activeNode.badge} — ${activeNode.title}",
            style = MaterialTheme.typography.titleSmall,
            color = nodeAccent,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = activeNode.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      // Interactive Action Buttons Row (Open GitHub, Copy Handle, Send Sonic Star Pulse)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = onOpenGithub,
          modifier = Modifier.testTag("open_github_hotsteel_button"),
        ) {
          Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.openGithubButton)
        }

        OutlinedButton(
          onClick = onCopyHandle,
          modifier = Modifier.testTag("copy_github_hotsteel_button"),
        ) {
          Icon(
            Icons.Filled.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.copyGithubButton)
        }

        OutlinedButton(
          onClick = onTriggerPulse,
          modifier = Modifier.testTag("send_sonic_pulse_button"),
        ) {
          Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = nodeAccent,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("${strings.sendPulseButton} ($pulseCount)")
        }
      }
    }
  }
}

@Composable
private fun LicenseFreedomRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  description: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp).padding(top = 2.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private const val AGPL_3_OR_LATER_FULL_SUMMARY_TEXT =
  """GNU AFFERO GENERAL PUBLIC LICENSE
Version 3, 19 November 2007
SPDX-License-Identifier: AGPL-3.0-or-later

Copyright (C) 2026 Hotsteel2901 (github@Hotsteel2901)
https://github.com/Hotsteel2901

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version (AGPL-3.0-or-later).

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

KEY PROVISIONS (AGPL-3.0-or-later):
1. Freedom 0: The freedom to run the program as you wish, for any purpose, in any country or region.
2. Freedom 1: The freedom to study how the program works (including the 24-bit/192kHz DSP pipeline and Universal Custom Music API adapters), and change it.
3. Freedom 2: The freedom to redistribute copies to help others.
4. Freedom 3 & Section 13 (Remote Network Interaction): If you modify this program, your modified version must prominently offer all users interacting with it remotely through a computer network an opportunity to receive the Corresponding Source of your version under the GNU AGPL v3.0 or later.

Full official text: https://www.gnu.org/licenses/agpl-3.0.html"""
