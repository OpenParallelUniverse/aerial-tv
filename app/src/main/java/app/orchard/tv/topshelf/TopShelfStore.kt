package app.orchard.tv.topshelf

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import app.orchard.tv.system.DiagLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
private val WATCH_NEXT = Uri.parse("content://android.media.tv/watch_next_program")
private val PREVIEWS = Uri.parse("content://android.media.tv/preview_program")
private val CHANNELS = Uri.parse("content://android.media.tv/channel")

enum class ShelfAccess { Unsupported, PermissionRequired, Ready, Unavailable }

/** Reads public TV listings only. Never writes browsable state or alters the stock launcher's rows. */
class TopShelfStore(private val context: Context, private val scope: CoroutineScope) {
    val preferences = ShelfPreferences(context)
    var programs by mutableStateOf<Map<String, List<ShelfProgram>>>(emptyMap()); private set
    var access by mutableStateOf(ShelfAccess.Unsupported); private set
    private var watching = false
    private var observed = false
    private var load: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 26 &&
        context.packageManager.resolveContentProvider("android.media.tv", 0) != null

    fun permitted(): Boolean = ContextCompat.checkSelfPermission(context, READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED

    fun watch(active: Boolean) {
        watching = active
        if (!active) {
            load?.cancel()
            if (observed) runCatching { context.contentResolver.unregisterContentObserver(observer) }
            observed = false
            return
        }
        refresh()
    }

    fun refresh() {
        if (!watching) return
        load?.cancel()
        load = scope.launch {
            if (!supported()) {
                access = ShelfAccess.Unsupported
                programs = emptyMap()
                DiagLog.i("top shelf: Unsupported")
                return@launch
            }
            if (!permitted()) {
                access = ShelfAccess.PermissionRequired
                programs = emptyMap()
                DiagLog.i("top shelf: PermissionRequired")
                return@launch
            }
            if (!observed) {
                observed = runCatching {
                    listOf(WATCH_NEXT, PREVIEWS, CHANNELS).forEach {
                        context.contentResolver.registerContentObserver(it, true, observer)
                    }
                    true
                }.getOrElse {
                    runCatching { context.contentResolver.unregisterContentObserver(observer) }
                    false
                }
            }
            delay(250)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val sources = listOf(
                        runCatching { readPrograms(WATCH_NEXT, ShelfKind.Continue) },
                        runCatching { readPrograms(PREVIEWS, ShelfKind.Recommend) },
                    )
                    val readable = sources.mapNotNull { it.getOrNull() }
                    if (readable.isEmpty()) throw sources.firstNotNullOf { it.exceptionOrNull() }
                    groupShelfPrograms(readable.flatten().filter { preferences.accepts(it) })
                }
            }
            result.onSuccess {
                programs = it.mapValues { (_, rows) -> rows.filter(preferences::accepts) }.filterValues { rows -> rows.isNotEmpty() }
                access = ShelfAccess.Ready
                DiagLog.i("top shelf: ${it.size} apps, ${it.values.sumOf { rows -> rows.size }} visible programs")
            }.onFailure {
                programs = emptyMap()
                access = if (it is SecurityException) ShelfAccess.PermissionRequired else ShelfAccess.Unavailable
                DiagLog.i("top shelf: ${access.name} (${it.javaClass.simpleName})")
            }
        }
    }

    fun hide(program: ShelfProgram) { preferences.hide(program); removeLocally() }
    fun disable(packageName: String) { preferences.disable(packageName); removeLocally() }
    fun restore() { preferences.restore(); refresh() }
    private fun removeLocally() {
        programs = programs.mapValues { (_, rows) -> rows.filter(preferences::accepts) }.filterValues { it.isNotEmpty() }
    }

    private fun readChannels(): Map<Long, String> = runCatching {
        context.contentResolver.query(CHANNELS, arrayOf("_id", "display_name"), null, null, null)?.use { c ->
            buildMap { while (c.moveToNext()) c.text("display_name")?.let { put(c.number("_id"), it) } }
        } ?: emptyMap()
    }.getOrDefault(emptyMap())

    internal fun readPrograms(uri: Uri, kind: ShelfKind): List<ShelfProgram> {
        val channelNames = if (kind == ShelfKind.Recommend) readChannels() else emptyMap()
        val projection = arrayOf("_id", "package_name", "title", "poster_art_uri", "thumbnail_uri", "intent_uri", "browsable", "season_display_number", "episode_display_number",
            "episode_title", "short_description", "internal_provider_id", "content_id") +
            if (kind == ShelfKind.Continue) arrayOf("last_playback_position_millis", "duration_millis", "last_engagement_time_utc_millis", "watch_next_type")
            else arrayOf("weight", "channel_id")
        // Non-privileged TvProvider callers cannot use SQL selection. Filter/group after reading.
        val cursor = try { context.contentResolver.query(uri, projection, null, null, null) }
        catch (_: IllegalArgumentException) {
            // Older vendor schemas may omit optional season/channel/content columns.
            val core = arrayOf("_id", "package_name", "title", "poster_art_uri", "thumbnail_uri", "intent_uri", "browsable") +
                if (kind == ShelfKind.Continue) arrayOf("last_playback_position_millis", "duration_millis", "last_engagement_time_utc_millis", "watch_next_type")
                else arrayOf("weight", "channel_id")
            context.contentResolver.query(uri, core, null, null, null)
        }
        return cursor?.use { c ->
            buildList {
                var scanned = 0
                while (scanned++ < 6000 && c.moveToNext()) {
                    val browsable = c.getColumnIndex("browsable")
                    if (browsable >= 0 && !c.isNull(browsable) && c.getLong(browsable) == 0L) continue
                    val pkg = c.text("package_name") ?: continue
                    val title = c.text("title") ?: continue
                    val intent = c.text("intent_uri") ?: continue
                    add(ShelfProgram(
                        id = "${kind.name}:${c.number("_id")}", packageName = pkg, title = title,
                        posterUri = c.text("poster_art_uri") ?: c.text("thumbnail_uri"), intentUri = intent, kind = kind,
                        positionMs = c.number("last_playback_position_millis"), durationMs = c.number("duration_millis"),
                        engagementTime = c.number("last_engagement_time_utc_millis"), weight = c.number("weight").toInt(),
                        watchNextType = c.number("watch_next_type").toInt(), season = c.text("season_display_number"),
                        episode = c.text("episode_display_number"), episodeTitle = c.text("episode_title"),
                        description = c.text("short_description"), channelName = channelNames[c.number("channel_id")],
                        contentId = c.text("content_id") ?: c.text("internal_provider_id"),
                    ))
                }
            }
        } ?: emptyList()
    }
}

private fun Cursor.text(name: String): String? = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }
    ?.let { getString(it)?.takeIf(String::isNotBlank) }
private fun Cursor.number(name: String): Long = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }
    ?.let { getLong(it) } ?: 0L
