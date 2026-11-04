package com.example.data.cloud

import androidx.room.withTransaction
import com.example.data.library.LibraryDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class RestoreResult(val tracksAdded: Int, val playlistsMerged: Int, val missingLocalTracks: Int)


class CloudBackup(private val db: LibraryDatabase, private val firestore: FirebaseFirestore) {
  private fun document(uid: String) = firestore.collection("users").document(uid).collection("backups").document("library")

  suspend fun upload(uid: String) = withContext(Dispatchers.IO) {
    require(FirebaseAuth.getInstance().currentUser?.uid == uid) { "Not signed in" }
    val content = db.withTransaction { BackupContent(db.libraryDao().getTracks(), db.libraryDao().getPlaylists()) }
    val json = BackupCodec.encode(content.tracks, content.playlists)
    withTimeout(25_000) {
      // Do not report success from the local Firestore cache while offline.
      document(uid).get(Source.SERVER).await()
      require(FirebaseAuth.getInstance().currentUser?.uid == uid) { "Account changed" }
      document(uid).set(mapOf(
        "userId" to uid, "schemaVersion" to 1, "data" to json, "updatedAt" to FieldValue.serverTimestamp(),
      )).await()
    }
  }

  suspend fun restore(uid: String): RestoreResult = withContext(Dispatchers.IO) {
    require(FirebaseAuth.getInstance().currentUser?.uid == uid) { "Not signed in" }
    val snapshot = withTimeout(25_000) { document(uid).get(Source.SERVER).await() }
    require(snapshot.exists()) { "No cloud backup exists" }
    require(snapshot.getString("userId") == uid && snapshot.getLong("schemaVersion") == 1L) { "Invalid backup" }
    val content = BackupCodec.decode(snapshot.getString("data") ?: error("Invalid backup"))
    require(FirebaseAuth.getInstance().currentUser?.uid == uid) { "Account changed" }
    db.withTransaction {
      var added = 0
      var merged = 0
      val dao = db.libraryDao()
      content.tracks.forEach { if (dao.insertTrack(it) != -1L) added++ }
      content.playlists.forEach { remote ->
        val local = dao.getPlaylist(remote.id)
        if (local == null || remote.updatedAt > local.updatedAt) {
          dao.putPlaylist(remote)
          merged++
        }
      }
      val availableIds = dao.getTracks().map { it.id }.toSet()
      val missing = content.playlists.flatMap { it.trackIds() }.distinct().count { it !in availableIds }
      RestoreResult(added, merged, missing)
    }
  }
}
