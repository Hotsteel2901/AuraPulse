# Security and data boundaries

- AuraPulse has no backend and no Firebase project. The only endpoints it contacts are
  `itunes.apple.com`, `all.api.radio-browser.info`, user-configured HTTPS JSON sources,
  user-supplied audio/stream URLs and `api.github.com`.
- A GitHub token is stored through `SecureStore`: AES/GCM with a key that never leaves the Android
  Keystore. The plaintext token is not written to SharedPreferences, Room, logs or backups. Signing
  out clears the stored ciphertext.
- The requested OAuth scope is `gist read:user`. Nothing else is asked for, and no token is sent
  anywhere except `api.github.com`.
- Gist backups contain track metadata, playlists, favourites and remote URLs only. They never contain
  `content://` URIs, local file paths, downloaded copies or Android document grants. A backup can
  reveal which stations or links a person listens to, so pushing is always an explicit action.
- Restore validates the schema, field types, URL schemes and list bounds before touching Room, and
  merges by identifier plus `updatedAt` instead of wiping local data. A changed Gist revision is
  reported as a conflict rather than silently overwritten.
- Custom JSON search endpoints must use HTTPS and cannot contain URL userinfo. No authentication
  header is configurable, so no credential can be smuggled into a source template.
- Plain HTTP is accepted only for user-supplied audio and radio streams, because many stations do not
  offer TLS. The interface states that such streams are unencrypted.
- Local files are opened through persisted Android document grants. Removing a track from AuraPulse
  never deletes the original document; app-owned downloads are deleted only from the app's private
  download directory.
- There is no analytics, crash reporting, advertising or device-fingerprinting code in the app.
