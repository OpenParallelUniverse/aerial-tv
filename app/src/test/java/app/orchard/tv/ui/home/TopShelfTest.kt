package app.orchard.tv.ui.home

import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import app.orchard.tv.topshelf.ShelfKind
import app.orchard.tv.topshelf.ShelfProgram
import app.orchard.tv.ui.components.Pressable
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
class TopShelfTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var host: ActivityController<ComponentActivity>? = null
    private lateinit var view: View
    private val navigation = ShelfNavigation()
    private val app = FocusRequester()
    private var opened: String? = null
    private var returned = 0
    private var up = 0
    private var rows by mutableStateOf(listOf(program("one"), program("two"), program("rec", ShelfKind.Recommend)))

    @After fun close() { compose.runOnUiThread { host?.close() } }

    private fun mount() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java)
            host = controller
            controller.setup()
            controller.get().setContent {
                view = LocalView.current
                Box(Modifier.size(960.dp, 540.dp)) {
                    TopShelf("test.player", "Player", rows, navigation, 150.dp,
                        onFocus = {}, onDown = { returned++; app.requestFocus() }, onUp = { up++; app.requestFocus() },
                        onOpen = { opened = it.id })
                    Pressable({}, focusRequester = app, modifier = Modifier.align(Alignment.BottomStart)) {
                        BasicText("Source app")
                    }
                }
            }
            controller.windowFocusChanged(true)
        }
        settle()
        compose.runOnUiThread { navigation.enter!!.invoke() }
        settle()
    }

    private fun settle() { repeat(25) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() } }
    private fun tap(code: Int) {
        compose.runOnUiThread {
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        settle()
    }

    @Test fun entersContentSelectsNextAndReturnsToSourceApp() {
        mount()
        tap(KeyEvent.KEYCODE_DPAD_RIGHT)
        tap(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("two", opened)
        tap(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(1, returned)
        compose.runOnUiThread { navigation.enter!!.invoke() }
        settle()
        opened = null
        tap(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("two", opened)
    }

    @Test fun remoteReachesRecommendationsAndReturnsFromStatusToSelectedCard() {
        mount()
        tap(KeyEvent.KEYCODE_DPAD_RIGHT)
        tap(KeyEvent.KEYCODE_DPAD_RIGHT)
        tap(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("rec", opened)
        tap(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(1, up)
        compose.runOnUiThread { navigation.enter!!.invoke() }
        settle()
        opened = null
        tap(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("rec", opened)
    }

    @Test fun removingFocusedProgramRecoversToAValidCard() {
        mount()
        tap(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.runOnUiThread { rows = rows.filterNot { it.id == "two" } }
        settle()
        tap(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("one", opened)
    }

    private fun program(id: String, kind: ShelfKind = ShelfKind.Continue) =
        ShelfProgram(id, "test.player", id, null, "https://example.test/$id", kind)
}
