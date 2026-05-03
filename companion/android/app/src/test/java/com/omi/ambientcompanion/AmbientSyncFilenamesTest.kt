package com.omi.ambientcompanion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AmbientSyncFilenamesTest {
    @Test
    fun omiPcm16BinUsesBackendTimestampTokenInSeconds() {
        val filename = AmbientSyncFilenames.omiPcm16Bin(metadata(Instant.parse("2024-01-02T00:00:00Z")), part = 3)

        assertEquals("ambient_android_pcm16_16000_1_1704153600_3.bin", filename)
    }

    @Test
    fun omiPcm16BinClampsFutureDeviceClockBehindUploadTime() {
        val before = Instant.now().epochSecond
        val filename = AmbientSyncFilenames.omiPcm16Bin(metadata(Instant.parse("2099-01-01T00:00:00Z")))
        val token = filename.removePrefix("ambient_android_pcm16_16000_1_").substringBefore("_").toLong()

        assertTrue(token <= before)
        assertTrue(token.toString().length == 10)
    }

    private fun metadata(startedAt: Instant): SpoolMetadata {
        return SpoolMetadata(
            sessionId = "session",
            startedAt = startedAt,
            filePath = "/tmp/audio.bin",
            bytes = 320,
            durationEstimateSeconds = 0.01,
            status = "pending",
        )
    }
}
