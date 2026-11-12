package app.orchard.tv.topshelf

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShelfProgramTest {
    private fun program(id: String = "1", kind: ShelfKind = ShelfKind.Continue, uri: String = "https://example.test/watch/$id") =
        ShelfProgram(id, "tv.example.player", "Film $id", null, uri, kind)

    @Test fun playbackProgressHandlesUnknownAndInvalidDurations() {
        assertNull(program().progress)
        assertEquals(0.4f, program().copy(positionMs = 40, durationMs = 100).progress!!, 0.001f)
        assertEquals(1f, program().copy(positionMs = Long.MAX_VALUE, durationMs = 100).progress!!, 0.001f)
        assertNull(program().copy(positionMs = -1, durationMs = 100).progress)
    }

    @Test fun contentLinksStayInsideThePublishingAppAndDropGrantFlags() {
        val intent = program().launchIntent()!!
        assertEquals("tv.example.player", intent.`package`)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
        assertNull(program(uri = "intent://watch#Intent;component=other.app/.Watch;end").launchIntent())
        assertNull(program(uri = "intent://watch#Intent;package=other.app;end").launchIntent())
        val own = program(uri = "intent://watch#Intent;scheme=player;package=tv.example.player;launchFlags=0x3;end").launchIntent()!!
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, own.flags)
    }

    @Test fun continueWinsDuplicatesAndRecentEngagementWinsWithinItsGroup() {
        val duplicate = program(kind = ShelfKind.Recommend)
        val older = program().copy(engagementTime = 1)
        val newer = program("2").copy(engagementTime = 50)
        val grouped = groupShelfPrograms(listOf(duplicate, older, newer)).getValue("tv.example.player")
        assertEquals(listOf("2", "1"), grouped.map { it.id })
        assertTrue(grouped.all { it.kind == ShelfKind.Continue })
    }

    @Test fun boundsEachCategoryIndependentlyAndSeparatesApps() {
        val rows = (1..30).map { program("c$it") } +
            (1..30).map { program("r$it", ShelfKind.Recommend) } +
            program("other").copy(packageName = "another.app") + program("blank").copy(title = " ")
        val result = groupShelfPrograms(rows)
        assertEquals(40, result.getValue("tv.example.player").size)
        assertEquals(1, result.getValue("another.app").size)
    }
}
