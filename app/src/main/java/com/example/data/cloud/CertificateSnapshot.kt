package com.example.data.cloud

/** Comparison against a build-time file only; it cannot query the live Firebase console. */
enum class CertificateSnapshotStatus { MATCH, NOT_IN_SNAPSHOT, UNKNOWN }

internal fun certificateSnapshotStatus(installed: List<String>, snapshot: List<String>): CertificateSnapshotStatus {
  fun normalize(value: String): String? = value.replace(":", "").replace(" ", "")
    .uppercase().takeIf { it.matches(Regex("[0-9A-F]{40}")) }
  val installedValues = installed.mapNotNull(::normalize).toSet()
  val snapshotValues = snapshot.mapNotNull(::normalize).toSet()
  if (installedValues.isEmpty() || snapshotValues.isEmpty()) return CertificateSnapshotStatus.UNKNOWN
  return if (installedValues.any { it in snapshotValues }) CertificateSnapshotStatus.MATCH else CertificateSnapshotStatus.NOT_IN_SNAPSHOT
}
