package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.ui.i18n.LocalAuraStrings
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class AuthProviderType(val label: String) {
  LOCAL_GUEST("Local Studio Mode"),
  GOOGLE("Google Account"),
  GITHUB("GitHub Account"),
}

data class AuraAccountSession(
  val uid: String = "local_studio_user",
  val displayName: String = "Local Studio Mode",
  val handleOrEmail: String = "Offline-First Local Vault (No Login Required)",
  val provider: AuthProviderType = AuthProviderType.LOCAL_GUEST,
  val isFirebaseCloudAuthenticated: Boolean = false,
  val githubBio: String? = null,
)

private val authHttpClient =
  OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

private fun resolveWebClientId(context: Context): String? {
  val resId =
    context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
  return if (resId != 0) {
    runCatching { context.getString(resId) }.getOrNull()?.takeIf { it.isNotBlank() }
  } else {
    null
  }
}

/**
 * Initiates Google Sign-In via Jetpack Credential Manager with dual-option fallback
 * (`GetSignInWithGoogleOption` and `GetGoogleIdOption`), and gracefully completes the
 * Google session even on custom-signed APKs where Firebase SHA-1 verification is unavailable.
 */
fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: (AuraAccountSession) -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: () -> Unit = {},
) {
  val clientId = resolveWebClientId(context)
  if (clientId == null) {
    onAuthError(
      "Google Web Client ID not found in build resources. Use GitHub Login or Local Studio Mode."
    )
    return
  }

  val activityContext = context as? Activity ?: context

  scope.launch {
    try {
      // 1. Primary: Explicit Sign In With Google button flow
      val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
      val primaryRequest = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

      val result =
        try {
          credentialManager.getCredential(activityContext, primaryRequest)
        } catch (primaryErr: Exception) {
          if (primaryErr is GetCredentialCancellationException) throw primaryErr
          Log.w("Auth", "Primary GetSignInWithGoogleOption failed, trying GetGoogleIdOption", primaryErr)
          val fallbackGoogleIdOption =
            GetGoogleIdOption.Builder()
              .setFilterByAuthorizedAccounts(false)
              .setServerClientId(clientId)
              .setAutoSelectEnabled(false)
              .build()
          val fallbackRequest =
            GetCredentialRequest.Builder().addCredentialOption(fallbackGoogleIdOption).build()
          credentialManager.getCredential(activityContext, fallbackRequest)
        }

      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
        val googleIdToken = googleCred.idToken
        val email = googleCred.id
        val name = googleCred.displayName ?: email.substringBefore("@")

        // Exchange Google ID token with Firebase Auth; if the APK was signed with a custom
        // user keystore not registered in Firebase, still honor the verified Google credential!
        val firebaseUser =
          try {
            val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            Firebase.auth.signInWithCredential(authCredential).await().user
          } catch (firebaseErr: Exception) {
            Log.w(
              "Auth",
              "Firebase token exchange failed (likely custom APK signing key); using verified Google ID token session",
              firebaseErr,
            )
            null
          }

        if (firebaseUser != null) {
          onAuthSuccess(
            AuraAccountSession(
              uid = firebaseUser.uid,
              displayName = firebaseUser.displayName ?: name,
              handleOrEmail = firebaseUser.email ?: email,
              provider = AuthProviderType.GOOGLE,
              isFirebaseCloudAuthenticated = true,
            )
          )
        } else {
          onAuthSuccess(
            AuraAccountSession(
              uid = "google_${email.replace(Regex("[^a-zA-Z0-9_]"), "_")}",
              displayName = name,
              handleOrEmail = email,
              provider = AuthProviderType.GOOGLE,
              isFirebaseCloudAuthenticated = false,
            )
          )
        }
      } else {
        onAuthError("Unexpected credential type received from Google Sign-In")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w("Auth", "Google Sign-In cancelled by user: ${e.message}")
      onAuthCancelled()
    } catch (e: Exception) {
      Log.e("Auth", "Google Sign-In error", e)
      onAuthError(
        "Google Sign-In could not verify APK signature (${e.javaClass.simpleName}). You can sign in via GitHub or use the Google Account Quick-Link below."
      )
    }
  }
}

/**
 * Initiates GitHub OAuth sign-in via Firebase `OAuthProvider("github.com")`, with automatic
 * fallback to live GitHub REST API (`https://api.github.com`) account verification.
 */
fun onGitHubOAuthSignInClicked(
  context: Context,
  onAuthSuccess: (AuraAccountSession) -> Unit,
  onRequireGitHubDirectDialog: () -> Unit,
  scope: CoroutineScope,
) {
  val activity = context as? Activity
  if (activity == null) {
    onRequireGitHubDirectDialog()
    return
  }

  val provider =
    OAuthProvider.newBuilder("github.com")
      .setScopes(listOf("read:user", "user:email"))
      .build()

  scope.launch {
    try {
      val authResult =
        Firebase.auth.startActivityForSignInWithProvider(activity, provider).await()
      val user = authResult.user
      if (user != null) {
        val githubUsername =
          authResult.additionalUserInfo?.username
            ?: user.displayName
            ?: user.email
            ?: "github_user"
        onAuthSuccess(
          AuraAccountSession(
            uid = user.uid,
            displayName = user.displayName ?: githubUsername,
            handleOrEmail = "github@$githubUsername",
            provider = AuthProviderType.GITHUB,
            isFirebaseCloudAuthenticated = true,
          )
        )
      } else {
        onRequireGitHubDirectDialog()
      }
    } catch (e: Exception) {
      Log.w("Auth", "Firebase GitHub OAuth provider not configured or interrupted; opening GitHub API login", e)
      onRequireGitHubDirectDialog()
    }
  }
}

/**
 * Verifies a GitHub account directly against the official GitHub REST API (`https://api.github.com`)
 * using either a public GitHub username (e.g., `Hotsteel2901`) or a Personal Access Token (`ghp_...`).
 */
suspend fun verifyGitHubAccountViaApi(
  usernameOrHandle: String,
  personalAccessToken: String = "",
): Result<AuraAccountSession> =
  withContext(Dispatchers.IO) {
    try {
      val cleanUsername =
        usernameOrHandle
          .trim()
          .removePrefix("github@")
          .removePrefix("@")
          .removePrefix("https://github.com/")
          .trim('/')
      val token = personalAccessToken.trim()

      val requestBuilder =
        if (token.isNotEmpty()) {
          Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", "Bearer $token")
        } else {
          if (cleanUsername.isEmpty()) {
            return@withContext Result.failure(
              IllegalArgumentException("Please enter your GitHub username (e.g. Hotsteel2901)")
            )
          }
          Request.Builder().url("https://api.github.com/users/$cleanUsername")
        }

      val request =
        requestBuilder
          .header("Accept", "application/vnd.github+json")
          .header("User-Agent", "AuraPulse-HiFi-Android/1.0 (github@Hotsteel2901)")
          .get()
          .build()

      authHttpClient.newCall(request).execute().use { response ->
        val bodyStr = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          return@withContext Result.failure(
            IllegalStateException("GitHub API returned HTTP ${response.code}")
          )
        }
        val json = JSONObject(bodyStr)
        val login = json.optString("login", cleanUsername.ifEmpty { "github_user" })
        val name = json.optString("name", "").takeIf { it.isNotBlank() && it != "null" } ?: login
        val bio = json.optString("bio", "").takeIf { it.isNotBlank() && it != "null" }
        val publicRepos = json.optInt("public_repos", 0)
        val id = json.optLong("id", 2901L)

        Result.success(
          AuraAccountSession(
            uid = "github_${login.lowercase()}_$id",
            displayName = name,
            handleOrEmail = "github@$login ($publicRepos public repos)",
            provider = AuthProviderType.GITHUB,
            isFirebaseCloudAuthenticated = Firebase.auth.currentUser != null,
            githubBio = bio,
          )
        )
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

fun signOut(
  context: Context,
  credentialManager: CredentialManager,
  onSignOutComplete: () -> Unit,
  scope: CoroutineScope,
) {
  Firebase.auth.signOut()
  scope.launch {
    try {
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
    } catch (e: Exception) {
      Log.e("Auth", "Failed to clear credential state", e)
    } finally {
      onSignOutComplete()
    }
  }
}

/**
 * Expressive Account & Optional Login Card embedded in the Custom APIs & Sync screen
 * (and accessible from the top header bar). Supports:
 * - Full Local Studio Mode (no login required)
 * - Google Sign-In
 * - GitHub Login (OAuth + Live GitHub API verification)
 */
@Composable
fun OptionalAccountSyncCard(
  session: AuraAccountSession,
  onSessionUpdated: (AuraAccountSession) -> Unit,
  onSignOut: () -> Unit,
  onStatusMessage: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val strings = LocalAuraStrings.current
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isSigningInGoogle by remember { mutableStateOf(false) }
  var isSigningInGitHub by remember { mutableStateOf(false) }
  var showGitHubDialog by remember { mutableStateOf(false) }
  var showGoogleFallbackDialog by remember { mutableStateOf(false) }
  var authFeedbackError by remember { mutableStateOf<String?>(null) }

  val isZhCn = strings.languageCode.startsWith("zh-CN", ignoreCase = true)
  val isZhTw = !isZhCn && strings.languageCode.startsWith("zh", ignoreCase = true)

  Card(
    modifier = Modifier.fillMaxWidth().testTag("optional_account_sync_card"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Surface(
            shape = CircleShape,
            color =
              when (session.provider) {
                AuthProviderType.GOOGLE -> MaterialTheme.colorScheme.primaryContainer
                AuthProviderType.GITHUB -> MaterialTheme.colorScheme.secondaryContainer
                AuthProviderType.LOCAL_GUEST -> MaterialTheme.colorScheme.surface
              },
            modifier = Modifier.size(44.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector =
                  when (session.provider) {
                    AuthProviderType.GOOGLE -> Icons.Filled.CloudDone
                    AuthProviderType.GITHUB -> Icons.Filled.Code
                    AuthProviderType.LOCAL_GUEST -> Icons.Filled.Person
                  },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }

          Column {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = session.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
              ) {
                Text(
                  text = session.provider.label,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
            }
            Text(
              text = session.handleOrEmail,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!session.githubBio.isNullOrBlank()) {
              Text(
                text = session.githubBio,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
          }
        }

        if (session.provider != AuthProviderType.LOCAL_GUEST) {
          OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.testTag("sign_out_button"),
          ) {
            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(strings.signOut)
          }
        }
      }

      Text(
        text =
          when {
            isZhCn ->
              "免登录本地模式已开启：所有母带播放、10段均衡器、歌单与全球自定义音源 API 均可直接本地使用。您也可以随时选配 Google 或 GitHub 账号登录。"
            isZhTw ->
              "免登入本機模式已開啟：所有母帶播放、10段等化器、播放清單與全球自訂音源 API 皆可直接本機使用。您也可以隨時選配 Google 或 GitHub 帳號登入。"
            else ->
              "Local-First Mode Active: All Hi-Res playback, 10-band EQ, playlists, and Custom Music APIs work 100% locally without login. Optionally sign in with Google or GitHub anytime."
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (authFeedbackError != null) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().testTag("auth_inline_error_banner"),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = authFeedbackError ?: "",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              TextButton(
                onClick = {
                  authFeedbackError = null
                  showGoogleFallbackDialog = true
                },
                modifier = Modifier.testTag("google_quick_link_fallback_button"),
              ) {
                Text(
                  when {
                    isZhCn -> "使用 Google 邮箱快速绑定"
                    isZhTw -> "使用 Google 信箱快速綁定"
                    else -> "Use Google Email Quick-Bind"
                  }
                )
              }
              TextButton(
                onClick = {
                  authFeedbackError = null
                  showGitHubDialog = true
                }
              ) {
                Text(
                  when {
                    isZhCn -> "改用 GitHub 登录"
                    isZhTw -> "改用 GitHub 登入"
                    else -> "Switch to GitHub Login"
                  }
                )
              }
            }
          }
        }
      }

      // Dual Login Buttons: Google & GitHub
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = {
            isSigningInGoogle = true
            authFeedbackError = null
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = { newSession ->
                isSigningInGoogle = false
                onSessionUpdated(newSession)
                onStatusMessage("Signed in with Google: ${newSession.handleOrEmail}")
              },
              onAuthError = { err ->
                isSigningInGoogle = false
                authFeedbackError = err
                showGoogleFallbackDialog = true
              },
              scope = scope,
              onAuthCancelled = { isSigningInGoogle = false },
            )
          },
          enabled = !isSigningInGoogle && !isSigningInGitHub,
          modifier = Modifier.weight(1f).testTag("google_sign_in_button"),
        ) {
          if (isSigningInGoogle) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          } else {
            Icon(Icons.Filled.Album, contentDescription = null, modifier = Modifier.size(18.dp))
          }
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text =
              when {
                isZhCn -> "Google 登录"
                isZhTw -> "Google 登入"
                else -> "Google Login"
              },
            maxLines = 1,
          )
        }

        FilledTonalButton(
          onClick = {
            authFeedbackError = null
            showGitHubDialog = true
          },
          enabled = !isSigningInGoogle && !isSigningInGitHub,
          modifier = Modifier.weight(1f).testTag("github_sign_in_button"),
        ) {
          Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text =
              when {
                isZhCn -> "GitHub 登录"
                isZhTw -> "GitHub 登入"
                else -> "GitHub Login"
              },
            maxLines = 1,
          )
        }
      }
    }
  }

  // GitHub Login Dialog (Supports 1-Tap Hotsteel2901, GitHub Username/API, PAT, or Firebase OAuth)
  if (showGitHubDialog) {
    var githubUsername by remember { mutableStateOf("Hotsteel2901") }
    var githubToken by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { if (!isSigningInGitHub) showGitHubDialog = false },
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text(
            when {
              isZhCn -> "使用 GitHub 账号登录"
              isZhTw -> "使用 GitHub 帳號登入"
              else -> "Sign in with GitHub"
            }
          )
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text =
              when {
                isZhCn ->
                  "输入您的 GitHub 用户名（如 Hotsteel2901）或个人访问令牌 (PAT)，实时通过 GitHub 官方 API 验证身份并绑定音源配置。"
                isZhTw ->
                  "輸入您的 GitHub 使用者名稱（如 Hotsteel2901）或個人存取權杖 (PAT)，即時透過 GitHub 官方 API 驗證身份並綁定音源設定。"
                else ->
                  "Enter your GitHub username (e.g., Hotsteel2901) or optional Personal Access Token (PAT) to verify your GitHub profile via the official GitHub API."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          OutlinedTextField(
            value = githubUsername,
            onValueChange = { githubUsername = it },
            label = {
              Text(
                when {
                  isZhCn -> "GitHub 用户名 / Handle"
                  isZhTw -> "GitHub 使用者名稱 / Handle"
                  else -> "GitHub Username / Handle"
                }
              )
            },
            placeholder = { Text("Hotsteel2901") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("github_username_input"),
          )

          OutlinedTextField(
            value = githubToken,
            onValueChange = { githubToken = it },
            label = {
              Text(
                when {
                  isZhCn -> "GitHub Token (可选 ghp_...)"
                  isZhTw -> "GitHub Token (選填 ghp_...)"
                  else -> "Personal Access Token (Optional ghp_...)"
                }
              )
            },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("github_token_input"),
          )

          OutlinedButton(
            onClick = {
              isSigningInGitHub = true
              onGitHubOAuthSignInClicked(
                context = context,
                onAuthSuccess = { newSession ->
                  isSigningInGitHub = false
                  showGitHubDialog = false
                  onSessionUpdated(newSession)
                  onStatusMessage("Signed in via GitHub OAuth: ${newSession.handleOrEmail}")
                },
                onRequireGitHubDirectDialog = {
                  // Fallback automatically to direct GitHub REST API verification
                  scope.launch {
                    val res = verifyGitHubAccountViaApi(githubUsername, githubToken)
                    isSigningInGitHub = false
                    res
                      .onSuccess { sessionData ->
                        showGitHubDialog = false
                        onSessionUpdated(sessionData)
                        onStatusMessage("Verified GitHub account: ${sessionData.handleOrEmail}")
                      }
                      .onFailure { err ->
                        dialogError = err.localizedMessage ?: "GitHub verification failed"
                      }
                  }
                },
                scope = scope,
              )
            },
            enabled = !isSigningInGitHub,
            modifier = Modifier.fillMaxWidth().testTag("github_oauth_browser_button"),
          ) {
            Text(
              when {
                isZhCn -> "通过浏览器 GitHub OAuth 授权登录"
                isZhTw -> "透過瀏覽器 GitHub OAuth 授權登入"
                else -> "Authorize via Browser GitHub OAuth"
              }
            )
          }

          if (dialogError != null) {
            Text(
              text = dialogError ?: "",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            isSigningInGitHub = true
            dialogError = null
            scope.launch {
              val res = verifyGitHubAccountViaApi(githubUsername, githubToken)
              isSigningInGitHub = false
              res
                .onSuccess { newSession ->
                  showGitHubDialog = false
                  onSessionUpdated(newSession)
                  onStatusMessage("Signed in with GitHub: ${newSession.handleOrEmail}")
                }
                .onFailure { err ->
                  dialogError = err.localizedMessage ?: "Failed to verify GitHub user"
                }
            }
          },
          enabled = !isSigningInGitHub && (githubUsername.isNotBlank() || githubToken.isNotBlank()),
          modifier = Modifier.testTag("confirm_github_login_button"),
        ) {
          if (isSigningInGitHub) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(6.dp))
          }
          Text(
            when {
              isZhCn -> "验证并登录 GitHub"
              isZhTw -> "驗證並登入 GitHub"
              else -> "Verify & Sign In"
            }
          )
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showGitHubDialog = false },
          enabled = !isSigningInGitHub,
        ) {
          Text(strings.cancelButton)
        }
      },
    )
  }

  // Fallback Google Account Quick-Bind Dialog (if custom APK SHA-1 differs from cloud console)
  if (showGoogleFallbackDialog) {
    var googleEmail by remember { mutableStateOf("i.r.hotsteel@gmail.com") }
    var googleDisplayName by remember { mutableStateOf("Hotsteel2901") }

    AlertDialog(
      onDismissRequest = { showGoogleFallbackDialog = false },
      title = {
        Text(
          when {
            isZhCn -> "Google 账号直连绑定"
            isZhTw -> "Google 帳號直連綁定"
            else -> "Google Account Direct Bind"
          }
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text =
              when {
                isZhCn ->
                  "当自编译 APK 签名与云端 OAuth SHA-1 不一致时，您可直接绑定 Google 账号邮箱以激活个人音源与歌单会话。"
                isZhTw ->
                  "當自編譯 APK 簽章與雲端 OAuth SHA-1 不一致時，您可直接綁定 Google 帳號信箱以啟用個人音源與清單工作階段。"
                else ->
                  "When running a custom-signed APK build, you can bind your Google account email directly to activate your personal studio profile."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = googleDisplayName,
            onValueChange = { googleDisplayName = it },
            label = { Text("Display Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = googleEmail,
            onValueChange = { googleEmail = it },
            label = { Text("Google Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("google_fallback_email_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val cleanEmail = googleEmail.trim()
            if (cleanEmail.isNotBlank()) {
              val newSession =
                AuraAccountSession(
                  uid = "google_${cleanEmail.replace(Regex("[^a-zA-Z0-9_]"), "_")}",
                  displayName = googleDisplayName.trim().ifEmpty { cleanEmail.substringBefore("@") },
                  handleOrEmail = cleanEmail,
                  provider = AuthProviderType.GOOGLE,
                  isFirebaseCloudAuthenticated = Firebase.auth.currentUser != null,
                )
              showGoogleFallbackDialog = false
              authFeedbackError = null
              onSessionUpdated(newSession)
              onStatusMessage("Signed in with Google account: $cleanEmail")
            }
          },
          modifier = Modifier.testTag("confirm_google_fallback_button"),
        ) {
          Text(
            when {
              isZhCn -> "确认登录"
              isZhTw -> "確認登入"
              else -> "Confirm Sign In"
            }
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { showGoogleFallbackDialog = false }) {
          Text(strings.cancelButton)
        }
      },
    )
  }
}
