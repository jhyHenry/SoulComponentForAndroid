/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.soul.android.plugin.component.resolve.arsc

import com.google.common.io.ByteArrayDataOutput
import com.google.common.io.ByteStreams
import com.google.common.primitives.UnsignedBytes
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Provides utilities to decode/encode a String packed in an arsc resource file.
 */
object ResourceString {
    /**
     * Given a buffer and an offset into the buffer, returns a String. The `offset` is the
     * 0-based byte offset from the start of the buffer where the string resides. This should be the
     * location in memory where the string's character count, followed by its byte count, and then
     * followed by the actual string is located.
     *
     *
     * Here's an example UTF-8-encoded string of ab©:
     * <pre>
     * 03 04 61 62 C2 A9 00
     * ^ Offset should be here
    </pre> *
     *
     * @param buffer The buffer containing the string to decode.
     * @param offset Offset into the buffer where the string resides.
     * @param type   The encoding type that the [ResourceString] is encoded in.
     * @return The decoded string.
     */
    fun decodeString(buffer: ByteBuffer, offset: Int, type: Type): String {
        var offset = offset
        val length: Int
        val characterCount = decodeLength(buffer, offset, type)
        offset += computeLengthOffset(characterCount, type)
        // UTF-8 strings have 2 lengths: the number of characters, and then the encoding length.
        // UTF-16 strings, however, only have 1 length: the number of characters.
        if (type == Type.UTF8) {
            length = decodeLength(buffer, offset, type)
            offset += computeLengthOffset(length, type)
        } else {
            length = characterCount * 2
        }
        return String(buffer.array(), offset, length, type.charset())
    }

    /**
     * Encodes a string in either UTF-8 or UTF-16 and returns the bytes of the encoded string.
     * Strings are prefixed by 2 values. The first is the number of characters in the string.
     * The second is the encoding length (number of bytes in the string).
     *
     *
     * Here's an example UTF-8-encoded string of ab©:
     * <pre>03 04 61 62 C2 A9 00</pre>
     *
     * @param str  The string to be encoded.
     * @param type The encoding type that the [ResourceString] should be encoded in.
     * @return The encoded string.
     */
    fun encodeString(str: String, type: Type): ByteArray {
        val bytes = str.toByteArray(type.charset())
        // The extra 5 bytes is for metadata (character count + byte count) and the NULL terminator.
        val output = ByteStreams.newDataOutput(bytes.size + 5)
        encodeLength(output, str.length, type)
        if (type == Type.UTF8) {  // Only UTF-8 strings have the encoding length.
            encodeLength(output, bytes.size, type)
        }
        output.write(bytes)
        // NULL-terminate the string
        if (type == Type.UTF8) {
            output.write(0)
        } else {
            output.writeShort(0)
        }
        return output.toByteArray()
    }

    private fun encodeLength(output: ByteArrayDataOutput, length: Int, type: Type) {
        if (length < 0) {
            output.write(0)
            return
        }
        if (type == Type.UTF8) {
            if (length > 0x7F) {
                output.write(length and 0x7F00 shr 8 or 0x80)
            }
            output.write(length and 0xFF)
        } else {  // UTF-16
            // TODO(acornwall): Replace output with a little-endian output.
            if (length > 0x7FFF) {
                val highBytes = length and 0x7FFF0000 shr 16 or 0x8000
                output.write(highBytes and 0xFF)
                output.write(highBytes and 0xFF00 shr 8)
            }
            val lowBytes = length and 0xFFFF
            output.write(lowBytes and 0xFF)
            output.write(lowBytes and 0xFF00 shr 8)
        }
    }

    private fun computeLengthOffset(length: Int, type: Type): Int {
        return (if (type == Type.UTF8) 1 else 2) * if (length >= (if (type == Type.UTF8) 0x80 else 0x8000)) 2 else 1
    }

    private fun decodeLength(buffer: ByteBuffer, offset: Int, type: Type): Int {
        return if (type == Type.UTF8) decodeLengthUTF8(buffer, offset) else decodeLengthUTF16(buffer, offset)
    }

    private fun decodeLengthUTF8(buffer: ByteBuffer, offset: Int): Int {
        // UTF-8 strings use a clever variant of the 7-bit integer for packing the string length.
        // If the first byte is >= 0x80, then a second byte follows. For these values, the length
        // is WORD-length in big-endian & 0x7FFF.
        var length = UnsignedBytes.toInt(buffer[offset])
        if (length and 0x80 != 0) {
            length = length and 0x7F shl 8 or UnsignedBytes.toInt(buffer[offset + 1])
        }
        return length
    }

    private fun decodeLengthUTF16(buffer: ByteBuffer, offset: Int): Int {
        // UTF-16 strings use a clever variant of the 7-bit integer for packing the string length.
        // If the first word is >= 0x8000, then a second word follows. For these values, the length
        // is DWORD-length in big-endian & 0x7FFFFFFF.
        var length: Int = buffer.getShort(offset).toInt() and 0xFFFF
        if (length and 0x8000 != 0) {
            length = length and 0x7FFF shl 16 or (buffer.getShort(offset + 2).toInt() and 0xFFFF)
        }
        return length
    }

    /**
     * Type of [ResourceString] to encode / decode.
     */
    enum class Type(private val charset: Charset) {
        UTF8(StandardCharsets.UTF_8), UTF16(StandardCharsets.UTF_16LE);

        fun charset(): Charset {
            return charset
        }

    }
}