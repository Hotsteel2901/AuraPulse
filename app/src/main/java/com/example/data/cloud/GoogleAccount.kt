package com.example.data.cloud

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class MissingFirebaseConfiguration : IllegalStateException()

object GoogleAccount {
  suspend fun signIn(activity: Activity): FirebaseUser {
    if (FirebaseApp.getApps(activity).isEmpty()) throw MissingFirebaseConfiguration()
    val resource = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
    val clientId = if (resource != 0) activity.getString(resource) else ""
    if (clientId.isBlank()) throw MissingFirebaseConfiguration()
    val manager = CredentialManager.create(activity)
    val primary = GetCredentialRequest.Builder()
      .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build()).build()
    val response = try {
      manager.getCredential(activity, primary)
    } catch (cancelled: GetCredentialCancellationException) {
      throw cancelled
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      // Some Play Services versions support the account selector but not the explicit button flow.
      val option = GetGoogleIdOption.Builder().setServerClientId(clientId)
        .setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
      manager.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
    }
    val credential = response.credential
    require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      "Unexpected credential type"
    }
    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
    // No local or email-only fallback: success means Firebase actually authenticated the token.
    return FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token, null))
      .await().user ?: error("Firebase returned no user")
  }

  suspend fun signOut(activity: Activity) {
    FirebaseAuth.getInstance().signOut()
    try { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { /* Firebase is already signed out. */ }
  }
}
