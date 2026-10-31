package com.example.data.cloud

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class MissingFirebaseConfiguration : IllegalStateException()
class CredentialFlowsFailed(val buttonError: Throwable, val selectorError: Throwable) : Exception("Google credential flows failed", selectorError)
class CredentialFallbackCancelled(val buttonError: Throwable, val selectorError: GetCredentialCancellationException) : Exception("Google account selector cancelled", selectorError)
class FirebaseTokenExchangeFailed(cause: Throwable) : Exception("Firebase token exchange failed", cause)

data class GoogleSignInDiagnostics(
  val packageName: String,
  val projectId: String?,
  val webClientConfigured: Boolean,
  val playServicesStatus: Int?,
  val sha1: List<String>,
  val sha256: List<String>,
)

object GoogleAccount {
  /** Both choices still require a Google ID token AND a successful Firebase token exchange. */
  suspend fun signIn(activity: Activity, useAccountSelector: Boolean = false): FirebaseUser {
    if (FirebaseApp.getApps(activity).isEmpty()) throw MissingFirebaseConfiguration()
    val resource = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
    val clientId = if (resource != 0) activity.getString(resource) else ""
    if (clientId.isBlank()) throw MissingFirebaseConfiguration()
    val manager = CredentialManager.create(activity)
    val selector = GetGoogleIdOption.Builder().setServerClientId(clientId)
      .setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
    val selectorRequest = GetCredentialRequest.Builder().addCredentialOption(selector).build()
    val response = if (useAccountSelector) {
      manager.getCredential(activity, selectorRequest)
    } else {
      val primary = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build()).build()
      try {
        manager.getCredential(activity, primary)
      } catch (cancelled: GetCredentialCancellationException) {
        throw cancelled
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (buttonError: Exception) {
        // Preserve both errors; the fallback used to silently discard the original failure.
        try { manager.getCredential(activity, selectorRequest) }
        catch (cancelled: GetCredentialCancellationException) { throw CredentialFallbackCancelled(buttonError, cancelled) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (selectorError: Exception) { throw CredentialFlowsFailed(buttonError, selectorError) }
      }
    }
    val credential = response.credential
    require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      "Unexpected credential type"
    }
    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
    try {
      return FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token, null))
        .await().user ?: error("Firebase returned no user")
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { throw FirebaseTokenExchangeFailed(error) }
  }

  /** Includes only public app/certificate configuration, never a token or account secret. */
  @Suppress("DEPRECATION")
  fun diagnostics(context: Context): GoogleSignInDiagnostics {
    val signatures = runCatching {
      if (Build.VERSION.SDK_INT >= 28) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
          .signingInfo?.apkContentsSigners?.toList().orEmpty()
      } else {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
          .signatures?.toList().orEmpty()
      }
    }.getOrDefault(emptyList())
    fun digest(algorithm: String) = signatures.map { signature ->
      MessageDigest.getInstance(algorithm).digest(signature.toByteArray()).joinToString(":") { "%02X".format(it.toInt() and 0xff) }
    }
    val resource = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    return GoogleSignInDiagnostics(
      packageName = context.packageName,
      projectId = runCatching { FirebaseApp.getInstance().options.projectId }.getOrNull(),
      webClientConfigured = resource != 0 && context.getString(resource).isNotBlank(),
      playServicesStatus = runCatching { GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context) }.getOrNull(),
      sha1 = digest("SHA-1"), sha256 = digest("SHA-256"),
    )
  }

  fun describeFailure(error: Throwable): String {
    fun detail(cause: Throwable): String {
      val code = when (cause) {
        is GetCredentialException -> cause.type
        else -> runCatching {
          cause.javaClass.methods.firstOrNull { it.name in setOf("getErrorCode", "getStatusCode") && it.parameterCount == 0 }
            ?.invoke(cause)?.toString()
        }.getOrNull()
      }
      // Provider exception messages may contain account data or tokens. Keep only structured
      // error types/codes and a numeric status extracted from the message, never the message itself.
      val numericStatus = cause.localizedMessage?.let {
        Regex("(?i)\\b(?:code|status|error)[ =:]+(\\d{1,4})\\b").find(it)?.groupValues?.get(1)
      }
      val parts = listOfNotNull(cause.javaClass.simpleName, code?.takeIf(String::isNotBlank), numericStatus)
      return parts.joinToString(": ").take(300)
    }
    return when (error) {
      is CredentialFlowsFailed -> "Button: ${detail(error.buttonError)}\nAccount selector: ${detail(error.selectorError)}"
      is CredentialFallbackCancelled -> "Button: ${detail(error.buttonError)}\nAccount selector: ${detail(error.selectorError)}"
      is FirebaseTokenExchangeFailed -> "Firebase: ${detail(error.cause ?: error)}"
      else -> detail(error)
    }
  }

  suspend fun signOut(activity: Activity) {
    FirebaseAuth.getInstance().signOut()
    try { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { /* Firebase is already signed out. */ }
  }
}
