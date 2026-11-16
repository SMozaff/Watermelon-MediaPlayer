package com.watermelon.subtitle.hash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files

class OpenSubtitlesHasherTest {

    @Test
    fun hashesExactlyOneChunkOfZeroBytes() {
        val file = Files.createTempFile("opensubtitles-64k-", ".bin").toFile()
        try {
            file.writeBytes(ByteArray(65_536))
            assertEquals("0000000000010000", OpenSubtitlesHasher.hash(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun hashesLargerZeroFilledFileIncludingItsSize() {
        val file = Files.createTempFile("opensubtitles-large-", ".bin").toFile()
        try {
            file.writeBytes(ByteArray(131_072))
            assertEquals("0000000000020000", OpenSubtitlesHasher.hash(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun preservesUnsignedHighBitThroughWrappingLongArithmetic() {
        val bytes = ByteArray(131_072)
        // First little-endian uint64 word is 0x8000000000000000. The remaining
        // words and the final 64 KiB chunk are zero.
        bytes[7] = 0x80.toByte()
        val file = Files.createTempFile("opensubtitles-high-bit-", ".bin").toFile()
        try {
            file.writeBytes(bytes)
            assertEquals("8000000000020000", OpenSubtitlesHasher.hash(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsFilesSmallerThanOneChunk() {
        val file = Files.createTempFile("opensubtitles-short-", ".bin").toFile()
        try {
            file.writeBytes(ByteArray(65_535))
            assertThrows(IllegalArgumentException::class.java) {
                OpenSubtitlesHasher.hash(file)
            }
        } finally {
            file.delete()
        }
    }
}
