package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CustomMusicSource
import com.example.data.model.Playlist
import com.example.data.model.SoundProfile
import com.example.data.model.SyncState
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AuraFirestoreRepository(private val db: FirebaseFirestore) {

  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth
    get() = Firebase.auth

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException(
        "User must be signed in with Google before accessing Firestore."
      )
  }

  // --- PLAYLISTS ---

  fun observePlaylists(
    userId: String = auth.currentUser?.uid ?: "unauthenticated"
  ): Flow<List<Playlist>> {
    val path = "users/$userId/playlists"
    return db
      .collection("users")
      .document(userId)
      .collection("playlists")
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(Playlist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  suspend fun getUserPlaylists(): Result<List<Playlist>> {
    val uid = requireUserId()
    val path = "users/$uid/playlists"
    return try {
      val snapshot =
        db
          .collection("users")
          .document(uid)
          .collection("playlists")
          .whereEqualTo("userId", uid)
          .get()
          .await()
      Result.success(
        snapshot.toObjects(Playlist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, path)
      Result.failure(e)
    }
  }

  suspend fun getPlaylistById(ownerUid: String, playlistId: String): Result<Playlist?> {
    val path = "users/$ownerUid/playlists/$playlistId"
    return try {
      val doc =
        db
          .collection("users")
          .document(ownerUid)
          .collection("playlists")
          .document(playlistId)
          .get()
          .await()
      Result.success(
        doc.toObject(Playlist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      Result.failure(e)
    }
  }

  suspend fun createPlaylist(
    title: String,
    description: String,
    accentHex: String = "#00F5D4",
    sourceService: String = "AURA_HIFI",
    isOfflinePinned: Boolean = false,
    trackIds: List<String> = emptyList(),
    customId: String? = null,
  ): Result<String> {
    val uid = requireUserId()
    val docId = customId ?: "pl_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = db.collection("users").document(uid).collection("playlists").document(docId)
    val allowedServices = setOf("AURA_HIFI", "CUSTOM_API", "SUBSONIC", "OPEN_GLOBAL", "HYBRID")
    val safeService = if (sourceService in allowedServices) sourceService else "CUSTOM_API"
    val payload =
      mapOf(
        "userId" to uid,
        "title" to title.trim().take(80).ifEmpty { "Untitled Mix" },
        "description" to description.trim().take(240),
        "accentHex" to accentHex.take(9),
        "sourceService" to safeService,
        "isOfflinePinned" to isOfflinePinned,
        "trackIds" to trackIds.take(100),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.set(payload).await()
      Result.success(docId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updatePlaylist(playlist: Playlist): Result<Unit> {
    val uid = requireUserId()
    require(playlist.id.isNotBlank()) { "Playlist ID must not be blank for update" }
    requireNotNull(playlist.createdAt) { "Playlist createdAt must not be null for update" }
    val docRef = db.collection("users").document(uid).collection("playlists").document(playlist.id)
    return try {
      docRef
        .set(
          playlist.copy(
            userId = uid,
            title = playlist.title.trim().take(80).ifEmpty { "Untitled Mix" },
            description = playlist.description.trim().take(240),
            trackIds = playlist.trackIds.take(100),
            updatedAt = null,
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deletePlaylist(playlistId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("playlists").document(playlistId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- CUSTOM MUSIC SOURCE APIs (USER-EXTENSIBLE GLOBAL SOURCES) ---

  fun observeCustomSources(
    userId: String = auth.currentUser?.uid ?: "unauthenticated"
  ): Flow<List<CustomMusicSource>> {
    val path = "users/$userId/customSources"
    return db
      .collection("users")
      .document(userId)
      .collection("customSources")
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          CustomMusicSource::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  suspend fun getUserCustomSources(): Result<List<CustomMusicSource>> {
    val uid = requireUserId()
    val path = "users/$uid/customSources"
    return try {
      val snapshot =
        db
          .collection("users")
          .document(uid)
          .collection("customSources")
          .whereEqualTo("userId", uid)
          .get()
          .await()
      Result.success(
        snapshot.toObjects(
          CustomMusicSource::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, path)
      Result.failure(e)
    }
  }

  suspend fun createCustomSource(
    name: String,
    baseUrl: String,
    searchPath: String,
    protocolType: String,
    regionCode: String,
    customHeaderName: String = "",
    customHeaderValue: String = "",
    preferredCodec: String = "FLAC Hi-Res",
    isEnabled: Boolean = true,
    customId: String? = null,
  ): Result<String> {
    val uid = requireUserId()
    val docId = customId ?: "src_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = db.collection("users").document(uid).collection("customSources").document(docId)
    val allowedProtocols =
      setOf(
        "OPEN_REST_JSON",
        "SUBSONIC_NAVIDROME",
        "RADIO_BROWSER_GLOBAL",
        "ARCHIVE_LOSSLESS",
        "JELLYFIN_AUDIO",
      )
    val safeProtocol = if (protocolType in allowedProtocols) protocolType else "OPEN_REST_JSON"
    val normalizedUrl =
      baseUrl.trim().take(300).let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
      }
    val payload =
      mapOf(
        "userId" to uid,
        "name" to name.trim().take(80).ifEmpty { "Custom Music API" },
        "baseUrl" to normalizedUrl,
        "searchPath" to searchPath.trim().take(200),
        "protocolType" to safeProtocol,
        "regionCode" to regionCode.trim().uppercase().take(16).ifEmpty { "GLOBAL" },
        "customHeaderName" to customHeaderName.trim().take(80),
        "customHeaderValue" to customHeaderValue.trim().take(240),
        "preferredCodec" to preferredCodec.trim().take(40).ifEmpty { "FLAC Hi-Res" },
        "isEnabled" to isEnabled,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.set(payload).await()
      Result.success(docId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateCustomSource(source: CustomMusicSource): Result<Unit> {
    val uid = requireUserId()
    require(source.id.isNotBlank()) { "CustomMusicSource ID must not be blank for update" }
    requireNotNull(source.createdAt) { "CustomMusicSource createdAt must not be null for update" }
    val docRef =
      db.collection("users").document(uid).collection("customSources").document(source.id)
    return try {
      docRef
        .set(
          source.copy(
            userId = uid,
            name = source.name.trim().take(80).ifEmpty { "Custom Music API" },
            baseUrl = source.baseUrl.trim().take(300),
            searchPath = source.searchPath.trim().take(200),
            regionCode = source.regionCode.trim().uppercase().take(16).ifEmpty { "GLOBAL" },
            customHeaderName = source.customHeaderName.trim().take(80),
            customHeaderValue = source.customHeaderValue.trim().take(240),
            preferredCodec = source.preferredCodec.trim().take(40).ifEmpty { "FLAC Hi-Res" },
            updatedAt = null,
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteCustomSource(sourceId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef =
      db.collection("users").document(uid).collection("customSources").document(sourceId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- SOUND PROFILES (10-BAND PARAMETRIC EQ) ---

  fun observeSoundProfiles(
    userId: String = auth.currentUser?.uid ?: "unauthenticated"
  ): Flow<List<SoundProfile>> {
    val path = "users/$userId/soundProfiles"
    return db
      .collection("users")
      .document(userId)
      .collection("soundProfiles")
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          SoundProfile::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  suspend fun createSoundProfile(
    name: String,
    presetCategory: String,
    bandsDb: List<Long>,
    preampGainTenths: Long,
    bassBoostPercent: Long,
    spatializerStrength: Long,
    dacBitPerfectMode: Boolean,
    isActive: Boolean,
    customId: String? = null,
  ): Result<String> {
    val uid = requireUserId()
    val docId = customId ?: "eq_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = db.collection("users").document(uid).collection("soundProfiles").document(docId)
    val normalizedBands =
      (bandsDb + List(10) { 0L }).take(10).map { it.coerceIn(-120L, 120L) }
    val payload =
      mapOf(
        "userId" to uid,
        "name" to name.trim().take(60).ifEmpty { "Custom Profile" },
        "presetCategory" to presetCategory,
        "bandsDb" to normalizedBands,
        "preampGainTenths" to preampGainTenths.coerceIn(-120L, 120L),
        "bassBoostPercent" to bassBoostPercent.coerceIn(0L, 100L),
        "spatializerStrength" to spatializerStrength.coerceIn(0L, 100L),
        "dacBitPerfectMode" to dacBitPerfectMode,
        "isActive" to isActive,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.set(payload).await()
      Result.success(docId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateSoundProfile(profile: SoundProfile): Result<Unit> {
    val uid = requireUserId()
    require(profile.id.isNotBlank()) { "SoundProfile ID must not be blank for update" }
    requireNotNull(profile.createdAt) { "SoundProfile createdAt must not be null for update" }
    val docRef =
      db.collection("users").document(uid).collection("soundProfiles").document(profile.id)
    return try {
      docRef
        .set(
          profile.copy(
            userId = uid,
            name = profile.name.trim().take(60).ifEmpty { "Custom Profile" },
            bandsDb = (profile.bandsDb + List(10) { 0L }).take(10).map { it.coerceIn(-120L, 120L) },
            preampGainTenths = profile.preampGainTenths.coerceIn(-120L, 120L),
            bassBoostPercent = profile.bassBoostPercent.coerceIn(0L, 100L),
            spatializerStrength = profile.spatializerStrength.coerceIn(0L, 100L),
            updatedAt = null,
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteSoundProfile(profileId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef =
      db.collection("users").document(uid).collection("soundProfiles").document(profileId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- CROSS-PLATFORM SYNC STATE ---

  fun observeSyncStates(
    userId: String = auth.currentUser?.uid ?: "unauthenticated"
  ): Flow<List<SyncState>> {
    val path = "users/$userId/syncState"
    return db
      .collection("users")
      .document(userId)
      .collection("syncState")
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          SyncState::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  suspend fun publishSyncState(
    deviceId: String,
    deviceName: String,
    activeTrackId: String,
    positionMs: Long,
    isPlaying: Boolean,
    activeService: String,
    audioQualityBadge: String,
    activeSourceName: String,
    regionCode: String,
    existingCreatedAt: com.google.firebase.Timestamp? = null,
  ): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("syncState").document(deviceId)
    val allowedServices = setOf("AURA_HIFI", "CUSTOM_API", "SUBSONIC", "OPEN_GLOBAL")
    val safeService = if (activeService in allowedServices) activeService else "AURA_HIFI"
    val payload =
      mapOf(
        "userId" to uid,
        "deviceName" to deviceName.take(80).ifEmpty { "Android Hi-Fi Deck" },
        "activeTrackId" to activeTrackId.take(64).ifEmpty { "unknown" },
        "positionMs" to positionMs.coerceIn(0L, 86400000L),
        "isPlaying" to isPlaying,
        "activeService" to safeService,
        "audioQualityBadge" to audioQualityBadge.take(60).ifEmpty { "Unknown" },
        "activeSourceName" to activeSourceName.take(80).ifEmpty { "Unknown" },
        "regionCode" to regionCode.trim().uppercase().take(16).ifEmpty { "GLOBAL" },
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    return try {
      docRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(
        e,
        if (existingCreatedAt == null) OperationType.CREATE else OperationType.UPDATE,
        docRef.path,
      )
      Result.failure(e)
    }
  }
}
