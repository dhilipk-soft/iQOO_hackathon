package com.studylens.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One entry in the in-app model picker. `available = false` means the manifest lists it
 * but no working download URL has been provided yet (e.g. Gemma, pending the team's
 * re-hosted public copy - see models.json and focus-insights-feature.md sibling docs
 * for why Gemma can't just point at Google's original gated repo).
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
    /** Reads app/src/main/assets/models.json - bundled with the app, no network needed to
     * see the list itself, only to actually download a model. */
    fun loadModels(context: Context): List<DownloadableModel> {
        return try {
            val json = context.assets.open("models.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val arr: JSONArray = root.getJSONArray("models")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DownloadableModel(
                    id = o.getString("id"),
                    displayName = o.getString("displayName"),
                    description = o.getString("description"),
                    filename = o.getString("filename"),
                    sizeBytes = o.getLong("sizeBytes"),
                    downloadUrl = o.getString("downloadUrl"),
                    available = o.optBoolean("available", true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
