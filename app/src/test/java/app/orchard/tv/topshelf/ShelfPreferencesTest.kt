package app.orchard.tv.topshelf

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShelfPreferencesTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private fun item() = ShelfProgram("row-1", "player.app", "Title", null, "https://example.test/1", ShelfKind.Continue, contentId = "film-1")

    @Test fun hiddenContentSurvivesReloadAndProviderRowRenumbering() {
        context.getSharedPreferences("top_shelf_choices", Context.MODE_PRIVATE).edit().clear().commit()
        val first = ShelfPreferences(context)
        first.hide(item())
        val reloaded = ShelfPreferences(context)
        assertFalse(reloaded.accepts(item().copy(id = "row-99")))
        assertTrue(reloaded.accepts(item().copy(contentId = "film-2")))
        assertTrue(reloaded.accepts(item().copy(packageName = "other.app")))
    }
    @Test fun appDisableIsPersistedAndRestoreMakesAllContentVisibleAgain() {
        context.getSharedPreferences("top_shelf_choices", Context.MODE_PRIVATE).edit().clear().commit()
        val first = ShelfPreferences(context)
        first.hide(item()); first.disable("other.app")
        val reloaded = ShelfPreferences(context)
        assertFalse(reloaded.accepts(item().copy(packageName = "other.app")))
        reloaded.restore()
        assertTrue(ShelfPreferences(context).accepts(item()))
        assertTrue(ShelfPreferences(context).accepts(item().copy(packageName = "other.app")))
    }
}
