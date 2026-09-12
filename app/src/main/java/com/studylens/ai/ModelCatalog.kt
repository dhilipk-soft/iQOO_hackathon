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
    val available: Boolean
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
                    available = !url.contains(PLACEHOLDER_MARKER, ignoreCase = true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
