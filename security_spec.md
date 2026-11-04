# Security and data boundaries

- `users/{uid}/backups/library` is readable, writable and deletable only by the matching authenticated Firebase UID. Rules restrict document ID, fields, schema version, string size and server timestamp. List access is denied.
- Backup contains playlists and remote audio URLs only. It never contains `content://` URIs, local files, downloaded paths or Android URI grants. URLs may themselves contain sensitive query parameters; the UI warns users before backup.
- Backups are explicit snapshots, not automatic synchronization. Restore validates the schema, URL schemes, identifiers, bounds and duplicates before a database transaction; newer local playlists are retained.
- Custom JSON search endpoints must be HTTPS and cannot contain URL userinfo. No authentication headers or tokens are stored by the new source configuration. HTTP is permitted for user-supplied audio and radio streams only because many stations do not offer HTTPS; the interface warns that these streams are unencrypted.
- Google login reports success only after Firebase verifies the Google ID token. No email-only or profile-lookup fallback is treated as authentication.
- The original Firestore collections and their rules remain for compatibility, but the new UI does not read or write them. The old local database is not destroyed and its fabricated catalog is not imported.
- Local files are accessed through persisted Android document grants. Removing a track from AuraPulse never deletes the original file. App-owned downloaded copies are removed only from the app’s private downloads directory.
