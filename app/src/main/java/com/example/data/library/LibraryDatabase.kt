package com.example.data.library

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
interface LibraryDao {
  @Query("SELECT * FROM library_tracks ORDER BY addedAt DESC, title COLLATE NOCASE ASC")
  fun observeTracks(): Flow<List<LibraryTrack>>

  @Query("SELECT * FROM library_playlists ORDER BY updatedAt DESC")
  fun observePlaylists(): Flow<List<LibraryPlaylist>>

  @Query("SELECT * FROM library_tracks")
  suspend fun getTracks(): List<LibraryTrack>

  @Query("SELECT * FROM library_playlists")
  suspend fun getPlaylists(): List<LibraryPlaylist>

  @Query("SELECT * FROM library_tracks WHERE id = :id")
  suspend fun getTrack(id: String): LibraryTrack?

  @Query("SELECT * FROM library_playlists WHERE id = :id")
  suspend fun getPlaylist(id: String): LibraryPlaylist?

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertTrack(track: LibraryTrack): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun putTrack(track: LibraryTrack)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun putPlaylist(playlist: LibraryPlaylist)

  @Query("DELETE FROM library_tracks WHERE id = :id")
  suspend fun deleteTrack(id: String)

  @Query("DELETE FROM library_playlists WHERE id = :id")
  suspend fun deletePlaylist(id: String)
}

// A separate database deliberately avoids importing the previous synthetic catalog.
// The old database is left untouched rather than destructively migrating user data.
@Database(entities = [LibraryTrack::class, LibraryPlaylist::class], version = 1, exportSchema = false)
abstract class LibraryDatabase : RoomDatabase() {
  abstract fun libraryDao(): LibraryDao

  companion object {
    @Volatile private var instance: LibraryDatabase? = null
    fun get(context: Context): LibraryDatabase = instance ?: synchronized(this) {
      instance ?: Room.databaseBuilder(
        context.applicationContext, LibraryDatabase::class.java, "aurapulse_library_v2.db"
      ).build().also { instance = it }
    }
  }
}
