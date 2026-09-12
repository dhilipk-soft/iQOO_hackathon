package com.studylens.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One entry in the in-app model picker. `available` is derived from downloadUrl, not read
 * from the JSON as a separate manual flag.
 * `isMultimodal` dictates whether image uploads are supported.
 */
data class DownloadableModel(
    val id: String,
    val displayName: String,
    val description: String,
    val filename: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val available: Boolean,
    val isMultimodal: Boolean
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
                DownloadableModel(
                    id = o.getString("id"),
                    displayName = o.getString("displayName"),
                    description = o.getString("description"),
                    filename = o.getString("filename"),
                    sizeBytes = o.getLong("sizeBytes"),
                    downloadUrl = url,
                    available = !url.contains(PLACEHOLDER_MARKER, ignoreCase = true),
                    isMultimodal = o.optBoolean("isMultimodal", true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
