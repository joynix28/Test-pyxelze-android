import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

fun main() {
    val img = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)
    val baos = ByteArrayOutputStream()
    ImageIO.write(img, "png", baos)
    val pngBytes = baos.toByteArray()

    val payload = "Hello World".toByteArray()

    val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    val buffer = ByteBuffer.wrap(pngBytes)
    val signature = ByteArray(8)
    buffer.get(signature)
    if (!signature.contentEquals(PNG_SIGNATURE)) {
        throw IllegalArgumentException("Not a valid PNG file")
    }

    val out = ByteArrayOutputStream()
    out.write(signature)

    var inserted = false

    while (buffer.hasRemaining()) {
        if (buffer.remaining() < 8) break
        val length = buffer.int
        val typeBytes = ByteArray(4)
        buffer.get(typeBytes)
        val type = String(typeBytes, Charsets.US_ASCII)

        // Inject before the first IDAT
        if (type == "IDAT" && !inserted) {
            println("Found IDAT, injecting")
            // writeChunk
            val dos = java.io.DataOutputStream(out)
            dos.writeInt(payload.size)
            val typeBytes = "SVLT".toByteArray(Charsets.US_ASCII)
            dos.write(typeBytes)
            dos.write(payload)
            val crc = CRC32()
            crc.update(typeBytes)
            crc.update(payload)
            dos.writeInt(crc.value.toInt())

            inserted = true
        }

        buffer.position(buffer.position() - 8)
        val fullChunk = ByteArray(length + 12)
        buffer.get(fullChunk)
        out.write(fullChunk)
    }

    println("Done: ${out.toByteArray().size}")
}
