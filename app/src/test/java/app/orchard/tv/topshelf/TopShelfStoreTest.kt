package app.orchard.tv.topshelf

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TopShelfStoreTest {
    @Test fun queryUsesNoSqlSelectionAndReadsWatchNextFields() {
        val context: Context = RuntimeEnvironment.getApplication()
        var selected: String? = "not called"
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("must be read-only")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("must be read-only")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("must be read-only")
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
                selected = selection
                assertNotNull(projection)
                return MatrixCursor(projection).apply {
                    addRow(projection!!.map { column -> when (column) {
                        "browsable" -> 1L
                        "watch_next_type" -> 2L
                        "season_display_number" -> "1"
                        "episode_display_number" -> "5"
                        "episode_title" -> "Episode five"
                        "content_id" -> "film-7"
                        "_id" -> 7L
                        "package_name" -> "test.player"
                        "title" -> "A film"
                        "intent_uri" -> "https://example.test/7"
                        "thumbnail_uri" -> "content://test.player/art/7"
                        "last_playback_position_millis" -> 400L
                        "duration_millis" -> 1000L
                        "last_engagement_time_utc_millis" -> 12345L
                        else -> null
                    } })
                }
            }
        }
        provider.attachInfo(context, null)
        ShadowContentResolver.registerProviderInternal("android.media.tv", provider)
        val store = TopShelfStore(context, CoroutineScope(Dispatchers.Unconfined))
        val rows = store.readPrograms(Uri.parse("content://android.media.tv/watch_next_program"), ShelfKind.Continue)
        assertNull(selected)
        assertEquals(1, rows.size)
        assertEquals("test.player", rows.single().packageName)
        assertEquals("content://test.player/art/7", rows.single().posterUri)
        assertEquals(0.4f, rows.single().progress!!, 0.001f)
        assertEquals(12345L, rows.single().engagementTime)
        assertEquals(2, rows.single().watchNextType)
        assertEquals("1", rows.single().season)
        assertEquals("5", rows.single().episode)
        assertEquals("film-7", rows.single().contentId)
    }
}
