package com.hotsteel.aurapulse.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hotsteel.aurapulse.BuildConfig
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.ui.components.LabeledRow
import com.hotsteel.aurapulse.ui.components.SurfaceCard
import com.hotsteel.aurapulse.ui.theme.hueGradient

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
  Column(Modifier.fillMaxSize()) {
    DetailTopBar(stringResource(R.string.about_title), onBack)
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(Modifier.height(18.dp))
      Box(
        Modifier
          .size(96.dp)
          .clip(MaterialShapes.Cookie9Sided.toShape())
          .background(Brush.linearGradient(hueGradient(268f))),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp))
      }
      Spacer(Modifier.height(14.dp))
      Text(
        stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMediumEmphasized,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        stringResource(R.string.app_tagline),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.height(20.dp))

      SurfaceCard {
        Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
      }
      Spacer(Modifier.height(12.dp))

      SurfaceCard(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
          stringResource(R.string.about_privacy).uppercase(),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.about_privacy_body), style = MaterialTheme.typography.bodyMedium)
      }
      Spacer(Modifier.height(12.dp))

      SurfaceCard {
        LinkRow(stringResource(R.string.about_source), "https://github.com/Hotsteel2901/AuraPulse")
        LinkRow(stringResource(R.string.about_license), "https://www.gnu.org/licenses/agpl-3.0.html")
        LinkRow("Material 3 Expressive", "https://m3.material.io/blog/building-with-m3-expressive")
        LinkRow("Media3", "https://developer.android.com/media/media3")
        LinkRow("Radio Browser", "https://www.radio-browser.info/")
      }
      Spacer(Modifier.height(12.dp))

      SurfaceCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        LabeledRow("minSdk", "24")
        LabeledRow("compileSdk / targetSdk", "36.1")
        LabeledRow("Package", BuildConfig.APPLICATION_ID)
        LabeledRow("Build", BuildConfig.BUILD_TYPE)
      }
      Spacer(Modifier.height(26.dp))
      Text(
        "© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} Hotsteel2901",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(30.dp))
    }
  }
}

@Composable
private fun LinkRow(title: String, url: String) {
  val context = LocalContext.current
  Row(
    Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.small)
      .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
      .padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    Icon(
      Icons.AutoMirrored.Filled.OpenInNew,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(16.dp),
    )
  }
}
