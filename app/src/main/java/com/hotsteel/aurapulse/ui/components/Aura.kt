package com.hotsteel.aurapulse.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hotsteel.aurapulse.data.library.Source
import com.hotsteel.aurapulse.data.library.TrackEntity
import com.hotsteel.aurapulse.ui.theme.hueGradient
import kotlin.math.absoluteValue

@Composable
fun rememberPressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.955f): Float {
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed) pressedScale else 1f,
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    label = "press",
  )
  return scale
}

/** Deterministic, scheme-aware gradient derived from the track id when no artwork exists. */
fun TrackEntity.artworkColors(): List<Color> {
  val seed = (id.hashCode().absoluteValue % 360).toFloat()
  val offset = when (source) {
    Source.RADIO -> 18f
    Source.PREVIEW -> 44f
    else -> 0f
  }
  return hueGradient(seed + offset)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Artwork(
  track: TrackEntity?,
  size: Dp,
  modifier: Modifier = Modifier,
  shape: Shape? = null,
  corner: Dp = size / 5f,
  contentScale: ContentScale = ContentScale.Crop,
  fillWidth: Boolean = false,
) {
  val surfaceShape = shape ?: RoundedCornerShape(corner)
  val colors = track?.artworkColors() ?: hueGradient(160f)
  val iconSize = if (fillWidth) 44.dp else size * 0.34f
  Box(
    modifier = modifier
      .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.size(size))
      .clip(surfaceShape)
      .background(Brush.linearGradient(colors)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      Icons.Default.MusicNote,
      contentDescription = null,
      tint = Color.White.copy(alpha = 0.55f),
      modifier = Modifier.size(iconSize),
    )
    val artwork = track?.artworkUri?.takeIf { it.isNotBlank() }
    if (artwork != null) {
      AsyncImage(
        model = artwork,
        contentDescription = null,
        contentScale = contentScale,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

/** Small three-bar "now playing" indicator that only animates while playing. */
@Composable
fun PlayingBars(color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "bars")
  val values = (0..2).map { index ->
    transition.animateFloat(
      initialValue = 0.35f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(380 + index * 90),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "bar$index",
    )
  }
  Canvas(modifier.size(width = 14.dp, height = 14.dp)) {
    val barWidth = size.width / 5f
    values.forEachIndexed { index, animated ->
      val height = size.height * animated.value
      drawRoundRect(
        color = color,
        topLeft = Offset(index * barWidth * 2f, size.height - height),
        size = androidx.compose.ui.geometry.Size(barWidth, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
      )
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  action: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        title,
        style = MaterialTheme.typography.titleLargeEmphasized,
        color = MaterialTheme.colorScheme.onBackground,
      )
      if (subtitle != null) {
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    action?.invoke()
  }
}

@Composable
fun PageHeader(
  eyebrow: String,
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  trailing: (@Composable () -> Unit)? = null,
) {
  Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          eyebrow.uppercase(),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 1.6.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          title,
          style = MaterialTheme.typography.displaySmall,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }
      trailing?.invoke()
    }
    Spacer(Modifier.height(6.dp))
    Text(
      subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun SurfaceCard(
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.surfaceContainer,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = color,
  ) {
    Column(Modifier.padding(18.dp), content = content)
  }
}

@Composable
fun EmptyState(
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  action: (@Composable () -> Unit)? = null,
) {
  Column(
    modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      Modifier.size(86.dp).clip(MaterialShapes.Cookie9Sided.toShape())
        .background(
          Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
          ),
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Default.PlayArrow,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(40.dp),
      )
    }
    Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
    Text(
      body,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.fillMaxWidth(),
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
    action?.invoke()
  }
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier) {
    Text(
      value,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
    )
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
  }
}

@Composable
fun LabeledRow(
  title: String,
  subtitle: String? = null,
  modifier: Modifier = Modifier,
  trailing: @Composable (() -> Unit)? = null,
) {
  Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
      if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    trailing?.invoke()
  }
}

/** Marquee that only starts when the text actually overflows its container. */
@Composable
fun MarqueeText(
  text: String,
  style: androidx.compose.ui.text.TextStyle,
  color: Color = MaterialTheme.colorScheme.onSurface,
  modifier: Modifier = Modifier,
) {
  var containerWidth by remember { mutableIntStateOf(0) }
  var textWidth by remember { mutableIntStateOf(0) }
  val overflows = textWidth > containerWidth && containerWidth > 0
  val transition = rememberInfiniteTransition(label = "marquee")
  val progress by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(if (overflows) 9000 else 2000, easing = androidx.compose.animation.core.LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "marqueeProgress",
  )
  val shift = if (overflows) -((textWidth - containerWidth + 32).coerceAtLeast(0)) * progress else 0f
  Box(modifier.onSizeChanged { containerWidth = it.width }) {
    Text(
      text = text,
      style = style,
      color = color,
      maxLines = 1,
      softWrap = false,
      onTextLayout = { layout -> textWidth = layout.size.width },
      modifier = Modifier.graphicsLayer { translationX = shift },
    )
  }
}

/**
 * Generative aura background: slow drifting radial gradients. It is decorative,
 * not a spectrum analyser, and it stops moving when playback is paused.
 */
@Composable
fun AuraBackdrop(
  modifier: Modifier = Modifier,
  playing: Boolean,
  seed: Int = 0,
) {
  val transition = rememberInfiniteTransition(label = "aura")
  val drift by transition.animateFloat(
    initialValue = 0f,
    targetValue = (Math.PI * 2).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(if (playing) 24_000 else 60_000, easing = androidx.compose.animation.core.LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "drift",
  )
  val primary = MaterialTheme.colorScheme.primary
  val tertiary = MaterialTheme.colorScheme.tertiary
  val secondary = MaterialTheme.colorScheme.secondary
  Canvas(modifier.fillMaxSize()) {
    val base = (seed % 360) / 360f
    drawAuraBlob(
      color = primary.copy(alpha = if (playing) 0.30f else 0.16f),
      center = Offset(
        size.width * (0.24f + 0.10f * kotlin.math.cos(drift + base * 6f)),
        size.height * (0.18f + 0.06f * kotlin.math.sin(drift * 0.8f)),
      ),
      radius = size.minDimension * 0.62f,
    )
    drawAuraBlob(
      color = tertiary.copy(alpha = if (playing) 0.24f else 0.12f),
      center = Offset(
        size.width * (0.82f + 0.08f * kotlin.math.sin(drift * 0.7f)),
        size.height * (0.30f + 0.05f * kotlin.math.cos(drift)),
      ),
      radius = size.minDimension * 0.52f,
    )
    drawAuraBlob(
      color = secondary.copy(alpha = if (playing) 0.20f else 0.10f),
      center = Offset(
        size.width * (0.55f + 0.12f * kotlin.math.sin(drift * 0.5f + 1.4f)),
        size.height * (0.92f + 0.04f * kotlin.math.cos(drift * 0.9f)),
      ),
      radius = size.minDimension * 0.58f,
    )
  }
}

private fun DrawScope.drawAuraBlob(color: Color, center: Offset, radius: Float) {
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(color, Color.Transparent),
      center = center,
      radius = radius,
    ),
    radius = radius,
    center = center,
  )
}

/** Rounded outline used by sheets and floating cards to separate them from the backdrop. */
@Composable
fun outlineModifier(): Modifier = Modifier.border(
  width = 1.dp,
  color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
  shape = MaterialTheme.shapes.extraLarge,
)

@Composable
fun SourceBadge(track: TrackEntity) {
  val label = when (track.source) {
    Source.RADIO -> "RADIO"
    Source.PREVIEW -> "PREVIEW"
    Source.CUSTOM -> "API"
    Source.DIRECT -> "LINK"
    else -> null
  } ?: return
  Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
  ) {
    Text(
      label,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
  }
}

@Composable
fun TrackSubtitle(track: TrackEntity, modifier: Modifier = Modifier) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      track.artist.ifBlank { "Unknown artist" },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false),
    )
    SourceBadge(track)
    if (track.missing) {
      Text("Missing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
  }
}

/** Simple wave decoration for empty and onboarding surfaces. */
@Composable
fun WaveDecoration(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
  Canvas(modifier) {
    val path = Path()
    val mid = size.height / 2f
    path.moveTo(0f, mid)
    var x = 0f
    val step = size.width / 24f
    while (x < size.width) {
      path.quadraticBezierTo(x + step / 2f, mid - size.height * 0.35f, x + step, mid)
      x += step
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
  }
}
