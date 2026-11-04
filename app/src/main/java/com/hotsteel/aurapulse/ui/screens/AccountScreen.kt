package com.hotsteel.aurapulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hotsteel.aurapulse.BuildConfig
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.data.github.AuthState
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.components.LabeledRow
import com.hotsteel.aurapulse.ui.components.PageHeader
import com.hotsteel.aurapulse.ui.components.SectionHeader
import com.hotsteel.aurapulse.ui.components.SurfaceCard
import com.hotsteel.aurapulse.ui.theme.hueGradient
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountScreen(vm: AuraViewModel, onBack: () -> Unit) {
  val auth by vm.auth.collectAsStateWithLifecycle()
  val sync by vm.sync.collectAsStateWithLifecycle()
  val busy by vm.syncBusy.collectAsStateWithLifecycle()
  val device by vm.deviceFlow.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var token by remember { mutableStateOf("") }
  var showToken by remember { mutableStateOf(false) }
  var showPublishHelp by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) { vm.refreshSyncState() }

  LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
    item("bar") { DetailTopBar(stringResource(R.string.account_title), onBack) }
    item("header") {
      PageHeader(
        eyebrow = "GitHub",
        title = stringResource(R.string.account_title),
        subtitle = stringResource(R.string.account_subtitle),
      )
    }

    when (val state = auth) {
      is AuthState.SignedIn -> {
        item("profile") {
          val user = state.session.user
          SurfaceCard(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(hueGradient(140f))),
                contentAlignment = Alignment.Center,
              ) {
                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onPrimary)
                if (user.avatarUrl != null) {
                  AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                  )
                }
              }
              Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                  user.name ?: user.login,
                  style = MaterialTheme.typography.titleLarge,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  "@${user.login}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                  stringResource(R.string.account_repos, user.publicRepos) + " · " + stringResource(R.string.account_followers, user.followers),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
            user.bio?.let {
              Spacer(Modifier.height(12.dp))
              Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(onClick = {
                runCatching {
                  context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(user.htmlUrl)))
                }
              }) {
                Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
                Text(stringResource(R.string.account_open_profile), Modifier.padding(start = 6.dp))
              }
              TextButton(onClick = vm::signOut) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(16.dp))
                Text(stringResource(R.string.account_sign_out), Modifier.padding(start = 6.dp))
              }
            }
          }
        }

        item("sync") {
          SectionHeader(stringResource(R.string.sync_title))
          SurfaceCard(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.sync_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
              Text(
                if (sync.lastPushedAt > 0) {
                  stringResource(
                    R.string.sync_last,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(sync.lastPushedAt)),
                  )
                } else stringResource(R.string.sync_never),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
              )
            }
            val revision = sync.revision
            if (revision != null) {
              Text(
                stringResource(R.string.sync_revision, revision.take(10)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (sync.conflict) {
              Spacer(Modifier.height(8.dp))
              Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(12.dp)) {
                  Text(stringResource(R.string.sync_conflict_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                  Text(
                    stringResource(R.string.sync_conflict_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                  )
                }
              }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
              Button(onClick = vm::pushBackup, enabled = !busy) {
                Icon(Icons.Default.CloudUpload, null, Modifier.size(18.dp))
                Text(stringResource(R.string.sync_push), Modifier.padding(start = 6.dp))
              }
              FilledTonalButton(onClick = vm::pullBackup, enabled = !busy) {
                Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                Text(stringResource(R.string.sync_pull), Modifier.padding(start = 6.dp))
              }
              if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
          }
        }

        item("scopes") {
          Spacer(Modifier.height(10.dp))
          SurfaceCard(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text("Access", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text("AuraPulse requests the gist scope and your public profile. The token is encrypted with the Android Keystore and sent only to api.github.com.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      AuthState.Restoring -> item("restoring") {
        Row(Modifier.fillMaxWidth().padding(40.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
      }

      AuthState.SignedOut -> {
        item("signin") {
          SurfaceCard(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.account_token_label), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
              stringResource(R.string.account_token_help),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
              value = token,
              onValueChange = { token = it },
              modifier = Modifier.fillMaxWidth(),
              label = { Text("ghp_… / github_pat_…") },
              singleLine = true,
              visualTransformation = if (showToken) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Hide" else "Show") }
              },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(onClick = { vm.signIn(token); token = "" }, enabled = token.isNotBlank()) {
                Text(stringResource(R.string.account_sign_in))
              }
              TextButton(onClick = {
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/settings/tokens/new?scopes=gist&description=AuraPulse"))) }
              }) { Text("Create a token") }
            }
          }
        }

        item("device") {
          Spacer(Modifier.height(12.dp))
          SectionHeader(stringResource(R.string.account_device_flow))
          SurfaceCard(Modifier.padding(horizontal = 20.dp)) {
            Text(
              stringResource(R.string.account_device_flow_help),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (BuildConfig.GITHUB_CLIENT_ID.isBlank()) {
              Text(
                stringResource(R.string.account_client_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            } else {
              Button(onClick = vm::startDeviceFlow) {
                Icon(Icons.Default.Person, null, Modifier.size(18.dp))
                Text(stringResource(R.string.account_sign_in_github), Modifier.padding(start = 6.dp))
              }
            }
          }
        }

        item("private") {
          Spacer(Modifier.height(10.dp))
          SurfaceCard(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text(
              "Listening does not require an account. Signing in only unlocks gist backup and playlist publishing.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }

  device?.let { flow ->
    AlertDialog(
      onDismissRequest = { vm.cancelDeviceFlow() },
      title = { Text(stringResource(R.string.account_device_code)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            flow.code.userCode,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(stringResource(R.string.account_device_waiting), style = MaterialTheme.typography.bodySmall)
          TextButton(onClick = {
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(flow.code.verificationUri))) }
          }) { Text(stringResource(R.string.account_device_open)) }
        }
      },
      confirmButton = { TextButton(onClick = vm::cancelDeviceFlow) { Text(stringResource(R.string.account_device_cancel)) } },
    )
  }
}
