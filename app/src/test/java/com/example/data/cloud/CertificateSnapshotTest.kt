package com.example.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class CertificateSnapshotTest {
  private val installed = "CB:2A:B0:F4:62:0E:B0:F1:87:CF:3A:D4:B4:9C:D3:C5:7B:33:0F:9D"
  private val existingConfig = "8df588ab8b8ef4b67468256d0ff03cc504d778f8"

  @Test fun mismatchingFingerprintIsOnlyReportedAsAbsentFromSnapshot() {
    assertEquals(CertificateSnapshotStatus.NOT_IN_SNAPSHOT, certificateSnapshotStatus(listOf(installed), listOf(existingConfig)))
  }

  @Test fun acceptsColonOrCaseDifferences() {
    assertEquals(CertificateSnapshotStatus.MATCH, certificateSnapshotStatus(listOf(installed), listOf("cb2ab0f4620eb0f187cf3ad4b49cd3c57b330f9d")))
  }

  @Test fun allCurrentSignersMustAppearInTheSnapshot() {
    assertEquals(CertificateSnapshotStatus.NOT_IN_SNAPSHOT, certificateSnapshotStatus(
      listOf(installed, existingConfig), listOf(existingConfig),
    ))
  }

  @Test fun noOrMalformedInformationIsUnknownNotAFalseMismatch() {
    assertEquals(CertificateSnapshotStatus.UNKNOWN, certificateSnapshotStatus(listOf(installed), emptyList()))
    assertEquals(CertificateSnapshotStatus.UNKNOWN, certificateSnapshotStatus(listOf("bad"), listOf(existingConfig)))
    assertEquals(CertificateSnapshotStatus.UNKNOWN, certificateSnapshotStatus(listOf(installed), listOf("bad")))
    assertEquals(CertificateSnapshotStatus.UNKNOWN, certificateSnapshotStatus(listOf(installed, "bad"), listOf(existingConfig)))
  }
}
