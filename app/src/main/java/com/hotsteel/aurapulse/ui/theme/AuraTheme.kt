package com.hotsteel.aurapulse.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.data.AppSettings
import com.hotsteel.aurapulse.data.Contrast
import com.hotsteel.aurapulse.data.MotionLevel
import com.hotsteel.aurapulse.data.Palette
import com.hotsteel.aurapulse.data.ThemeMode

/**
 * Six curated seeds. Hues are chosen so that the expressive "vibrant" range
 * stays legible in both light and dark surfaces.
 */
private data class Seed(
  val primaryHue: Float,
  val secondaryHue: Float,
  val tertiaryHue: Float,
  val saturation: Float,
)

private fun seedOf(palette: Palette): Seed = when (palette) {
  Palette.AURA -> Seed(132f, 165f, 38f, 0.55f)
  Palette.EMBER -> Seed(14f, 340f, 40f, 0.62f)
  Palette.TIDE -> Seed(196f, 174f, 260f, 0.58f)
  Palette.BLOOM -> Seed(318f, 285f, 20f, 0.55f)
  Palette.GRAPHITE -> Seed(210f, 200f, 40f, 0.18f)
  Palette.SOLAR -> Seed(44f, 24f, 190f, 0.68f)
}

private fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
  val h = ((hue % 360f) + 360f) % 360f
  val s = saturation.coerceIn(0f, 1f)
  val l = lightness.coerceIn(0f, 1f)
  val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
  val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
  val m = l - c / 2f
  val (r, g, b) = when {
    h < 60f -> Triple(c, x, 0f)
    h < 120f -> Triple(x, c, 0f)
    h < 180f -> Triple(0f, c, x)
    h < 240f -> Triple(0f, x, c)
    h < 300f -> Triple(x, 0f, c)
    else -> Triple(c, 0f, x)
  }
  return Color(red = (r + m).coerceIn(0f, 1f), green = (g + m).coerceIn(0f, 1f), blue = (b + m).coerceIn(0f, 1f))
}

/** Approximates M3 tone mapping: chroma fades towards the extremes of the ramp. */
private fun tone(hue: Float, saturation: Float, tone: Float): Color {
  val l = tone / 100f
  val chroma = saturation * (1f - kotlin.math.abs(l - 0.5f) * 1.25f).coerceIn(0.15f, 1f)
  return hsl(hue, chroma, l)
}

private fun ColorScheme.withTones(seed: Seed, dark: Boolean, contrast: Contrast, black: Boolean): ColorScheme {
  val k = when (contrast) {
    Contrast.LOW -> 0.85f
    Contrast.STANDARD -> 1f
    Contrast.HIGH -> 1.18f
  }
  fun t(hue: Float, value: Float): Color {
    val adjusted = if (dark) 100f - (100f - value) * k else value * k
    return tone(hue, seed.saturation, adjusted.coerceIn(0f, 100f))
  }
  val p = seed.primaryHue
  val s = seed.secondaryHue
  val te = seed.tertiaryHue

  val surfaceTone = when {
    black -> 0f
    dark -> 6f
    else -> 98.5f
  }
  val onSurfaceTone = if (dark) 92f else 8f

  return copy(
    primary = t(p, if (dark) 82f else 38f),
    onPrimary = t(p, if (dark) 18f else 100f),
    primaryContainer = t(p, if (dark) 32f else 90f),
    onPrimaryContainer = t(p, if (dark) 92f else 12f),
    inversePrimary = t(p, if (dark) 40f else 80f),
    secondary = t(s, if (dark) 80f else 38f),
    onSecondary = t(s, if (dark) 18f else 100f),
    secondaryContainer = t(s, if (dark) 30f else 90f),
    onSecondaryContainer = t(s, if (dark) 92f else 12f),
    tertiary = t(te, if (dark) 84f else 38f),
    onTertiary = t(te, if (dark) 18f else 100f),
    tertiaryContainer = t(te, if (dark) 30f else 90f),
    onTertiaryContainer = t(te, if (dark) 92f else 12f),
    background = if (black) Color.Black else t(p, surfaceTone),
    onBackground = t(p, onSurfaceTone),
    surface = if (black) Color.Black else t(p, surfaceTone),
    onSurface = t(p, onSurfaceTone),
    surfaceVariant = t(p, if (dark) 28f else 90f),
    onSurfaceVariant = t(p, if (dark) 80f else 30f),
    surfaceTint = t(p, if (dark) 82f else 38f),
    inverseSurface = t(p, if (dark) 92f else 20f),
    inverseOnSurface = t(p, if (dark) 20f else 92f),
    surfaceDim = if (black) Color.Black else t(p, if (dark) 5f else 88f),
    surfaceBright = t(p, if (dark) 26f else 99f),
    surfaceContainerLowest = if (black) Color.Black else t(p, if (dark) 3f else 100f),
    surfaceContainerLow = if (black) Color.Black else t(p, if (dark) 9f else 96f),
    surfaceContainer = if (black) Color.Black else t(p, if (dark) 12f else 94f),
    surfaceContainerHigh = if (black) Color.Black else t(p, if (dark) 17f else 92f),
    surfaceContainerHighest = if (black) Color.Black else t(p, if (dark) 22f else 90f),
    outline = t(p, if (dark) 62f else 48f),
    outlineVariant = t(p, if (dark) 32f else 80f),
    error = t(25f, if (dark) 82f else 40f),
    onError = t(25f, if (dark) 18f else 100f),
    errorContainer = t(25f, if (dark) 30f else 92f),
    onErrorContainer = t(25f, if (dark) 92f else 12f),
    scrim = Color.Black,
  )
}

/** Two-stop gradient used for generated artwork covers. */
fun hueGradient(hue: Float, saturation: Float = 0.52f): List<Color> = listOf(
  hsl(hue, saturation, 0.62f),
  hsl(hue + 34f, saturation * 0.9f, 0.30f),
  hsl(hue - 24f, saturation * 0.8f, 0.22f),
)

fun auraScheme(settings: AppSettings, dark: Boolean): ColorScheme {
  val black = dark && settings.theme == ThemeMode.BLACK
  val base = if (dark) darkColorScheme() else lightColorScheme()
  return base.withTones(seedOf(settings.palette), dark, settings.contrast, black)
}

@Composable
fun rememberDynamicScheme(dark: Boolean): ColorScheme? {
  if (Build.VERSION.SDK_INT < 31) return null
  val context = LocalContext.current
  return remember(dark) {
    runCatching { if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }.getOrNull()
  }
}

private val Display = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))
private val Body = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))

private fun emphasized(style: TextStyle): TextStyle {
  // Several base styles inherit the default TextUnit.Unspecified letter spacing.
  // Scaling that value throws at composition time, so only tightened text is touched.
  val spacing = style.letterSpacing
  return style.copy(
    fontWeight = FontWeight.SemiBold,
    letterSpacing = if (spacing == TextUnit.Unspecified) spacing else spacing * 0.9f,
  )
}

private fun buildTypography(): Typography {
  val display = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium)
  val body = TextStyle(fontFamily = Body)
  val base = Typography(
    displayLarge = display.copy(fontSize = 57.sp, lineHeight = 62.sp, letterSpacing = (-0.6).sp),
    displayMedium = display.copy(fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.4).sp),
    displaySmall = display.copy(fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = display.copy(fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp),
    headlineMedium = display.copy(fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = display.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = display.copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall = body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    bodyLarge = body.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = body.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodySmall = body.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelLarge = body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = body.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelSmall = body.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
  )
  return Typography(
    displayLarge = base.displayLarge, displayMedium = base.displayMedium, displaySmall = base.displaySmall,
    headlineLarge = base.headlineLarge, headlineMedium = base.headlineMedium, headlineSmall = base.headlineSmall,
    titleLarge = base.titleLarge, titleMedium = base.titleMedium, titleSmall = base.titleSmall,
    bodyLarge = base.bodyLarge, bodyMedium = base.bodyMedium, bodySmall = base.bodySmall,
    labelLarge = base.labelLarge, labelMedium = base.labelMedium, labelSmall = base.labelSmall,
    displayLargeEmphasized = emphasized(base.displayLarge),
    displayMediumEmphasized = emphasized(base.displayMedium),
    displaySmallEmphasized = emphasized(base.displaySmall),
    headlineLargeEmphasized = emphasized(base.headlineLarge).copy(letterSpacing = (-0.5).sp),
    headlineMediumEmphasized = emphasized(base.headlineMedium),
    headlineSmallEmphasized = emphasized(base.headlineSmall),
    titleLargeEmphasized = emphasized(base.titleLarge),
    titleMediumEmphasized = emphasized(base.titleMedium),
    titleSmallEmphasized = emphasized(base.titleSmall),
    bodyLargeEmphasized = base.bodyLarge.copy(fontWeight = FontWeight.Medium),
    bodyMediumEmphasized = base.bodyMedium.copy(fontWeight = FontWeight.Medium),
    bodySmallEmphasized = base.bodySmall.copy(fontWeight = FontWeight.Medium),
    labelLargeEmphasized = base.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMediumEmphasized = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmallEmphasized = base.labelSmall.copy(fontWeight = FontWeight.SemiBold),
  )
}

fun auraShapes(scale: Float): Shapes {
  fun shape(dp: Float) = RoundedCornerShape((dp * scale).dp)
  return Shapes(
    extraSmall = shape(8f),
    small = shape(12f),
    medium = shape(18f),
    large = shape(26f),
    extraLarge = shape(34f),
  )
}

@Composable
fun AuraTheme(settings: AppSettings, systemDark: Boolean, content: @Composable () -> Unit) {
  val dark = when (settings.theme) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.BLACK -> true
    ThemeMode.SYSTEM -> systemDark
  }
  val dynamic = if (settings.dynamicColor) rememberDynamicScheme(dark) else null
  val scheme = dynamic ?: remember(settings, dark) { auraScheme(settings, dark) }
  val motion = when (settings.motion) {
    MotionLevel.FULL -> MotionScheme.expressive()
    MotionLevel.REDUCED -> MotionScheme.standard()
    MotionLevel.SYSTEM -> if (android.provider.Settings.Global.getFloat(
        LocalContext.current.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
      ) == 0f) MotionScheme.standard() else MotionScheme.expressive()
  }
  MaterialExpressiveTheme(
    colorScheme = scheme,
    motionScheme = motion,
    shapes = auraShapes(settings.cornerScale),
    typography = buildTypography(),
    content = content,
  )
}
