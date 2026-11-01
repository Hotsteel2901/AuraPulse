package com.example.data.cloud

import androidx.credentials.exceptions.GetCredentialCancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAccountDiagnosticsTest {
  class FirebaseLikeError(val errorCode: String) : Exception("account@example.com password=secret token=abc status: 10")

  @Test fun diagnosticsKeepCodesWithoutLeakingProviderMessages() {
    val failure = FirebaseTokenExchangeFailed(FirebaseLikeError("ERROR_OPERATION_NOT_ALLOWED"))
    val summary = GoogleAccount.describeFailure(failure)
    assertTrue(summary.contains("ERROR_OPERATION_NOT_ALLOWED"))
    assertTrue(summary.contains("10"))
    assertFalse(summary.contains("account@example.com"))
    assertFalse(summary.contains("secret"))
    assertFalse(summary.contains("abc"))
  }

  @Test fun fallbackCancellationDoesNotHideTheOriginalButtonFailure() {
    val summary = GoogleAccount.describeFailure(CredentialFallbackCancelled(
      IllegalStateException(), GetCredentialCancellationException("cancelled"),
    ))
    assertTrue(summary.contains("IllegalStateException"))
    assertTrue(summary.contains("GetCredentialCancellationException"))
  }

  @Test fun bracketedPlayServicesStatusIsExtractedWithoutRevealingTheMessage() {
    val summary = GoogleAccount.describeFailure(GetCredentialCancellationException("[16] Account reauth failed for private@example.com"))
    assertTrue(summary.contains("16"))
    assertFalse(summary.contains("private@example.com"))
    assertFalse(summary.contains("Account reauth failed"))
  }

  @Test fun bothCredentialErrorsRemainVisible() {
    val summary = GoogleAccount.describeFailure(CredentialFlowsFailed(IllegalStateException(), IllegalArgumentException()))
    assertTrue(summary.contains("IllegalStateException"))
    assertTrue(summary.contains("IllegalArgumentException"))
  }
}
