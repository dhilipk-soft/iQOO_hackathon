package com.studylens.ai

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed class ModelDownloadState {
    object Idle : ModelDownloadState()
    data class Downloading(val progressPct: Int) : ModelDownloadState()
    object Success : ModelDownloadState()
    data class Failed(val message: String) : ModelDownloadState()
}

/**
 * Downloads a model file straight from a public HTTPS URL into the app's private storage.
 */
class ModelDownloadManager(private val context: Context) {

    fun enqueueDownload(model: DownloadableModel) {
        val data = workDataOf(
            ModelDownloadWorker.KEY_DISPLAY_NAME to model.displayName,
            ModelDownloadWorker.KEY_FILENAME to model.filename,
            ModelDownloadWorker.KEY_URL to model.downloadUrl,
            ModelDownloadWorker.KEY_SIZE to model.sizeBytes
        )
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ModelDownloadWorker.workName(model.id),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun observeDownload(modelId: String): Flow<ModelDownloadState> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.workName(modelId))
            .map { infos ->
                val info = infos.firstOrNull() ?: return@map ModelDownloadState.Idle
                when (info.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        val pct = info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)
                        ModelDownloadState.Downloading(pct)
                    }
                    WorkInfo.State.SUCCEEDED -> ModelDownloadState.Success
                    WorkInfo.State.FAILED -> {
                        val error = info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed"
                        ModelDownloadState.Failed(error)
                    }
                    WorkInfo.State.CANCELLED -> ModelDownloadState.Idle
                }
            }
    }

    fun cancelDownload(model: DownloadableModel) {
        WorkManager.getInstance(context).cancelUniqueWork(ModelDownloadWorker.workName(model.id))
    }

    fun isDownloaded(model: DownloadableModel): Boolean {
        return File(context.filesDir, model.filename).exists()
    }

    /** Marks this model as the active model and persists whether it supports multimodal image inputs. */
    fun setActiveModel(model: DownloadableModel) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_FILENAME, model.filename)
            .putString(KEY_ACTIVE_DISPLAY_NAME, model.displayName)
            .putBoolean(KEY_ACTIVE_IS_MULTIMODAL, model.isMultimodal)
            .apply()
    }

    /** Resets the active-model preference back to the built-in default - used after deleting
     * whichever model was active, so the app doesn't keep pointing at a filename that no
     * longer exists on disk. Call llmEngine.invalidate() right after this too. */
    fun clearActiveModel() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_FILENAME, DEFAULT_MODEL_FILENAME)
            .putString(KEY_ACTIVE_DISPLAY_NAME, DEFAULT_MODEL_DISPLAY_NAME)
            .apply()
    }

    fun isActiveModel(model: DownloadableModel): Boolean {
        return getActiveModelFilename(context) == model.filename
    }

    /** Deletes the downloaded file to free up storage (these are multi-GB files). If it was
     * the active model, falls back to the default preference so LlmEngine doesn't keep
     * pointing at a now-missing file - the caller should still call llmEngine.invalidate()
     * to drop any in-memory engine/conversation still holding the deleted file open. */
    fun deleteDownloadedModel(model: DownloadableModel) {
        File(context.filesDir, model.filename).delete()
        if (isActiveModel(model)) {
            clearActiveModel()
        }
    }

    companion object {
        private const val PREFS_NAME = "studylens_model_prefs"
        private const val KEY_ACTIVE_FILENAME = "active_model_filename"
        private const val KEY_ACTIVE_DISPLAY_NAME = "active_model_display_name"
        private const val KEY_ACTIVE_IS_MULTIMODAL = "active_model_is_multimodal"

        const val DEFAULT_MODEL_FILENAME = "Qwen2-VL-2B.litertlm"
        private const val DEFAULT_MODEL_DISPLAY_NAME = "Qwen2-VL 2B (multimodal, default)"

        fun getActiveModelFilename(context: Context): String {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_FILENAME, DEFAULT_MODEL_FILENAME) ?: DEFAULT_MODEL_FILENAME
        }

        fun getActiveModelDisplayName(context: Context): String {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_DISPLAY_NAME, DEFAULT_MODEL_DISPLAY_NAME) ?: DEFAULT_MODEL_DISPLAY_NAME
        }

        fun getActiveModelIsMultimodal(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE_IS_MULTIMODAL, true)
        }
    }
}
