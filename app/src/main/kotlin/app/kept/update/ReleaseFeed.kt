package app.kept.update

import org.json.JSONArray

/** A newer build published in the public releases repo. */
data class UpdateInfo(
    val versionName: String,
    /** The build number, which is also the app's versionCode. */
    val build: Int,
    val apkUrl: String,
    val sizeBytes: Long,
    val notes: String,
)

private val TAG = Regex("""^v(\d+\.\d+\.\d+)-build-(\d+)$""")

/**
 * Picks the newest release newer than [currentBuild] from the GitHub releases JSON. Pure, so it's tested.
 * Tags look like `v0.2.1-build-3`; drafts and releases without an .apk are ignored.
 */
fun pickUpdate(releasesJson: String, currentBuild: Int): UpdateInfo? {
    val array = JSONArray(releasesJson)
    return (0 until array.length()).mapNotNull { i ->
        val r = array.getJSONObject(i)
        if (r.optBoolean("draft")) return@mapNotNull null
        val m = TAG.matchEntire(r.optString("tag_name")) ?: return@mapNotNull null
        val assets = r.optJSONArray("assets") ?: return@mapNotNull null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }
            ?: return@mapNotNull null
        UpdateInfo(
            versionName = m.groupValues[1],
            build = m.groupValues[2].toInt(),
            apkUrl = apk.getString("browser_download_url"),
            sizeBytes = apk.optLong("size"),
            notes = r.optString("body"),
        )
    }.filter { it.build > currentBuild }.maxByOrNull { it.build }
}
