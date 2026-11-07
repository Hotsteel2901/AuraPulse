package com.hotsteel.aurapulse.data.github

import android.content.Context
import com.hotsteel.aurapulse.core.SecureStore
import com.hotsteel.aurapulse.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class GitHubSession(
  val token: String,
  val user: GitHubUser,
  val scope: String? = null,
)

sealed interface AuthState {
  data object SignedOut : AuthState
  data object Restoring : AuthState
  data class SignedIn(val session: GitHubSession) : AuthState
}

/**
 * Owns the GitHub identity. The token lives in the Android Keystore-backed
 * [SecureStore]; only the public profile is cached in plain preferences so the
 * UI can render instantly on a cold start.
 */
class GitHubAccount(context: Context) {
  private val app = context.applicationContext
  private val api = GitHubApi()
  private val secure = SecureStore(app, "github_token")
  private val cache = app.getSharedPreferences("aurapulse_github", Context.MODE_PRIVATE)
  private val scope = kotlinx.coroutines.CoroutineScope(
    kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
  )
  private val mutable = MutableStateFlow<AuthState>(AuthState.Restoring)
  val state: StateFlow<AuthState> = mutable.asStateFlow()

  val session: GitHubSession? get() = (mutable.value as? AuthState.SignedIn)?.session

  init {
    // Never touch the Android Keystore from Application.onCreate's main thread.
    // Unwrapping the token is a keystore-daemon binder call; on a device whose
    // keystore is slow (or wedged, as vendor builds can be) it delayed the first
    // frame for seconds. The UI already renders AuthState.Restoring.
    scope.launch {
      val token = secure.get()
      if (token.isNullOrBlank()) {
        mutable.value = AuthState.SignedOut
      } else {
        // Render the cached profile immediately, then refresh it in the background.
        val cached = readCache()
        mutable.value = if (cached != null) AuthState.SignedIn(GitHubSession(token, cached)) else AuthState.Restoring
        refresh(token)
      }
    }
  }

  private fun readCache(): GitHubUser? = runCatching {
    val json = JSONObject(cache.getString("user", "{}") ?: "{}")
    val login = json.optString("login").takeIf { it.isNotBlank() } ?: error("no cached login")
    GitHubUser(
      login = login, id = json.optLong("id"), name = json.optString("name").takeIf { it.isNotBlank() },
      avatarUrl = json.optString("avatar_url").takeIf { it.isNotBlank() }, bio = json.optString("bio").takeIf { it.isNotBlank() },
      publicRepos = json.optInt("public_repos"), followers = json.optInt("followers"),
      htmlUrl = json.optString("html_url", "https://github.com/$login"),
    )
  }.getOrNull()

  private fun cacheUser(user: GitHubUser) {
    val json = JSONObject()
      .put("login", user.login).put("id", user.id).put("name", user.name.orEmpty())
      .put("avatar_url", user.avatarUrl.orEmpty()).put("bio", user.bio.orEmpty())
      .put("public_repos", user.publicRepos).put("followers", user.followers).put("html_url", user.htmlUrl)
    cache.edit().putString("user", json.toString()).apply()
  }

  /** Verifies the token against /user before persisting it. */
  suspend fun signIn(token: String): GitHubSession {
    val clean = token.trim()
    require(clean.isNotEmpty()) { "empty token" }
    val user = api.user(clean)
    secure.put(clean)
    cacheUser(user)
    val session = GitHubSession(clean, user)
    mutable.value = AuthState.SignedIn(session)
    return session
  }

  suspend fun signInWithDeviceCode(
    clientId: String,
    scope: String = "gist read:user",
    onCode: (DeviceCode) -> Unit,
  ): GitHubSession {
    val code = api.requestDeviceCode(clientId, scope)
    onCode(code)
    val token = api.awaitDeviceToken(clientId, code)
    return signIn(token)
  }

  suspend fun refresh(token: String? = null) {
    val value = token ?: session?.token ?: return
    try {
      val user = api.user(value)
      cacheUser(user)
      mutable.value = AuthState.SignedIn(GitHubSession(value, user))
    } catch (e: CancellationException) {
      throw e
    } catch (e: GitHubException) {
      if (e.isUnauthorized) {
        // The token was revoked on GitHub's side: drop it instead of pretending.
        signOut()
      } else if (mutable.value is AuthState.Restoring) {
        mutable.value = AuthState.SignedOut
      }
    } catch (_: Exception) {
      if (mutable.value is AuthState.Restoring) {
        val cached = readCache()
        mutable.value = if (cached != null) AuthState.SignedIn(GitHubSession(value, cached)) else AuthState.SignedOut
      }
    }
  }

  fun signOut() {
    secure.clear()
    cache.edit().clear().apply()
    mutable.value = AuthState.SignedOut
  }

  fun api(): GitHubApi = api

  fun requireSession(): GitHubSession =
    session ?: throw IllegalStateException("not signed in")

  fun setScope(scope: String?) {
    session?.let { mutable.value = AuthState.SignedIn(it.copy(scope = scope)) }
  }

  companion object {
    /** Scopes AuraPulse asks for: gists only, plus the profile read that every token allows. */
    const val SCOPES = "gist read:user"
  }
}
