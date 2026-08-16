package com.idt.widget.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.idt.widget.BuildConfig
import com.idt.widget.data.remote.UpdateChecker
import com.idt.widget.util.NotificationHelper

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val update = UpdateChecker().check(applicationContext) ?: return Result.success()
            if (update.isNewerThan(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)) {
                NotificationHelper.ensureChannels(applicationContext)
                NotificationHelper.notifyUpdate(
                    applicationContext,
                    update.versionName,
                    update.apkUrl,
                    update.changelog,
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}