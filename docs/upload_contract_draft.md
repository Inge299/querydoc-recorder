# Upload API Draft (for querydoc-platform)

This client is implemented around a queue and chunked uploads.

## Audio chunk upload flow

1. Android records audio chunks of 30s (`audio/mp4`, AAC-LC, mono, 24kHz, 64kbps).
2. For each chunk, client asks backend for upload target.
3. Client uploads to storage and confirms upload completion.

### Mandatory preprocessing before transcription

On backend, before transcription, **always run loudness normalization and denoise**.
This is the biggest quality boost for ASR, especially for quiet or distant lecturers.

## Suggested endpoints

### 1) Request upload target

`POST /api/v1/mobile/uploads/audio-chunks/presign`

Request body:
```json
{
  "chunkId": "uuid",
  "sessionId": "uuid",
  "mimeType": "audio/mp4",
  "durationSec": 30,
  "sizeBytes": 123456
}
```

Response body:
```json
{
  "uploadUrl": "https://...",
  "method": "PUT",
  "headers": {
    "Content-Type": "audio/mp4"
  },
  "storageKey": "mobile/.../chunk_0001.m4a",
  "expiresAt": "2026-04-16T12:00:00Z"
}
```

### 2) Confirm upload

`POST /api/v1/mobile/uploads/audio-chunks/complete`

Request body:
```json
{
  "chunkId": "uuid",
  "sessionId": "uuid",
  "storageKey": "mobile/.../chunk_0001.m4a",
  "etag": "\"abcd...\""
}
```

Response body:
```json
{
  "accepted": true
}
```

## Idempotency requirements

- `chunkId` must be globally unique on client.
- Backend should accept repeated completion calls for the same `chunkId` without duplicating objects.
- Presign and complete should be authenticated with the same mobile identity model used by querydoc-platform.
