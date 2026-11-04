package com.hotsteel.aurapulse.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.ui.components.WaveDecoration
import com.hotsteel.aurapulse.ui.theme.hueGradient
import kotlinx.coroutines.launch

private data class OnboardPage(val icon: ImageVector, val title: Int, val body: Int, val hue: Float)

private val pages = listOf(
  OnboardPage(Icons.Default.FolderOpen, R.string.onboarding_one_title, R.string.onboarding_one_body, 268f),
  OnboardPage(Icons.Default.MotionPhotosOn, R.string.onboarding_two_title, R.string.onboarding_two_body, 318f),
  OnboardPage(Icons.Default.CloudOff, R.string.onboarding_three_title, R.string.onboarding_three_body, 202f),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
  val pager = rememberPagerState(pageCount = { pages.size })
  val scope = rememberCoroutineScope()
  val last = pager.currentPage == pages.lastIndex

  Surface(color = MaterialTheme.colorScheme.surface) {
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
      Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          stringResource(R.string.app_name).uppercase(),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          letterSpacing = 2.4.sp,
        )
        Spacer(Modifier.weight(1f))
        if (!last) {
          TextButton(onClick = onDone) { Text(stringResource(R.string.onboarding_skip)) }
        }
      }

      HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
        val page = pages[index]
        Column(
          Modifier.fillMaxSize().padding(horizontal = 24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          MorphingMark(page.icon, page.hue, selected = pager.currentPage == index)
          Spacer(Modifier.height(36.dp))
          Text(
            stringResource(page.title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(12.dp))
          Text(
            stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(18.dp))
          WaveDecoration(Modifier.fillMaxWidth().height(28.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        }
      }

      Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        pages.indices.forEach { index ->
          val active = index == pager.currentPage
          Box(
            Modifier
              .padding(horizontal = 4.dp)
              .size(width = if (active) 26.dp else 8.dp, height = 8.dp)
              .clip(CircleShape)
              .background(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
              ),
          )
        }
      }

      Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        androidx.compose.animation.AnimatedVisibility(visible = !last, enter = fadeIn(), exit = fadeOut()) {
          Button(
            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().height(56.dp),
          ) { Text(stringResource(R.string.onboarding_next)) }
        }
        androidx.compose.animation.AnimatedVisibility(visible = last, enter = fadeIn(), exit = fadeOut()) {
          Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.onboarding_start))
          }
        }
      }
    }
  }
}

/** The brand mark: a Material shape that rotates and breathes while the page is active. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingMark(icon: ImageVector, hue: Float, selected: Boolean) {
  val transition = rememberInfiniteTransition(label = "mark")
  val spin by transition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(tween(24_000, easing = FastOutSlowInEasing), RepeatMode.Restart),
    label = "spin",
  )
  val breathe by transition.animateFloat(
    initialValue = 0.96f,
    targetValue = 1.04f,
    animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "breathe",
  )
  Box(
    Modifier
      .size(210.dp)
      .graphicsLayer {
        rotationZ = if (selected) spin else 0f
        scaleX = if (selected) breathe else 1f
        scaleY = if (selected) breathe else 1f
      }
      .clip(MaterialShapes.Cookie12Sided.toShape())
      .background(Brush.linearGradient(hueGradient(hue))),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier
        .size(120.dp)
        .clip(MaterialShapes.Burst.toShape())
        .background(Color.White.copy(alpha = 0.18f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        icon,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(56.dp),
      )
    }
  }
}
