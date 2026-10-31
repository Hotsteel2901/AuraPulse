# AuraPulse Security Specification (Phase 0 TDD)

## 1. Data Invariants & Relationship Mapping
- **Multi-User Isolation**: Every document in `/users/{userId}/playlists/{playlistId}`, `/users/{userId}/soundProfiles/{profileId}`, `/users/{userId}/customSources/{sourceId}`, and `/users/{userId}/syncState/{deviceId}` belongs strictly to `userId` (`request.auth.uid == userId` and `data.userId == request.auth.uid`).
- **Path Hardening**: All document IDs (`userId`, `playlistId`, `profileId`, `sourceId`, `deviceId`) must match `^[a-zA-Z0-9_\-]+$` with length `<= 128`.
- **Immutable Metadata**: `userId` and `createdAt` cannot be modified after creation (`incoming().userId == existing().userId && incoming().createdAt == existing().createdAt`).
- **Temporal Integrity**: `createdAt` and `updatedAt` must be valid Firestore `timestamp` objects `<= request.time`.
- **Bounded Collections & Strings**:
  - Playlist `trackIds` is a list of strings bounded to `<= 100` items.
  - SoundProfile `bandsDb` is a list of integers bounded to `== 10` items.
  - CustomMusicSource validates `protocolType`, `baseUrl`, `regionCode`, and string bounds.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Read/List**: Calling `get` or `list` on `/users/alice_123/customSources` with `request.auth == null`.
2. **Cross-User Read (Ownership Leak)**: Authenticated `bob_456` reading `/users/alice_123/customSources/src_1`.
3. **Identity Spoofing on Create**: `alice_123` writing to `/users/alice_123/playlists/pl_1` with `userId: "bob_456"`.
4. **Shadow Update (Ghost Field Injection)**: `alice_123` updating `/users/alice_123/customSources/src_1` with an undeclared field `isVerifiedAdmin: true`.
5. **Immutable Field Tampering**: `alice_123` updating `createdAt` or `userId` on an existing playlist or custom source.
6. **Value Poisoning (Oversized String)**: `alice_123` setting `baseUrl` to a 5,000-character string.
7. **Unbounded Array Attack**: `alice_123` pushing 500 track IDs into `trackIds` (exceeds max 100).
8. **Invalid Enum Injection**: `alice_123` creating a custom music source with `protocolType: "INVALID_PROTO"`.
9. **Future Timestamp Spoofing**: `alice_123` submitting `createdAt` set 1 hour in the future (`> request.time`).
10. **Path ID Poisoning**: Creating a document with special characters or >128 chars in `sourceId`.
11. **Missing Required Keys on Create**: Creating a `customSources` doc without `baseUrl` or `regionCode`.
12. **Unfiltered List Query**: Querying `customSources` without matching `userId == request.auth.uid`.
