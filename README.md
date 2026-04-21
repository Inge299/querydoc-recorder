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

This project is a hand-crafted skeleton and is ready to be opened in Android Studio.

Because this environment does not have `gradle`/Android SDK configured, build verification was not executed here.

## Next implementation steps

1. Replace `StubUploadRepository` with real `querydoc-platform` upload API integration.
2. Replace heuristic rectangle detector with OpenCV contour-based detector.
3. Add simple diagnostics viewer screen (read last N lines of log).

## Transcription pipeline recommendation

Before server-side transcription, always run audio through **loudness normalization and denoise**.
This gives the largest practical improvement in speech recognition quality, especially for distant lecturers.
