package app.orchard.tv.ui.home

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import app.orchard.tv.ui.components.Pressable
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PressableHoldTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var host: ActivityController<ComponentActivity>? = null
    private lateinit var view: View
    private val first = FocusRequester()
    private val second = FocusRequester()
    private var clicks = 0
    private var holds = 0
    @After fun close() { compose.runOnUiThread { host?.close() } }
    private fun settle() { repeat(10) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() } }
    private fun mount() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            host = Robolectric.buildActivity(ComponentActivity::class.java).also { c ->
                c.setup()
                c.get().setContent {
                    view = LocalView.current
                    Column {
                        Pressable({ clicks++ }, focusRequester = first, onLongPress = { holds++; second.requestFocus() }) { BasicText("First") }
                        Pressable({ clicks++ }, focusRequester = second) { BasicText("Second") }
                    }
                }
                c.windowFocusChanged(true)
            }
        }
        settle()
        compose.runOnUiThread { first.requestFocus() }
        settle()
    }
    private fun key(action: Int) { compose.runOnUiThread { view.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_DPAD_CENTER)) }; settle() }
    @Test fun shortPressClicksOnce() {
        mount(); key(KeyEvent.ACTION_DOWN); key(KeyEvent.ACTION_UP)
        assertEquals(1, clicks); assertEquals(0, holds)
    }
    @Test fun longPressTransfersFocusWithoutActivatingTheNewControlOnRelease() {
        mount(); key(KeyEvent.ACTION_DOWN)
        compose.mainClock.advanceTimeBy(650)
        compose.runOnUiThread { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(650)) }
        settle()
        key(KeyEvent.ACTION_UP)
        assertEquals(1, holds); assertEquals(0, clicks)
    }
}
