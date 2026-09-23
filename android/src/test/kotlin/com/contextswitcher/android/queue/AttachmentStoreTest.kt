package com.contextswitcher.android.queue

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/// A camera-sized photo arrives scaled to the long-side limit and upright,
/// under a name safe to put into the message text.
// [utest->dsn~android-message-images~1]
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttachmentStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `a large rotated photo is stored scaled, upright and as JPEG`() {
        val photo = tmp.newFile("PXL 2026.jpg")
        photo.outputStream().use { Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(photo.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val dir = File(tmp.root, "attachments")

        val stored = AttachmentStore(dir).store(photo.name) { photo.inputStream() }

        assertThat(stored.parentFile).isEqualTo(dir)
        assertThat(stored.name).matches("\\d{8}-\\d{6}-\\d{3}-PXL_2026\\.jpg")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(stored.path, bounds)
        assertThat(bounds.outWidth to bounds.outHeight).isEqualTo(1536 to 2048)
        assertThat(bounds.outMimeType).isEqualTo("image/jpeg")
    }

    @Test
    fun `something that is not an image is refused`() {
        val text = tmp.newFile("notes.txt").apply { writeText("not an image") }

        org.assertj.core.api.Assertions.assertThatThrownBy {
            AttachmentStore(File(tmp.root, "attachments")).store(text.name) { text.inputStream() }
        }.isInstanceOf(java.io.IOException::class.java)
    }
}
