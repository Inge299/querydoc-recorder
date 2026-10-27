package com.querydoc.lecturerecorder.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadMetadataTest {
    @Test
    fun parsesChunkMetadata() {
        val value = UploadMetadata.parse("sessionId=s1;chunkIndex=3;chunkCount=12;capturedAtMs=100;durationMs=30000")
        assertEquals("s1", value?.sessionId)
        assertEquals(3, value?.chunkIndex)
        assertEquals(12, value?.chunkCount)
        assertEquals(100L, value?.capturedAtMs)
        assertEquals(30000L, value?.durationMs)
    }

    @Test
    fun rejectsMissingSessionOrIndex() {
        assertNull(UploadMetadata.parse(null))
        assertNull(UploadMetadata.parse("chunkIndex=1"))
        assertNull(UploadMetadata.parse("sessionId=s1"))
    }

    @Test
    fun acceptsUnknownChunkCountForLiveRecording() {
        val value = UploadMetadata.parse("sessionId=s1;chunkIndex=0;chunkCount=0")
        assertEquals(null, value?.chunkCount)
    }
}
