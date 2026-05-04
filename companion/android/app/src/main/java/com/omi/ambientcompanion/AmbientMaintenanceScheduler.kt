package com.omi.ambientcompanion

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle

object AmbientMaintenanceScheduler {
    private const val JOB_ID = 711042
    private const val PERIOD_MS = 15 * 60 * 1000L

    fun schedule(context: Context, reason: String = "app") {
        val appContext = context.applicationContext
        val scheduler = appContext.getSystemService(JobScheduler::class.java) ?: return
        val info = JobInfo.Builder(JOB_ID, ComponentName(appContext, AmbientMaintenanceJobService::class.java))
            .setPersisted(true)
            .setPeriodic(PERIOD_MS)
            .setExtras(PersistableBundle().apply { putString("reason", reason) })
            .build()
        val result = scheduler.schedule(info)
        AuditLog(appContext).record("maintenance_scheduled", mapOf("reason" to reason, "result" to result))
    }

    fun isScheduled(context: Context): Boolean {
        val scheduler = context.applicationContext.getSystemService(JobScheduler::class.java) ?: return false
        return scheduler.allPendingJobs.any { it.id == JOB_ID }
    }
}
