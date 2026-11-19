package stapel.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartitionIdTest {
    @Test
    fun sha256MatchesFipsVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hex(ByteArray(0)),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc".encodeToByteArray()),
        )
        // Two-block message (padding crosses a block boundary).
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
        )
        // 55 and 56 bytes: the last length that fits one block and the first that does not.
        assertEquals(
            "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            Sha256.hex(ByteArray(55) { 'a'.code.toByte() }),
        )
        assertEquals(
            "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            Sha256.hex(ByteArray(56) { 'a'.code.toByte() }),
        )
    }

    @Test
    fun accountPartitionIsAHashNeverTheId() {
        val id = PartitionId.account("user-42@example.org")
        assertEquals("u-" + Sha256.hex("user-42@example.org".encodeToByteArray()).take(16), id.name)
        assertFalse("user" in id.name)
        assertTrue(id.isAccount)
        assertFalse(PartitionId.App.isAccount)
        assertEquals(PartitionId.account("user-42@example.org"), id)
        assertNotEquals(PartitionId.account("user-43@example.org"), id)
    }

    @Test
    fun parseAcceptsOnlyOwnNames() {
        val id = PartitionId.account("a")
        assertEquals(id, PartitionId.parse(id.name))
        assertEquals(PartitionId.App, PartitionId.parse("app"))
        assertNull(PartitionId.parse("u-XYZ"))
        assertNull(PartitionId.parse("u-0123456789abcdef0"))
        assertNull(PartitionId.parse("tmp"))
    }

    @Test
    fun emptyAccountIdIsRejected() {
        assertFailsWith<IllegalArgumentException> { PartitionId.account("") }
    }
}
