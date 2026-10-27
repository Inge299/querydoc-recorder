# Upload API Draft (for querydoc-platform)

This client is implemented around a queue and chunked uploads.

## Audio chunk upload flow

1. Android records audio chunks of 30s (`audio/mp4`, AAC-LC, mono, 24kHz, 64kbps).
2. For each chunk, client asks backend for upload target.
3. Client uploads to storage and confirms upload completion.

### Mandatory preprocessing before transcription

On backend, before transcription, **always run loudness normalization and denoise**.
This is the biggest quality boost for ASR, especially for quiet or distant lecturers.

## Implemented endpoints

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
    "content-type": "audio/mp4"
  },
  "storageKey": "<userId>/audio-chunks/<sessionId>/000000-<uuid>.m4a",
  "uploadSessionId": "...",
  "expiresAt": "2026-04-16T12:00:00Z",
  "accepted": true
}
```

A duplicate `chunkId` returns HTTP 200 with `{ "duplicate": true, "accepted": true, "materialFileId": "..." }` and no upload URL.

### 2) Confirm upload

`POST /api/v1/mobile/uploads/audio-chunks/complete`

Request body:
```json
{
  "uploadSessionId": "..."
}
```

`chunkId` is accepted as a fallback when `uploadSessionId` is not sent. Completion is idempotent.

Response body:
```json
{
  "accepted": true
}
```

## S3 requirements

The platform must run with `STORAGE_DRIVER=s3`. The presigned URL includes `x-amz-content-sha256=UNSIGNED-PAYLOAD`; the S3-compatible endpoint must accept a plain `PUT` with the returned `content-type` header. `S3_REGION` must match the endpoint (Selectel virtual-hosted buckets use `ru-7`).

## Idempotency requirements

- `chunkId` must be globally unique on client.
- Backend should accept repeated completion calls for the same `chunkId` without duplicating objects.
- Presign and complete should be authenticated with the same mobile identity model used by querydoc-platform.
