package com.pyxelze.roxify.core

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SteganographyHelperTest {

    @Test
    fun testEncodeAndDecode() {
        // Create a fake Bitmap of 100x100
        val carrier = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                carrier.setPixel(x, y, Color.rgb(200, 200, 200))
            }
        }

        val payload = "Hello Steganography!".toByteArray(Charsets.UTF_8)

        val stegoImage = SteganographyHelper.encode(carrier, payload)

        val extractedData = SteganographyHelper.decode(stegoImage)

        assertArrayEquals(payload, extractedData)
    }
}
