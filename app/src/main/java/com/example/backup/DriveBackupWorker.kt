package com.example.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager background worker that automatically syncs Room Database snapshots to Google Drive
 * periodically (every 12h/24h) or shortly after transactions/roster data change.
 */
class DriveBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = DriveBackupManager.getSettings(applicationContext)
        if (!settings.autoBackupEnabled || !settings.isConnected) {
            return Result.success()
        }

        return when (val auth = DriveBackupManager.authorizeDrive(applicationContext)) {
            is DriveAuthOutcome.Authorized -> {
                val backupResult = DriveBackupManager.performBackupNow(
                    context = applicationContext,
                    accessToken = auth.accessToken
                )
                if (backupResult.isSuccess) {
                    Result.success()
                } else {
                    val error = backupResult.exceptionOrNull()
                    if (error is DriveAuthExpiredException) {
                        DriveBackupManager.updateStatus(
                            applicationContext,
                            "Perlu otorisasi ulang Google Drive"
                        )
                        Result.failure()
                    } else if (runAttemptCount < 3) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            }

            is DriveAuthOutcome.NeedsConsent -> {
                DriveBackupManager.updateStatus(
                    applicationContext,
                    "Menunggu otorisasi ulang Google Drive"
                )
                Result.failure()
            }

            is DriveAuthOutcome.Error -> {
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    DriveBackupManager.updateStatus(applicationContext, auth.message)
                    Result.failure()
                }
            }
        }
    }
}
