# LectureRecorderAndroid

Android MVP for long lecture recording with:
- audio recording in foreground service
- 30-second chunk rotation
- persistent upload queue (Room + WorkManager)
- local replay of recorded chunks
- camera capture + manual 4-point perspective crop
- auto rectangle suggestion for board/screen with manual correction
- periodic cleanup worker for expired local files
- diagnostics log in app internal storage

## Current status

The recorder is integrated with the QueryDoc mobile API: phone/password login, persistent session cookie, S3 presigned PUT via `querydoc-platform`, completion, and WorkManager retries. Unit tests cover chunk metadata parsing; `assembleDebug` and `lintDebug` pass.

Still missing for the full product:

1. Session finalize endpoint and automatic conspect generation after all chunks are uploaded.
2. In-app transcript/conspect screen (the current release uploads and hands off to the web workspace).
3. Release signing and device/E2E verification.
4. Diagnostics viewer for the last N lines of the log.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

The API base URL defaults to `https://querydoc.ru`. Override it for an emulator or staging:

```bash
./gradlew :app:assembleDebug -Pquerydoc.apiBaseUrl=http://10.0.2.2:3000
```

HTTP cleartext for emulator builds is not enabled by default; use an HTTPS staging URL or add a debug-only network security config.

## Transcription pipeline recommendation

Before server-side transcription, always run audio through **loudness normalization and denoise**.
This gives the largest practical improvement in speech recognition quality, especially for distant lecturers.
