package stapel.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Sha256Test {
    @Test
    fun knownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
        // Padding edges (55/56/64/119 bytes), several blocks, and UTF-8.
        val expected = mapOf(
            55 to "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            56 to "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            64 to "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
            119 to "31eba51c313a5c08226adf18d4a359cfdfd8d2e816b13f4af952f7ea6584dcfb",
            1000 to "41edece42d63e8d9bf515a9ba6932e1c20cbc9f5a5d134645adb5db1b9737ea3",
        )
        for ((n, hex) in expected) assertEquals(hex, sha256Hex("a".repeat(n)), "length $n")
        assertEquals("1de36a32af798da0c1ac9297603a320ed8fe567cf21c9177112a4ce914ebb8be", sha256Hex("\u043a\u043b\u044e\u0447"))
    }

    @Test
    fun aTokenIsNeverPrinted() {
        val token = PushToken("secret-token-value", TokenKind.Fcm, "firebase")
        assertFalse("secret" in token.toString())
        assertEquals(sha256Hex("secret-token-value"), token.fingerprint)
    }
}
