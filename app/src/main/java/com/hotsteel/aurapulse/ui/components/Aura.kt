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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.hotsteel.aurapulse.core.RenderGuard
import com.hotsteel.aurapulse.data.library.Source
import com.hotsteel.aurapulse.data.library.TrackEntity
import com.hotsteel.aurapulse.ui.theme.hueGradient
import kotlin.math.absoluteValue

@Composable
fun rememberPressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.955f): Float {
  val pressed by interactionSource.collectIsPressedAsState()
  // An explicit spring rather than the theme's spatial spec. The expressive
  // motion scheme is still experimental in this build, and a pathological
  // spring here would keep the press animation running indefinitely.
  val scale by animateFloatAsState(
    targetValue = if (pressed) pressedScale else 1f,
    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 1200f),
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
  // Both the palette and the shader are cached: this composable sits in the mini
  // player, which recomposes on every playback tick, and a fresh linear gradient
  // per tick meant a fresh native shader four times a second.
  val colors = remember(track?.id, track?.source) { track?.artworkColors() ?: hueGradient(160f) }
  val brush = remember(colors) { Brush.linearGradient(colors) }
  val iconSize = if (fillWidth) 44.dp else size * 0.34f
  Box(
    modifier = modifier
      .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.size(size))
      .clip(surfaceShape)
      .background(brush),
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
  val calm by RenderGuard.calm.collectAsState()
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
      val height = size.height * (if (calm) 0.62f else animated.value)
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
  val calm by RenderGuard.calm.collectAsState()
  val transition = rememberInfiniteTransition(label = "marquee")
  // Held as a State and read in the draw phase, so the label scrolls at 60 fps
  // without recomposing or re-measuring its text on every frame.
  val progress = transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(if (overflows) 9000 else 2000, easing = androidx.compose.animation.core.LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "marqueeProgress",
  )
  Box(modifier.onSizeChanged { containerWidth = it.width }) {
    Text(
      text = text,
      style = style,
      color = color,
      maxLines = 1,
      softWrap = false,
      onTextLayout = { layout -> textWidth = layout.size.width },
      modifier = Modifier.graphicsLayer {
        translationX = if (overflows && !calm) {
          -((textWidth - containerWidth + 32).coerceAtLeast(0) * progress.value)
        } else {
          0f
        }
      },
    )
  }
}

/**
 * Generative aura background: overlapping radial glows derived from the palette.
 *
 * Deliberately static. It used to drift on a 26 s loop, which meant three
 * full-screen radial shaders were rebuilt on every frame — the render thread
 * never caught up and the main thread blocked inside
 * `HardwareRenderer.nSyncAndDrawFrame` for seconds at a time. The glows are now
 * rasterised once into a cached display list.
 */
@Composable
fun AuraBackdrop(
  modifier: Modifier = Modifier,
  playing: Boolean,
  seed: Int = 0,
) {
  val primary = MaterialTheme.colorScheme.primary
  val tertiary = MaterialTheme.colorScheme.tertiary
  val secondary = MaterialTheme.colorScheme.secondary
  val base = (seed % 360) / 360f
  Box(
    modifier
      .fillMaxSize()
      .drawWithCache {
        val w = size.width
        val h = size.height
        val d = size.minDimension
        val c1 = Offset(w * (0.24f + 0.05f * kotlin.math.cos(base * 6f)), h * 0.18f)
        val c2 = Offset(w * 0.82f, h * 0.30f)
        val c3 = Offset(w * 0.55f, h * 0.92f)
        val r1 = d * 0.70f
        val r2 = d * 0.58f
        val r3 = d * 0.64f
        val b1 = Brush.radialGradient(
          listOf(primary.copy(alpha = if (playing) 0.24f else 0.13f), Color.Transparent),
          center = c1,
          radius = r1,
        )
        val b2 = Brush.radialGradient(
          listOf(tertiary.copy(alpha = if (playing) 0.19f else 0.10f), Color.Transparent),
          center = c2,
          radius = r2,
        )
        val b3 = Brush.radialGradient(
          listOf(secondary.copy(alpha = if (playing) 0.16f else 0.09f), Color.Transparent),
          center = c3,
          radius = r3,
        )
        onDrawBehind {
          drawCircle(b1, radius = r1, center = c1)
          drawCircle(b2, radius = r2, center = c2)
          drawCircle(b3, radius = r3, center = c3)
        }
      },
  )
}

/**
 * Wavy progress bar drawn with a plain Canvas. It repaints only when the value
 * changes (a couple of times per second) and never runs a frame-clock animation,
 * so it stays off the render thread's critical path. The wave is a static sine
 * rather than a travelling one: visually the same family, an order of magnitude
 * cheaper.
 */
@Composable
fun WaveProgressBar(
  progress: Float,
  modifier: Modifier = Modifier,
  barHeight: Dp = 6.dp,
  amplitude: Dp = 2.5.dp,
  color: Color = MaterialTheme.colorScheme.primary,
  trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
  showThumb: Boolean = true,
) {
  Canvas(modifier) {
    val width = size.width
    if (width <= 0f) return@Canvas
    val cy = size.height / 2f
    val stroke = barHeight.toPx()
    val cap = stroke / 2f
    drawRoundRect(
      color = trackColor,
      topLeft = Offset(0f, cy - cap),
      size = Size(width, stroke),
      cornerRadius = CornerRadius(cap),
    )
    val value = progress.coerceIn(0f, 1f)
    if (value <= 0f) return@Canvas
    val filled = (width * value).coerceIn(stroke, width)
    val wave = amplitude.toPx()
    val wavelength = 26.dp.toPx()
    val path = Path()
    path.moveTo(0f, cy)
    var x = 0f
    var up = true
    while (x < filled) {
      val next = (x + wavelength / 2f).coerceAtMost(filled)
      path.quadraticBezierTo((x + next) / 2f, if (up) cy - wave else cy + wave, next, cy)
      x = next
      up = !up
    }
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    if (showThumb) {
      drawCircle(
        color = color,
        radius = stroke * 1.15f,
        center = Offset(filled.coerceIn(cap, (width - cap).coerceAtLeast(cap)), cy),
      )
    }
  }
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
