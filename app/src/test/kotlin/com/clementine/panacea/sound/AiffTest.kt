package com.clementine.panacea.sound

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AiffTest {

    /** 44100 as an 80-bit extended float. */
    private val rate44100 = byteArrayOf(0x40, 0x0E, 0xAC.toByte(), 0x44, 0, 0, 0, 0, 0, 0)

    private fun aiff(bits: Int, channels: Int, samples: ByteArray, compression: String? = null): ByteArray {
        val comm = ByteBuffer.allocate(if (compression == null) 18 else 24).order(ByteOrder.BIG_ENDIAN)
            .putShort(channels.toShort())
            .putInt(samples.size / channels / ((bits + 7) / 8).coerceAtLeast(1))
            .putShort(bits.toShort())
            .put(rate44100)
        if (compression != null) comm.put(compression.toByteArray()).put(0).put(0)   // empty, padded name
        val out = ByteArrayOutputStream()
        fun chunk(id: String, body: ByteArray) {
            out.write(id.toByteArray())
            out.write(ByteBuffer.allocate(4).putInt(body.size).array())
            out.write(body)
            if (body.size % 2 == 1) out.write(0)
        }
        chunk("COMM", comm.array())
        chunk("SSND", ByteArray(8) + samples)
        val body = out.toByteArray()
        return "FORM".toByteArray() + ByteBuffer.allocate(4).putInt(body.size + 4).array() +
            (if (compression == null) "AIFF" else "AIFC").toByteArray() + body
    }

    private fun wavHeader(wav: ByteArray) = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun recognisesAiffOnly() {
        assertTrue(Aiff.isAiff(aiff(16, 1, ByteArray(4))))
        assertFalse(Aiff.isAiff("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray()))
        assertFalse(Aiff.isAiff(ByteArray(3)))
    }

    @Test
    fun readsTheExtendedSampleRate() {
        assertEquals(44100.0, Aiff.extended(rate44100, 0), 0.0)
        val r48000 = byteArrayOf(0x40, 0x0E, 0xBB.toByte(), 0x80.toByte(), 0, 0, 0, 0, 0, 0)
        assertEquals(48000.0, Aiff.extended(r48000, 0), 0.0)
    }

    @Test
    fun sixteenBitBecomesLittleEndianPcm() {
        val wav = Aiff.toWav(aiff(16, 2, byteArrayOf(0x12, 0x34, 0x56, 0x78)))
        val b = wavHeader(wav)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(1, b.getShort(20).toInt())        // PCM
        assertEquals(2, b.getShort(22).toInt())        // channels
        assertEquals(44100, b.getInt(24))
        assertEquals(44100 * 4, b.getInt(28))          // bytes per second
        assertEquals(16, b.getShort(34).toInt())
        assertEquals(4, b.getInt(40))
        assertArrayEquals(byteArrayOf(0x34, 0x12, 0x78, 0x56), wav.copyOfRange(44, 48))
    }

    @Test
    fun eightBitTurnsUnsignedAndTwentyFourBitKeepsItsWidth() {
        val eight = Aiff.toWav(aiff(8, 1, byteArrayOf(0, -128, 127)))
        assertArrayEquals(byteArrayOf(-128, 0, -1), eight.copyOfRange(44, 47))
        val twentyFour = Aiff.toWav(aiff(24, 1, byteArrayOf(1, 2, 3)))
        assertEquals(24, wavHeader(twentyFour).getShort(34).toInt())
        assertArrayEquals(byteArrayOf(3, 2, 1), twentyFour.copyOfRange(44, 47))
    }

    @Test
    fun aifcVariantsAreCarriedOver() {
        val sowt = Aiff.toWav(aiff(16, 1, byteArrayOf(0x34, 0x12), "sowt"))
        assertArrayEquals(byteArrayOf(0x34, 0x12), sowt.copyOfRange(44, 46))

        val floatBytes = ByteBuffer.allocate(8).putDouble(0.5).array()
        val fromDouble = Aiff.toWav(aiff(64, 1, floatBytes, "fl64"))
        val b = wavHeader(fromDouble)
        assertEquals(3, b.getShort(20).toInt())        // IEEE float
        assertEquals(32, b.getShort(34).toInt())
        assertEquals(0.5f, b.getFloat(44), 0f)

        val ulaw = Aiff.toWav(aiff(8, 1, byteArrayOf(0x7F, 0x00), "ulaw"))
        assertEquals(7, wavHeader(ulaw).getShort(20).toInt())
    }

    private fun resource(name: String) = javaClass.getResourceAsStream("/aiff/$name")!!.use { it.readBytes() }

    /** Files written by ffmpeg, checked against ffmpeg's own decoding of them. */
    @Test
    fun matchesAnotherDecoderOnRealFiles() {
        for ((aiff, raw, format, bits) in listOf(
            listOf("s16-stereo.aiff", "s16-stereo.raw", 1, 16),
            listOf("s24.aiff", "s24.raw", 1, 24),
            listOf("f32.aifc", "f32.raw", 3, 32),
            listOf("s8.aiff", "s8-unsigned.raw", 1, 8),
        )) {
            val wav = Aiff.toWav(resource(aiff as String))
            val b = wavHeader(wav)
            assertEquals(aiff, format, b.getShort(20).toInt())
            assertEquals(aiff, 22050, b.getInt(24))
            assertEquals(aiff, bits, b.getShort(34).toInt())
            assertArrayEquals(aiff, resource(raw as String), wav.copyOfRange(44, 44 + b.getInt(40)))
        }
    }

    @Test(expected = Aiff.Unsupported::class)
    fun compressionAndroidCantPlayIsRefused() {
        Aiff.toWav(aiff(16, 1, ByteArray(4), "ima4"))
    }
}
