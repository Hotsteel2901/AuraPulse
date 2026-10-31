package com.example.ui.player

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R

@Composable
fun AboutScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(Modifier.fillMaxWidth().widthIn(max = 820.dp),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 40.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
          Text(stringResource(R.string.settings_title), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      item { PageHeader(stringResource(R.string.about_eyebrow), stringResource(R.string.about_title), stringResource(R.string.about_body)) }
      item {
        AboutCard {
          Text("AuraPulse", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
          Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("AGPL-3.0-or-later", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
          OutlinedButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Hotsteel2901/AuraPulse"))) }
          }) { Text(stringResource(R.string.view_source)) }
        }
      }
      item {
        AboutCard {
          SectionTitle(stringResource(R.string.about_playback_title))
          Text(stringResource(R.string.about_playback_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      item {
        AboutCard {
          SectionTitle(stringResource(R.string.about_privacy_title))
          Text(stringResource(R.string.about_privacy_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      item {
        AboutCard {
          SectionTitle(stringResource(R.string.about_services_title))
          Text(stringResource(R.string.about_services_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
private fun AboutCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
  }
}
