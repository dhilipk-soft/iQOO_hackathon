package com.studylens.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One entry in the in-app model picker. `available` is derived from downloadUrl, not read
 * from the JSON as a separate manual flag.
 * `isMultimodal` dictates whether image uploads are supported.
 */
enum class ModelProfile {
    STANDARD,
    COMPACT
}

data class DownloadableModel(
    val id: String,
    val displayName: String,
    val description: String,
    val filename: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val available: Boolean,
    // Confirmed (not guessed) via real on-device errors that this file's format can't load
    // in litertlm-android's Engine API at all - e.g. .task files failing with "Unable to
    // open zip archive" or "TF_LITE_VISION_ENCODER not found". Distinct from `available`
    // (which is about hosting/URLs): a model can be fully hosted and downloaded and STILL be
    // knownIncompatible, in which case the picker must never offer to switch to it, even if
    // the file is already sitting on disk from earlier testing.
    val knownIncompatible: Boolean = false,
    // Drives whether the chat UI offers image upload for the active model - text-only
    // models (none currently in the catalog, but the field stays for when one works) should
    // hide that entry point rather than let the user attach a photo the model can't read.
    val isMultimodal: Boolean = true,
    val maxTokens: Int = 1536,
    val profile: ModelProfile = ModelProfile.STANDARD
)

object ModelCatalog {
    private const val PLACEHOLDER_MARKER = "REPLACE_ME"

    /** Reads app/src/main/assets/models.json - bundled with the app. */
    fun loadModels(context: Context): List<DownloadableModel> {
        return try {
            val json = context.assets.open("models.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val arr: JSONArray = root.getJSONArray("models")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val url = o.getString("downloadUrl")
                val profileStr = o.optString("profile", "STANDARD").uppercase()
                val profile = try { ModelProfile.valueOf(profileStr) } catch (e: Exception) { ModelProfile.STANDARD }
                DownloadableModel(
                    id = o.getString("id"),
                    displayName = o.getString("displayName"),
                    description = o.getString("description"),
                    filename = o.getString("filename"),
                    sizeBytes = o.getLong("sizeBytes"),
                    downloadUrl = url,
                    available = !url.contains(PLACEHOLDER_MARKER, ignoreCase = true),
                    knownIncompatible = o.optBoolean("knownIncompatible", false),
                    isMultimodal = o.optBoolean("isMultimodal", true),
                    maxTokens = o.optInt("maxTokens", 1536),
                    profile = profile
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
