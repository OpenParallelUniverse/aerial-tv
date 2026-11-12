package app.orchard.tv.topshelf

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShelfImagesTest {
    @Test fun artworkUsesRequestedPixelSizeAndImmutableOutput() {
        val source = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
        val output = ShelfImages.bake(source, 392, 220)
        assertEquals(392, output.width); assertEquals(220, output.height)
        assertFalse(output.isMutable)
        assertFalse(source.isRecycled)
        ShelfImages.trimMemory()
        assertFalse(output.isRecycled)
    }
}
