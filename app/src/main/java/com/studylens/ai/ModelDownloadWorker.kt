package com.studylens.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Runs a model download as a genuine background job via WorkManager, with a foreground
 * notification - survives navigating away from the picker screen, backgrounding the app,
 * or even the screen turning off. This is exactly why it kept resetting to 0% before: the
 * previous version ran inside the Composable's own coroutine scope, which gets cancelled
 * the moment you leave that screen.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES) // multi-GB files, slow venue wifi
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: "model"
        val filename = inputData.getString(KEY_FILENAME) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val expectedSize = inputData.getLong(KEY_SIZE, 0L)

        setForeground(createForegroundInfo(displayName, 0))

        val targetFile = File(applicationContext.filesDir, filename)
        val tempFile = File(applicationContext.filesDir, "$filename.download")

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(workDataOf(KEY_ERROR to "Download failed: HTTP ${response.code}"))
                }
                val body = response.body
                    ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Empty response from server"))
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: expectedSize

                body.byteStream().use { input ->
                    RandomAccessFile(tempFile, "rw").use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var downloaded = 0L
                        var lastProgressReportAt = 0L
                        var lastNotificationAt = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            val pct = if (totalBytes > 0) (downloaded * 100 / totalBytes).toInt() else 0
                            if (now - lastProgressReportAt > 200) {
                                setProgress(workDataOf(KEY_PROGRESS to pct))
                                lastProgressReportAt = now
                            }
                            if (now - lastNotificationAt > 1000) {
                                setForeground(createForegroundInfo(displayName, pct))
                                lastNotificationAt = now
                            }
                        }
                        setProgress(workDataOf(KEY_PROGRESS to 100))
                    }
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                return@withContext Result.failure(workDataOf(KEY_ERROR to "Couldn't finalize the downloaded file"))
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // User hit "Cancel" - clean up the partial file, then let the cancellation
            // propagate normally instead of swallowing it as a generic failure.
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed - check your connection")))
        }
    }

    private fun createForegroundInfo(displayName: String, progressPct: Int): ForegroundInfo {
        val channelId = "model_download_channel"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Model downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Downloading $displayName")
            .setContentText("$progressPct%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progressPct, false)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_FILENAME = "filename"
        const val KEY_URL = "url"
        const val KEY_SIZE = "size"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        private const val NOTIFICATION_ID = 4242

        fun workName(modelId: String) = "download_model_$modelId"
    }
}
