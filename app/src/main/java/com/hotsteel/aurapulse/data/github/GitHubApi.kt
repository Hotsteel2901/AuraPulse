package com.hotsteel.aurapulse.data.github

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class GitHubUser(
  val login: String,
  val id: Long,
  val name: String?,
  val avatarUrl: String?,
  val bio: String?,
  val publicRepos: Int,
  val followers: Int,
  val htmlUrl: String,
)

data class GitHubGist(
  val id: String,
  val description: String?,
  val htmlUrl: String,
  val updatedAt: String?,
  val isPublic: Boolean,
  val files: Map<String, String>,
)

data class DeviceCode(
  val deviceCode: String,
  val userCode: String,
  val verificationUri: String,
  val expiresInSeconds: Int,
  val intervalSeconds: Int,
)

sealed interface DeviceTokenResult {
  data object Pending : DeviceTokenResult
  data object SlowDown : DeviceTokenResult
  data object Denied : DeviceTokenResult
  data object Expired : DeviceTokenResult
  data class Token(val accessToken: String, val scope: String?) : DeviceTokenResult
}

class GitHubException(
  val status: Int,
  message: String,
  val documentation: String? = null,
) : IOException(message) {
  val isUnauthorized: Boolean get() = status == 401
  val isForbidden: Boolean get() = status == 403
  val isNotFound: Boolean get() = status == 404
}

/**
 * Minimal, dependency-free GitHub REST client. Only the endpoints AuraPulse
 * actually uses are implemented; every call goes straight to api.github.com.
 */
class GitHubApi(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(25, TimeUnit.SECONDS)
    .callTimeout(40, TimeUnit.SECONDS)
    .build(),
) {
  private val json = "application/json; charset=utf-8".toMediaType()

  private fun request(path: String, token: String?): Request.Builder =
    Request.Builder()
      .url(API.toHttpUrl().newBuilder().addPathSegments(path.removePrefix("/")).build())
      .header("Accept", "application/vnd.github+json")
      .header("X-GitHub-Api-Version", "2022-11-28")
      .apply { token?.let { header("Authorization", "Bearer $it") } }

  private fun Request.Builder.jsonBody(body: JSONObject) = post(body.toString().toRequestBody(json))

  private fun execute(request: Request): JSONObject = runCatching {
    client.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        val error = runCatching { JSONObject(body) }.getOrNull()
        throw GitHubException(
          status = response.code,
          message = error?.optString("message")?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}",
          documentation = error?.optString("documentation_url"),
        )
      }
      if (body.isBlank()) JSONObject() else JSONObject(body)
    }
  }.getOrElse { error ->
    if (error is GitHubException) throw error
    throw GitHubException(0, error.localizedMessage ?: error.javaClass.simpleName)
  }

  suspend fun user(token: String): GitHubUser = withContext(Dispatchers.IO) {
    val json = execute(request("user", token).get().build())
    GitHubUser(
      login = json.getString("login"),
      id = json.optLong("id"),
      name = json.optString("name").takeIf { it.isNotBlank() },
      avatarUrl = json.optString("avatar_url").takeIf { it.isNotBlank() },
      bio = json.optString("bio").takeIf { it.isNotBlank() },
      publicRepos = json.optInt("public_repos"),
      followers = json.optInt("followers"),
      htmlUrl = json.optString("html_url", "https://github.com/${json.getString("login")}"),
    )
  }

  /** Lists gists; the payload is a JSON array, so it is parsed separately. */
  suspend fun listGists(token: String, perPage: Int = 100): List<GitHubGist> = withContext(Dispatchers.IO) {
    val request = request("gists?per_page=$perPage", token).get().build()
    val body = runCatching {
      client.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          val error = runCatching { JSONObject(text) }.getOrNull()
          throw GitHubException(response.code, error?.optString("message") ?: "HTTP ${response.code}")
        }
        text
      }
    }.getOrElse { error ->
      if (error is GitHubException) throw error
      throw GitHubException(0, error.localizedMessage ?: "network")
    }
    val array = org.json.JSONArray(body)
    (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.toGist() }
  }

  suspend fun gist(token: String, id: String): GitHubGist = withContext(Dispatchers.IO) {
    execute(request("gists/$id", token).get().build()).toGist()
  }

  suspend fun createGist(token: String, description: String, files: Map<String, String>, public: Boolean): GitHubGist =
    withContext(Dispatchers.IO) {
      val fileJson = JSONObject()
      files.forEach { (name, content) -> fileJson.put(name, JSONObject().put("content", content)) }
      val payload = JSONObject()
        .put("description", description)
        .put("public", public)
        .put("files", fileJson)
      execute(request("gists", token).jsonBody(payload).build()).toGist()
    }

  suspend fun updateGist(token: String, id: String, description: String?, files: Map<String, String>?): GitHubGist =
    withContext(Dispatchers.IO) {
      val payload = JSONObject()
      description?.let { payload.put("description", it) }
      files?.let { map ->
        val fileJson = JSONObject()
        map.forEach { (name, content) -> fileJson.put(name, JSONObject().put("content", content)) }
        payload.put("files", fileJson)
      }
      execute(request("gists/$id", token).patch(payload.toString().toRequestBody(json)).build()).toGist()
    }

  suspend fun deleteGist(token: String, id: String) = withContext(Dispatchers.IO) {
    val response = client.newCall(request("gists/$id", token).delete().build()).execute()
    response.use {
      if (!it.isSuccessful && it.code != 404) throw GitHubException(it.code, "HTTP ${it.code}")
    }
  }

  suspend fun deleteGistFile(token: String, id: String, fileName: String) = withContext(Dispatchers.IO) {
    val payload = JSONObject().put("files", JSONObject().put(fileName, JSONObject.NULL))
    execute(request("gists/$id", token).patch(payload.toString().toRequestBody(json)).build())
  }

  // ------------------------------------------------------------- device flow

  suspend fun requestDeviceCode(clientId: String, scope: String): DeviceCode = withContext(Dispatchers.IO) {
    val body = FormBody.Builder().add("client_id", clientId).add("scope", scope).build()
    val request = Request.Builder().url("https://github.com/login/device/code")
      .header("Accept", "application/json").post(body).build()
    val json = runCatching {
      client.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw GitHubException(response.code, "Device code request failed")
        JSONObject(text)
      }
    }.getOrElse { error ->
      if (error is GitHubException) throw error
      throw GitHubException(0, error.localizedMessage ?: "network")
    }
    if (json.has("error")) throw GitHubException(400, json.optString("error_description", json.optString("error")))
    DeviceCode(
      deviceCode = json.getString("device_code"),
      userCode = json.getString("user_code"),
      verificationUri = json.optString("verification_uri", "https://github.com/login/device"),
      expiresInSeconds = json.optInt("expires_in", 900),
      intervalSeconds = json.optInt("interval", 5).coerceAtLeast(5),
    )
  }

  suspend fun pollDeviceToken(clientId: String, deviceCode: String): DeviceTokenResult = withContext(Dispatchers.IO) {
    val body = FormBody.Builder()
      .add("client_id", clientId)
      .add("device_code", deviceCode)
      .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
      .build()
    val request = Request.Builder().url("https://github.com/login/oauth/access_token")
      .header("Accept", "application/json").post(body).build()
    val json = client.newCall(request).execute().use { response ->
      val text = response.body?.string().orEmpty()
      if (!response.isSuccessful) throw GitHubException(response.code, "Token request failed")
      JSONObject(text)
    }
    val token = json.optString("access_token")
    if (token.isNotBlank()) return@withContext DeviceTokenResult.Token(token, json.optString("scope").takeIf { it.isNotBlank() })
    when (json.optString("error")) {
      "authorization_pending" -> DeviceTokenResult.Pending
      "slow_down" -> DeviceTokenResult.SlowDown
      "expired_token" -> DeviceTokenResult.Expired
      "access_denied" -> DeviceTokenResult.Denied
      else -> throw GitHubException(400, json.optString("error_description", "Unknown device flow error"))
    }
  }

  /** Polls until the person approves, denies or the code expires. */
  suspend fun awaitDeviceToken(clientId: String, code: DeviceCode, onTick: (DeviceTokenResult) -> Unit = {}): String {
    var interval = code.intervalSeconds
    val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
    while (System.currentTimeMillis() < deadline) {
      delay(interval * 1000L)
      when (val result = pollDeviceToken(clientId, code.deviceCode)) {
        is DeviceTokenResult.Token -> return result.accessToken
        DeviceTokenResult.Pending -> onTick(result)
        DeviceTokenResult.SlowDown -> { interval += 5; onTick(result) }
        DeviceTokenResult.Denied -> throw GitHubException(403, "Authorization denied")
        DeviceTokenResult.Expired -> throw GitHubException(410, "Device code expired")
      }
    }
    throw GitHubException(410, "Device code expired")
  }

  private fun JSONObject.toGist(): GitHubGist {
    val filesJson = optJSONObject("files")
    val files = buildMap {
      filesJson?.keys()?.forEach { key ->
        val content = filesJson.optJSONObject(key)?.optString("content").orEmpty()
        put(key, content)
      }
    }
    return GitHubGist(
      id = optString("id"),
      description = optString("description").takeIf { it.isNotBlank() },
      htmlUrl = optString("html_url"),
      updatedAt = optString("updated_at").takeIf { it.isNotBlank() },
      isPublic = optBoolean("public", false),
      files = files,
    )
  }

  private companion object {
    const val API = "https://api.github.com/"
  }
}
