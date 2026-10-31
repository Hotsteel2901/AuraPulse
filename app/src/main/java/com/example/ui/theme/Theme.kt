package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val ExpressiveShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
  )

@Composable
fun AuraPulseTheme(
  accentHex: String = "#00F5D4",
  secondaryHex: String = "#7B2CBF",
  useSystemDynamicWallpaper: Boolean = false,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val targetPrimary = parseHexColor(accentHex, ElectricCyan)
  val targetSecondary = parseHexColor(secondaryHex, NeonViolet)

  val animatedPrimary by
    animateColorAsState(
      targetValue = targetPrimary,
      animationSpec = spring(stiffness = Spring.StiffnessLow),
      label = "dynamicPrimary",
    )
  val animatedSecondary by
    animateColorAsState(
      targetValue = targetSecondary,
      animationSpec = spring(stiffness = Spring.StiffnessLow),
      label = "dynamicSecondary",
    )
  val animatedSurfaceVariant by
    animateColorAsState(
      targetValue = lerp(ObsidianSurfaceVariant, targetPrimary, 0.14f),
      animationSpec = spring(stiffness = Spring.StiffnessLow),
      label = "dynamicSurfaceVariant",
    )
  val animatedBackground by
    animateColorAsState(
      targetValue = lerp(ObsidianBackground, targetPrimary, 0.06f),
      animationSpec = spring(stiffness = Spring.StiffnessLow),
      label = "dynamicBackground",
    )

  val baseScheme =
    if (useSystemDynamicWallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      dynamicDarkColorScheme(context)
    } else {
      darkColorScheme(
        primary = animatedPrimary,
        onPrimary = Color(0xFF041318),
        primaryContainer = lerp(ObsidianSurfaceHigh, animatedPrimary, 0.28f),
        onPrimaryContainer = TextPrimary,
        secondary = animatedSecondary,
        onSecondary = TextPrimary,
        secondaryContainer = lerp(ObsidianSurfaceVariant, animatedSecondary, 0.30f),
        onSecondaryContainer = TextPrimary,
        tertiary = WarmAmber,
        background = animatedBackground,
        onBackground = TextPrimary,
        surface = ObsidianSurface,
        onSurface = TextPrimary,
        surfaceVariant = animatedSurfaceVariant,
        onSurfaceVariant = TextSecondary,
      )
    }

  MaterialTheme(
    colorScheme = baseScheme,
    typography = Typography,
    shapes = ExpressiveShapes,
    content = content,
  )
}

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
  AuraPulseTheme(content = content)
}
