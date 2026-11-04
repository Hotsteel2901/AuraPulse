package com.example.data.cloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleOAuthSnapshotAssetTest {
  @Test fun bundledSnapshotHasOnlyPublicFingerprintMetadataForThisApplication() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val snapshot = context.assets.open("google-oauth-diagnostics.json").bufferedReader().use { JSONObject(it.readText()) }
    assertEquals(context.packageName, snapshot.getString("packageName"))
    assertTrue(snapshot.has("projectId"))
    val hashes = snapshot.getJSONArray("sha1")
    for (index in 0 until hashes.length()) assertTrue(hashes.getString(index).matches(Regex("[0-9A-F]{40}")))
    assertEquals(setOf("packageName", "projectId", "sha1"), snapshot.keys().asSequence().toSet())
    assertFalse(snapshot.has("client_id"))
    assertFalse(snapshot.has("api_key"))
  }
}
