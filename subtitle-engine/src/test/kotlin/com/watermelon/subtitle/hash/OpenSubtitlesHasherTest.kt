package com.watermelon.subtitle.hash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files

class OpenSubtitlesHasherTest {

    @Test
    fun hashesMinimumTwoChunksOfZeroBytes() {
        val file = Files.createTempFile("opensubtitles-128k-", ".bin").toFile()
        try {
            file.writeBytes(ByteArray(131_072))
            assertEquals("0000000000020000", OpenSubtitlesHasher.hash(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun hashesLargerZeroFilledFileIncludingItsSize() {
        val file = Files.createTempFile("opensubtitles-large-", ".bin").toFile()
        try {
            file.writeBytes(ByteArray(262_144))
            assertEquals("0000000000040000", OpenSubtitlesHasher.hash(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun preservesUnsignedHighBitThroughWrappingLongArithmetic() {
        val bytes = ByteArray(131_072)
        // First little-endian uint64 word of the head chunk is 0x8000000000000000.
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
    fun sumsHeadAndTailWithoutOverlapAtMinimumSize() {
        val bytes = ByteArray(131_072)
        bytes[0] = 1                 // head word = 1
        bytes[65_536] = 2            // tail word = 2
        val file = Files.createTempFile("opensubtitles-head-tail-", ".bin").toFile()
        try {
            file.writeBytes(bytes)
            // 1 + 2 + fileSize(0x20000) = 0x20003
            assertEquals("0000000000020003", OpenSubtitlesHasher.hash(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsFilesSmallerThanTwoChunks() {
        listOf(65_535, 65_536, 131_071).forEach { size ->
            val file = Files.createTempFile("opensubtitles-short-", ".bin").toFile()
            try {
                file.writeBytes(ByteArray(size))
                assertThrows(IllegalArgumentException::class.java) {
                    OpenSubtitlesHasher.hash(file)
                }
            } finally {
                file.delete()
            }
        }
    }
}
