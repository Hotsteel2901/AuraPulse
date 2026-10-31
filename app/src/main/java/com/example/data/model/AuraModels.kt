package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Playlist(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val description: String = "",
  val accentHex: String = "#00F5D4",
  val sourceService: String = "AURA_HIFI",
  val isOfflinePinned: Boolean = false,
  val trackIds: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CustomMusicSource(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val baseUrl: String = "",
  val searchPath: String = "",
  val protocolType: String = "OPEN_REST_JSON",
  val regionCode: String = "GLOBAL",
  val customHeaderName: String = "",
  val customHeaderValue: String = "",
  val preferredCodec: String = "FLAC Hi-Res",
  val isEnabled: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SoundProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val presetCategory: String = "HARMAN_TARGET",
  val bandsDb: List<Long> = listOf(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L),
  val preampGainTenths: Long = 0L,
  val bassBoostPercent: Long = 0L,
  val spatializerStrength: Long = 0L,
  val dacBitPerfectMode: Boolean = false,
  val isActive: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SyncState(
  @DocumentId val id: String = "",
  val userId: String = "",
  val deviceName: String = "",
  val activeTrackId: String = "",
  val positionMs: Long = 0L,
  val isPlaying: Boolean = false,
  val activeService: String = "AURA_HIFI",
  val audioQualityBadge: String = "24-bit/192kHz FLAC",
  val activeSourceName: String = "Aura Hi-Res Core",
  val regionCode: String = "GLOBAL",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Entity(tableName = "offline_tracks")
data class OfflineTrackEntity(
  @PrimaryKey val trackId: String,
  val title: String,
  val artist: String,
  val album: String,
  val durationMs: Long,
  val bitDepth: Int,
  val sampleRateKhz: Double,
  val bitrateKbps: Int,
  val codec: String,
  val accentHex: String,
  val secondaryHex: String,
  val sourceProvider: String,
  val externalUri: String,
  val baseFreqHz: Double,
  val harmonicRatio: Double,
  val isDownloadedOffline: Boolean,
  val offlineSizeMb: Double,
  val localPcmPath: String?,
  val downloadedAtEpochMs: Long,
)
