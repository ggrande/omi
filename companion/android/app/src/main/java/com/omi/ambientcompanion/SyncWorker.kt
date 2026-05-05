package com.omi.ambientcompanion

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlin.concurrent.thread

object SyncWorker {
    private var running = false

    @Synchronized
    fun drainAsync(context: Context, force: Boolean = false) {
        if (running) return
        running = true
        thread(name = "ambient-sync") {
            try {
                drain(context.applicationContext, force)
            } finally {
                running = false
            }
        }
    }

    fun drain(context: Context, force: Boolean = false) {
        val prefs = AppPrefs(context)
        if (!networkAvailable(context)) {
            prefs.lastSyncLabel = "Network unavailable; buffering locally"
            return
        }
        val audit = AuditLog(context)
        val now = System.currentTimeMillis()
        if (force) {
            prefs.nextSyncAfterMs = 0
            prefs.syncFailureCount = 0
            audit.record("sync_backoff_cleared", mapOf("reason" to "manual_or_segment_flush"))
        } else if (prefs.nextSyncAfterMs > now) {
            prefs.lastSyncLabel = "Backoff active; retry in ${(prefs.nextSyncAfterMs - now) / 1000}s"
            audit.record("sync_backoff_active", mapOf("retry_after_ms" to (prefs.nextSyncAfterMs - now)))
            return
        }
        val client = PluginClient(context)
        val omiAuth = OmiAuthClient(context)
        val fallbackQueue = FallbackSegmentQueue(context)
        var attempted = false
        var succeeded = false
        val pendingSegments = fallbackQueue.pending()
        val pluginReady = prefs.fallbackSegmentsUrl.isNotBlank() && SecureStore(context).getSecret("device_token").isNotBlank()
        if (pendingSegments.isNotEmpty() && pluginReady) {
            attempted = true
            if (client.uploadFallbackSegments(pendingSegments)) {
                fallbackQueue.clearUploaded(pendingSegments.map { it.id }.toSet())
                audit.record("fallback_segments_uploaded", mapOf("count" to pendingSegments.size))
                succeeded = true
            }
        } else if (pendingSegments.any { !it.rawAudioAvailable }) {
            val directOmiFallbackSegments = pendingSegments
                .filter { it.text.isNotBlank() && !it.rawAudioAvailable }
                .sortedBy { it.start }
                .take(500)
            attempted = true
            if (omiAuth.uploadFallbackSegments(directOmiFallbackSegments)) {
                val uploadedIds = directOmiFallbackSegments.map { it.id }.toSet()
                fallbackQueue.clearUploaded(uploadedIds)
                succeeded = true
            }
        } else if (pendingSegments.isNotEmpty()) {
            audit.record("fallback_segments_waiting_controller", mapOf("count" to pendingSegments.size))
        }
        LocalSttWorker(context).drainSpoolForLocalTranscripts()
        val spool = CaptureSpoolStore(context)
        val uploaded = mutableListOf<String>()
        val uploadedSessions = mutableListOf<String>()
        val pendingSpool = spool.list("pending")
        if (pendingSpool.isNotEmpty()) {
            prefs.lastSyncLabel = "Uploading ${pendingSpool.size} audio segment(s)"
        }
        if (prefs.allowAudioUpload) {
            pendingSpool.forEach { meta ->
                attempted = true
                val omiResult = omiAuth.uploadAudioFile(meta, spool.readPlainChunks(meta))
                val uploadedToOmi = omiResult.success
                val uploadedToController = if (uploadedToOmi) false else client.uploadAudioFile(meta, spool.readPlainChunks(meta))
                if (uploadedToOmi || uploadedToController) {
                    uploaded.add(meta.filePath)
                    uploadedSessions.add(meta.sessionId)
                    succeeded = true
                    if (uploadedToOmi) {
                        prefs.lastSyncLabel = "Omi upload accepted; tracing job result"
                        omiAuth.traceSyncResult(omiResult, "immediate")
                        Thread.sleep(TRACE_AFTER_UPLOAD_DELAY_MS)
                        omiAuth.traceSyncResult(omiResult, "delayed")
                    }
                    audit.record(
                        "spool_audio_uploaded",
                        mapOf(
                            "session_id" to meta.sessionId,
                            "bytes" to meta.bytes,
                            "destination" to if (uploadedToOmi) "omi_sync_local_files" else "controller_audio_spool",
                        ),
                    )
                }
            }
        } else {
            audit.record("spool_audio_upload_skipped", mapOf("reason" to "policy_or_local_setting_disabled"))
        }
        if (uploaded.isNotEmpty()) {
            spool.markStatus(uploaded, "synced")
            CaptureActivityStore(context).markSynced(uploadedSessions)
            if (prefs.deleteSyncedAudio) spool.deleteByStatus("synced")
        }
        if (attempted && succeeded) {
            prefs.syncFailureCount = 0
            prefs.nextSyncAfterMs = 0
            prefs.lastSyncLabel = "Last sync succeeded"
        } else if (attempted) {
            prefs.lastSyncLabel = "Last sync failed; retry scheduled"
            scheduleBackoff(prefs, audit)
        } else {
            prefs.lastSyncLabel = "Nothing pending to sync"
        }
    }

    private fun scheduleBackoff(prefs: AppPrefs, audit: AuditLog) {
        val failures = (prefs.syncFailureCount + 1).coerceAtMost(8)
        val delayMs = (30_000L * (1 shl (failures - 1))).coerceAtMost(30 * 60_000L)
        prefs.syncFailureCount = failures
        prefs.nextSyncAfterMs = System.currentTimeMillis() + delayMs
        audit.record("sync_backoff_scheduled", mapOf("failures" to failures, "delay_ms" to delayMs))
    }

    private fun networkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private const val TRACE_AFTER_UPLOAD_DELAY_MS = 20_000L
}
