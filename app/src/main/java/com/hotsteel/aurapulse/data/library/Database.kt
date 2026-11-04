package com.hotsteel.aurapulse.data.library

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AuraDao {
  @Query("SELECT * FROM tracks ORDER BY addedAt DESC")
  fun observeTracks(): Flow<List<TrackEntity>>

  @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
  fun observePlaylists(): Flow<List<PlaylistEntity>>

  @Query("SELECT * FROM tracks")
  suspend fun tracks(): List<TrackEntity>

  @Query("SELECT * FROM tracks WHERE id = :id")
  suspend fun track(id: String): TrackEntity?

  @Query("SELECT * FROM playlists WHERE id = :id")
  suspend fun playlist(id: String): PlaylistEntity?

  @Query("SELECT * FROM playlists")
  suspend fun playlists(): List<PlaylistEntity>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertTrack(track: TrackEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun putTrack(track: TrackEntity)

  @Query("UPDATE tracks SET missing = :missing WHERE id = :id")
  suspend fun setMissing(id: String, missing: Boolean)

  @Query("DELETE FROM tracks WHERE id = :id")
  suspend fun deleteTrack(id: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun putPlaylist(playlist: PlaylistEntity)

  @Query("DELETE FROM playlists WHERE id = :id")
  suspend fun deletePlaylist(id: String)

  @Insert
  suspend fun insertEvent(event: PlayEventEntity)

  @Query("SELECT trackId, COUNT(*) AS plays, COALESCE(SUM(listenedMs), 0) AS listenedMs, MAX(startedAt) AS lastPlayedAt FROM play_events GROUP BY trackId")
  fun observeStats(): Flow<List<PlayStat>>

  @Query("SELECT trackId, COUNT(*) AS plays, COALESCE(SUM(listenedMs), 0) AS listenedMs, MAX(startedAt) AS lastPlayedAt FROM play_events GROUP BY trackId")
  suspend fun stats(): List<PlayStat>

  @Query("SELECT * FROM play_events WHERE startedAt >= :since ORDER BY startedAt ASC")
  suspend fun eventsSince(since: Long): List<PlayEventEntity>

  @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM play_events")
  fun observeTotalMs(): Flow<Long>

  @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM play_events")
  suspend fun totalMs(): Long

  @Query("DELETE FROM play_events")
  suspend fun clearEvents()

  @Query("SELECT * FROM lyrics WHERE trackId = :id")
  suspend fun lyrics(id: String): LyricEntity?

  @Query("SELECT trackId FROM lyrics")
  fun observeLyricIds(): Flow<List<String>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun putLyrics(lyrics: LyricEntity)

  @Query("DELETE FROM lyrics WHERE trackId = :id")
  suspend fun deleteLyrics(id: String)

  @Query("SELECT * FROM queue_state WHERE id = 1")
  suspend fun queueState(): QueueStateEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun putQueueState(state: QueueStateEntity)
}

@Database(
  entities = [TrackEntity::class, PlaylistEntity::class, PlayEventEntity::class, LyricEntity::class, QueueStateEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class AuraDatabase : RoomDatabase() {
  abstract fun dao(): AuraDao

  companion object {
    @Volatile private var instance: AuraDatabase? = null

    fun get(context: Context): AuraDatabase = instance ?: synchronized(this) {
      instance ?: Room.databaseBuilder(context.applicationContext, AuraDatabase::class.java, "aurapulse.db")
        .build().also { instance = it }
    }
  }
}
