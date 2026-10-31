package com.example.data.cloud

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

  @Test fun bothCredentialErrorsRemainVisible() {
    val summary = GoogleAccount.describeFailure(CredentialFlowsFailed(IllegalStateException(), IllegalArgumentException()))
    assertTrue(summary.contains("IllegalStateException"))
    assertTrue(summary.contains("IllegalArgumentException"))
  }
}
