package com.clementine.panacea.sound

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Rewrites AIFF and AIFF-C files as WAV, which Android can play and AIFF it can't. The samples are
 * the same; only the byte order and the wrapper change. Covers integer PCM of any width, `sowt`,
 * 32- and 64-bit float, µ-law and A-law, which is what AIFF files hold in practice.
 */
object Aiff {
    class Unsupported(message: String) : Exception(message)

    private const val WAV_PCM = 1
    private const val WAV_FLOAT = 3
    private const val WAV_ALAW = 6
    private const val WAV_MULAW = 7

    fun isAiff(head: ByteArray): Boolean =
        head.size >= 12 && tag(head, 0) == "FORM" && (tag(head, 8) == "AIFF" || tag(head, 8) == "AIFC")

    fun toWav(aiff: ByteArray): ByteArray {
        if (!isAiff(aiff)) throw Unsupported("Not an AIFF file")
        val compressed = tag(aiff, 8) == "AIFC"
        var channels = 0
        var bits = 0
        var rate = 0.0
        var compression = "NONE"
        var data: ByteArray? = null

        var pos = 12
        while (pos + 8 <= aiff.size) {
            val id = tag(aiff, pos)
            val size = ByteBuffer.wrap(aiff, pos + 4, 4).int.toLong() and 0xFFFFFFFFL
            val body = pos + 8
            val end = (body + size).coerceAtMost(aiff.size.toLong()).toInt()
            when (id) {
                "COMM" -> {
                    val b = ByteBuffer.wrap(aiff, body, end - body)
                    channels = b.short.toInt()
                    b.int // sample frames; the sound data says the same
                    bits = b.short.toInt()
                    rate = extended(aiff, body + 8)
                    if (compressed && end - body >= 22) compression = tag(aiff, body + 18)
                }
                "SSND" -> {
                    val offset = ByteBuffer.wrap(aiff, body, 4).int
                    val start = body + 8 + offset
                    if (start <= end) data = aiff.copyOfRange(start, end)
                }
            }
            pos = body + ((size + 1) and 0x1FFFFFFFEL).toInt()   // chunks are padded to an even length
        }

        val samples = data ?: throw Unsupported("No sound data")
        if (channels <= 0 || rate <= 0 || rate > Int.MAX_VALUE) throw Unsupported("Unreadable format")
        val bytesPer = (bits + 7) / 8
        return when (compression) {
            "NONE", "twos" -> {
                if (bytesPer !in 1..4) throw Unsupported("Unsupported sample size")
                wav(WAV_PCM, channels, rate.toInt(), bytesPer * 8, integerToLittleEndian(samples, bytesPer, bigEndian = true))
            }
            "sowt" -> {
                if (bytesPer !in 1..4) throw Unsupported("Unsupported sample size")
                wav(WAV_PCM, channels, rate.toInt(), bytesPer * 8, integerToLittleEndian(samples, bytesPer, bigEndian = false))
            }
            "fl32", "FL32" -> wav(WAV_FLOAT, channels, rate.toInt(), 32, reverseEach(samples, 4))
            "fl64", "FL64" -> wav(WAV_FLOAT, channels, rate.toInt(), 32, doubleToFloat(samples))
            "ulaw", "ULAW" -> wav(WAV_MULAW, channels, rate.toInt(), 8, samples)
            "alaw", "ALAW" -> wav(WAV_ALAW, channels, rate.toInt(), 8, samples)
            else -> throw Unsupported("Compressed AIFF ($compression)")
        }
    }

    /** WAV wants little-endian samples, and unsigned ones at 8 bits. */
    private fun integerToLittleEndian(data: ByteArray, width: Int, bigEndian: Boolean): ByteArray {
        val out = ByteArray(data.size - data.size % width)
        for (i in out.indices step width) {
            for (j in 0 until width) out[i + j] = data[if (bigEndian) i + width - 1 - j else i + j]
            if (width == 1) out[i] = (out[i] + 128).toByte()
        }
        return out
    }

    private fun reverseEach(data: ByteArray, width: Int): ByteArray = integerToLittleEndian(data, width, bigEndian = true)

    private fun doubleToFloat(data: ByteArray): ByteArray {
        val input = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val out = ByteBuffer.allocate(data.size / 8 * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(data.size / 8) { out.putFloat(input.double.toFloat()) }
        return out.array()
    }

    private fun wav(format: Int, channels: Int, rate: Int, bits: Int, samples: ByteArray): ByteArray {
        val block = channels * bits / 8
        val b = ByteBuffer.allocate(44 + samples.size + samples.size % 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + samples.size + samples.size % 2).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16)
            .putShort(format.toShort()).putShort(channels.toShort())
            .putInt(rate).putInt(rate * block)
            .putShort(block.toShort()).putShort(bits.toShort())
        b.put("data".toByteArray()).putInt(samples.size).put(samples)
        return b.array()
    }

    /** The 80-bit IEEE 754 extended float AIFF uses for the sample rate. */
    internal fun extended(bytes: ByteArray, at: Int): Double {
        val exponent = ((bytes[at].toInt() and 0x7F) shl 8) or (bytes[at + 1].toInt() and 0xFF)
        val mantissa = ByteBuffer.wrap(bytes, at + 2, 8).long
        if (exponent == 0 && mantissa == 0L) return 0.0
        // The mantissa is unsigned with an explicit leading bit.
        val m = (mantissa ushr 11).toDouble() * 2048.0 + (mantissa and 0x7FF).toDouble()
        val value = m * Math.pow(2.0, (exponent - 16383 - 63).toDouble())
        return if (bytes[at].toInt() and 0x80 != 0) -value else value
    }

    private fun tag(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.ISO_8859_1)
}
