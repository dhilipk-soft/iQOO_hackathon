package com.studylens.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One entry in the in-app model picker. `available` is derived from downloadUrl, not read
 * from the JSON as a separate manual flag - a real URL makes a model available automatically,
 * a REPLACE_ME placeholder (e.g. Gemma, pending the team's re-hosted public copy) doesn't.
 * This avoids the "I updated the URL but forgot to also flip available:true" mistake.
 */
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
    val knownIncompatible: Boolean = false
)

object ModelCatalog {
    private const val PLACEHOLDER_MARKER = "REPLACE_ME"

    /** Reads app/src/main/assets/models.json - bundled with the app, no network needed to
     * see the list itself, only to actually download a model. */
    fun loadModels(context: Context): List<DownloadableModel> {
        return try {
            val json = context.assets.open("models.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val arr: JSONArray = root.getJSONArray("models")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val url = o.getString("downloadUrl")
                DownloadableModel(
                    id = o.getString("id"),
                    displayName = o.getString("displayName"),
                    description = o.getString("description"),
                    filename = o.getString("filename"),
                    sizeBytes = o.getLong("sizeBytes"),
                    downloadUrl = url,
                    available = !url.contains(PLACEHOLDER_MARKER, ignoreCase = true),
                    knownIncompatible = o.optBoolean("knownIncompatible", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
