package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuraFirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createAndObservePlaylist_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = AuraFirestoreRepository(firestore)

    val createResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createPlaylist(
          title = "Audiophile Reference 24/192",
          description = "Mastered lossless selection",
          accentHex = "#00F5D4",
          sourceService = "CUSTOM_API",
          isOfflinePinned = true,
          trackIds = listOf("trk_neon_01", "trk_solar_02"),
        )
      }
    assertTrue(
      "Expected playlist creation to succeed: ${createResult.exceptionOrNull()}",
      createResult.isSuccess,
    )
    val playlistId = createResult.getOrThrow()

    val emitted =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observePlaylists(uid).first { list -> list.any { it.id == playlistId } }
      }
    assertTrue(emitted.any { it.id == playlistId && it.title == "Audiophile Reference 24/192" })
  }

  @Test
  fun createAndObserveCustomSource_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = AuraFirestoreRepository(firestore)

    val createResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createCustomSource(
          name = "Tokyo Lossless Subsonic Node",
          baseUrl = "https://hifi.example.jp",
          searchPath = "/rest/search3.view",
          protocolType = "SUBSONIC_NAVIDROME",
          regionCode = "JP",
          customHeaderName = "X-Api-Token",
          customHeaderValue = "audiophile_token",
          preferredCodec = "FLAC 24-Bit/192kHz",
          isEnabled = true,
        )
      }
    assertTrue(
      "Expected custom source creation to succeed: ${createResult.exceptionOrNull()}",
      createResult.isSuccess,
    )
    val sourceId = createResult.getOrThrow()

    val sources =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeCustomSources(uid).first { list -> list.any { it.id == sourceId } }
      }
    assertTrue(sources.any { it.id == sourceId && it.regionCode == "JP" })
  }

  @Test
  fun createSoundProfile_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = AuraFirestoreRepository(firestore)

    val createResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createSoundProfile(
          name = "Warm Tube Amp",
          presetCategory = "WARM_TUBE",
          bandsDb = listOf(25L, 20L, 15L, 5L, 0L, -5L, 10L, 15L, 20L, 15L),
          preampGainTenths = -15L,
          bassBoostPercent = 30L,
          spatializerStrength = 45L,
          dacBitPerfectMode = true,
          isActive = true,
        )
      }
    assertTrue(
      "Expected sound profile creation to succeed: ${createResult.exceptionOrNull()}",
      createResult.isSuccess,
    )
    val profileId = createResult.getOrThrow()

    val profiles =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeSoundProfiles(uid).first { list -> list.any { it.id == profileId } }
      }
    assertTrue(profiles.any { it.id == profileId && it.presetCategory == "WARM_TUBE" })
  }

  @Test
  fun getPlaylistById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = AuraFirestoreRepository(firestore)
    val playlistId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo
          .createPlaylist(
            title = "Alice Secret Studio Session",
            description = "Private FLAC mix",
          )
          .getOrThrow()
      }

    signInTestUser(BOB_EMAIL)
    val bobRepo = AuraFirestoreRepository(firestore)
    val crossReadResult =
      withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getPlaylistById(aliceUid, playlistId) }
    assertTrue("Expected cross-user access to fail", crossReadResult.isFailure)
    val firestoreException = crossReadResult.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull("Expected FirebaseFirestoreException", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
  }

  @Test
  fun observePlaylists_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()
    val repo = AuraFirestoreRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observePlaylists(aliceUid).first() }
      fail("Expected unauthenticated observePlaylists to throw exception")
    } catch (e: Exception) {
      val firestoreException = e.unwrapFirestoreException()
      assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice_aura@test.com"
    const val BOB_EMAIL = "bob_aura@test.com"
    const val DEFAULT_TIMEOUT_MS = 10000L
    const val FLOW_TIMEOUT_MS = 6000L
  }
}
