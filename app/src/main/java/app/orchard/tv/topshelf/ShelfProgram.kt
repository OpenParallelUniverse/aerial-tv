package app.orchard.tv.topshelf

import android.content.Intent

enum class ShelfKind { Continue, Recommend }

data class ShelfProgram(
    val id: String,
    val packageName: String,
    val title: String,
    val posterUri: String?,
    val intentUri: String,
    val kind: ShelfKind,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val engagementTime: Long = 0,
    val weight: Int = 0,
    val watchNextType: Int = 0,
    val season: String? = null,
    val episode: String? = null,
    val episodeTitle: String? = null,
    val description: String? = null,
    val channelName: String? = null,
    val contentId: String? = null,
) {
    val stableKey: String get() = packageName + ":" + kind.name + ":" + (contentId?.takeIf { it.isNotBlank() } ?: intentUri)
    val remainingMinutes: Long? get() = if (durationMs > positionMs && positionMs > 0) (durationMs - positionMs + 59_999) / 60_000 else null
    val progress: Float? get() = if (durationMs > 0 && positionMs > 0)
        (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat() else null

    /** Provider data is untrusted. Never launch a component outside its publishing package. */
    fun launchIntent(): Intent? = runCatching {
        val parsed = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
        if (parsed.component?.packageName?.let { it != packageName } == true ||
            parsed.`package`?.let { it != packageName } == true) return null
        parsed.selector = null
        parsed.clipData = null
        parsed.setPackage(packageName)
        parsed.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        parsed
    }.getOrNull()
}

/** Keep source order meaningful, deduplicate across sources, and bound the UI's bitmap demand. */
internal fun groupShelfPrograms(programs: List<ShelfProgram>): Map<String, List<ShelfProgram>> =
    programs.asSequence().filter { it.title.isNotBlank() && it.intentUri.isNotBlank() && it.launchIntent() != null }
        .sortedWith(compareBy<ShelfProgram> { it.kind.ordinal }
            .thenByDescending { it.engagementTime }.thenByDescending { it.weight })
        .distinctBy { it.packageName to it.intentUri }
        .groupBy { it.packageName }
        .mapValues { (_, items) -> ShelfKind.entries.flatMap { kind -> items.filter { it.kind == kind }.take(20) } }
