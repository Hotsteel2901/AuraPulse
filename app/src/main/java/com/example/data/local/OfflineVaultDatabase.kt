package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.OfflineTrackEntity
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Dao
interface OfflineTrackDao {
  @Query("SELECT * FROM offline_tracks ORDER BY title ASC")
  fun observeAllTracks(): Flow<List<OfflineTrackEntity>>

  @Query("SELECT * FROM offline_tracks WHERE isDownloadedOffline = 1 ORDER BY downloadedAtEpochMs DESC")
  fun observeDownloadedTracks(): Flow<List<OfflineTrackEntity>>

  @Query("SELECT COUNT(*) FROM offline_tracks")
  suspend fun countTracks(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTracks(tracks: List<OfflineTrackEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTrack(track: OfflineTrackEntity)

  @Query(
    "UPDATE offline_tracks SET isDownloadedOffline = :downloaded, localPcmPath = :localPath, downloadedAtEpochMs = :timestamp WHERE trackId = :trackId"
  )
  suspend fun updateOfflineState(
    trackId: String,
    downloaded: Boolean,
    localPath: String?,
    timestamp: Long,
  )
}

@Database(entities = [OfflineTrackEntity::class], version = 1, exportSchema = false)
abstract class OfflineVaultDatabase : RoomDatabase() {
  abstract fun offlineTrackDao(): OfflineTrackDao

  companion object {
    @Volatile private var INSTANCE: OfflineVaultDatabase? = null

    fun getInstance(context: Context): OfflineVaultDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                OfflineVaultDatabase::class.java,
                "aurapulse_offline_vault.db",
              )
              .fallbackToDestructiveMigration()
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}

class OfflineVaultRepository(
  private val dao: OfflineTrackDao,
  private val appContext: Context,
) {
  val allTracks: Flow<List<OfflineTrackEntity>> = dao.observeAllTracks()
  val downloadedTracks: Flow<List<OfflineTrackEntity>> = dao.observeDownloadedTracks()

  suspend fun seedInitialCatalogIfEmpty() {
    if (dao.countTracks() == 0) {
      dao.insertTracks(defaultStudioMasterCatalog())
    }
  }

  suspend fun upsertExternalTrack(track: OfflineTrackEntity) {
    dao.insertTrack(track)
  }

  suspend fun toggleOfflineDownload(track: OfflineTrackEntity): Boolean =
    withContext(Dispatchers.IO) {
      val vaultDir = File(appContext.filesDir, "offline_hires_vault").apply { mkdirs() }
      val targetFile = File(vaultDir, "${track.trackId}.wav")

      if (track.isDownloadedOffline) {
        if (targetFile.exists()) targetFile.delete()
        dao.updateOfflineState(track.trackId, false, null, 0L)
        false
      } else {
        writeLosslessPreviewWav(targetFile, track.baseFreqHz, track.harmonicRatio)
        dao.updateOfflineState(
          trackId = track.trackId,
          downloaded = true,
          localPath = targetFile.absolutePath,
          timestamp = System.currentTimeMillis(),
        )
        true
      }
    }

  private fun writeLosslessPreviewWav(file: File, baseFreq: Double, harmonicRatio: Double) {
    val sampleRate = 48000
    val durationSeconds = 2
    val numSamples = sampleRate * durationSeconds
    val numChannels = 2
    val bytesPerSample = 2
    val dataSize = numSamples * numChannels * bytesPerSample

    FileOutputStream(file).use { out ->
      val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
      header.put("RIFF".toByteArray(Charsets.US_ASCII))
      header.putInt(36 + dataSize)
      header.put("WAVE".toByteArray(Charsets.US_ASCII))
      header.put("fmt ".toByteArray(Charsets.US_ASCII))
      header.putInt(16)
      header.putShort(1) // PCM
      header.putShort(numChannels.toShort())
      header.putInt(sampleRate)
      header.putInt(sampleRate * numChannels * bytesPerSample)
      header.putShort((numChannels * bytesPerSample).toShort())
      header.putShort(16)
      header.put("data".toByteArray(Charsets.US_ASCII))
      header.putInt(dataSize)
      out.write(header.array())

      val pcmBuffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
      for (i in 0 until numSamples) {
        val t = i.toDouble() / sampleRate
        val env = sin(PI * t / durationSeconds)
        val left =
          (sin(2.0 * PI * baseFreq * t) * 0.45 +
            sin(2.0 * PI * baseFreq * harmonicRatio * t) * 0.28 +
            sin(2.0 * PI * baseFreq * 2.0 * t) * 0.15) * env
        val right =
          (sin(2.0 * PI * (baseFreq * 1.003) * t) * 0.45 +
            sin(2.0 * PI * baseFreq * harmonicRatio * t + 0.4) * 0.28 +
            sin(2.0 * PI * baseFreq * 1.5 * t) * 0.15) * env
        val leftShort = (left * 26000).toInt().coerceIn(-32767, 32767).toShort()
        val rightShort = (right * 26000).toInt().coerceIn(-32767, 32767).toShort()
        if (pcmBuffer.remaining() < 4) {
          out.write(pcmBuffer.array(), 0, pcmBuffer.position())
          pcmBuffer.clear()
        }
        pcmBuffer.putShort(leftShort)
        pcmBuffer.putShort(rightShort)
      }
      if (pcmBuffer.position() > 0) {
        out.write(pcmBuffer.array(), 0, pcmBuffer.position())
      }
    }
  }

  companion object {
    fun defaultStudioMasterCatalog(): List<OfflineTrackEntity> =
      listOf(
        OfflineTrackEntity(
          trackId = "trk_neon_01",
          title = "Neon Echoes in Kyoto",
          artist = "Kaito & The Analog Syndicate",
          album = "Chromatic Horizons (24-Bit Studio Master • JP)",
          durationMs = 248_000L,
          bitDepth = 24,
          sampleRateKhz = 192.0,
          bitrateKbps = 9216,
          codec = "FLAC Hi-Res",
          accentHex = "#00F5D4",
          secondaryHex = "#7B2CBF",
          sourceProvider = "AURA_HIFI",
          externalUri = "aurapulse://master/trk_neon_01",
          baseFreqHz = 220.0,
          harmonicRatio = 1.498,
          isDownloadedOffline = false,
          offlineSizeMb = 164.8,
          localPcmPath = null,
          downloadedAtEpochMs = 0L,
        ),
        OfflineTrackEntity(
          trackId = "trk_solar_02",
          title = "Solar Drift over Atacama",
          artist = "Elena Rostova Quartet",
          album = "Desert Resonance (Open Global Node • CL/BR)",
          durationMs = 312_000L,
          bitDepth = 24,
          sampleRateKhz = 96.0,
          bitrateKbps = 4608,
          codec = "FLAC Lossless",
          accentHex = "#FF9F1C",
          secondaryHex = "#E71D36",
          sourceProvider = "OPEN_GLOBAL",
          externalUri = "https://archive.org/details/trk_solar_02",
          baseFreqHz = 196.0,
          harmonicRatio = 1.25,
          isDownloadedOffline = false,
          offlineSizeMb = 108.4,
          localPcmPath = null,
          downloadedAtEpochMs = 0L,
        ),
        OfflineTrackEntity(
          trackId = "trk_velvet_03",
          title = "Velvet Fjord Nocturne",
          artist = "Nordic Chamber Ensemble",
          album = "Subzero Acoustics (DXD • NO/SE)",
          durationMs = 284_000L,
          bitDepth = 32,
          sampleRateKhz = 384.0,
          bitrateKbps = 12288,
          codec = "WAV DXD",
          accentHex = "#4CC9F0",
          secondaryHex = "#4361EE",
          sourceProvider = "AURA_HIFI",
          externalUri = "aurapulse://master/trk_velvet_03",
          baseFreqHz = 174.61,
          harmonicRatio = 1.334,
          isDownloadedOffline = false,
          offlineSizeMb = 218.2,
          localPcmPath = null,
          downloadedAtEpochMs = 0L,
        ),
        OfflineTrackEntity(
          trackId = "trk_cyber_04",
          title = "Sub-Bass Monolith",
          artist = "Vektorwerk",
          album = "Berlin Modular Sessions (Subsonic Node • DE)",
          durationMs = 219_000L,
          bitDepth = 24,
          sampleRateKhz = 96.0,
          bitrateKbps = 4608,
          codec = "Subsonic FLAC",
          accentHex = "#F72585",
          secondaryHex = "#3A0CA3",
          sourceProvider = "SUBSONIC",
          externalUri = "subsonic://stream/trk_cyber_04",
          baseFreqHz = 110.0,
          harmonicRatio = 1.5,
          isDownloadedOffline = false,
          offlineSizeMb = 84.5,
          localPcmPath = null,
          downloadedAtEpochMs = 0L,
        ),
        OfflineTrackEntity(
          trackId = "trk_amber_05",
          title = "Seoul Midnight Analog",
          artist = "Han River Synth Collective",
          album = "Warm Bias Tape (Custom REST API • KR)",
          durationMs = 265_000L,
          bitDepth = 24,
          sampleRateKhz = 192.0,
          bitrateKbps = 9216,
          codec = "DSD128 / FLAC",
          accentHex = "#FFBE0B",
          secondaryHex = "#FB5607",
          sourceProvider = "CUSTOM_API",
          externalUri = "customapi://seoul-hifi/trk_amber_05",
          baseFreqHz = 261.63,
          harmonicRatio = 1.25,
          isDownloadedOffline = false,
          offlineSizeMb = 176.0,
          localPcmPath = null,
          downloadedAtEpochMs = 0L,
        ),
        OfflineTrackEntity(
          trackId = "trk_emerald_06",
          title = "Monsoon Raga in Mumbai",
          artist = "Aether & Sitar Ensemble",
          album = "Acoustic World Masters (Open Global • IN/SG)",
          durationMs = 204_000L,
          bitDepth = 24,
          sampleRateKhz = 96.0,
          bitrateKbps = 4608,
          codec = "FLAC Hi-Res",
          accentHex = "#38B000",
          secondaryHex = "#007200",
          sourceProvider = "OPEN_GLOBAL",
          externalUri = "https://archive.org/details/trk_emerald_06",
          baseFreqHz = 293.66,
          harmonicRatio = 1.498,
          isDownloadedOffline = false,
          offlineSizeMb = 79.2,
          localPcmPath = null,
          downloadedAtEpochMs = 0L,
        ),
      )
  }
}
