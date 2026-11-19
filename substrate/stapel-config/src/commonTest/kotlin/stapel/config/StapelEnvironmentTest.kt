package stapel.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val client = ClientInfo("com.example.app", "1.2.0", "42", ClientPlatform.Android, "Android", "15", "Pixel 9", sdkVersion = "0.1.0")

class StapelEnvironmentTest {
    private val env = StapelEnvironment(
        name = "production",
        posture = Posture.Live,
        endpoints = mapOf("auth" to ServiceEndpoint("https://auth.example.com/auth/api/v1")),
        defaultEndpoint = ServiceEndpoint("https://api.example.com/"),
        client = client,
    )

    @Test
    fun endpointsResolvePerModuleThenDefault() {
        assertEquals("https://auth.example.com/auth/api/v1/", env.endpoint("auth").baseUrl)
        assertEquals("https://api.example.com/", env.endpoint("profiles").baseUrl)
        val bare = StapelEnvironment("x", Posture.Live, mapOf("auth" to ServiceEndpoint("https://a.example.com/")), client = client)
        val e = assertFailsWith<IllegalStateException> { bare.endpoint("chat") }
        assertTrue("'chat'" in e.message!!)
    }

    @Test
    fun canonicalPathsAreRebasedOntoTheModuleMount() {
        assertEquals("https://auth.example.com/auth/api/v1/sessions/7/", env.url("auth", "/auth/api/v1/sessions/7/"))
        assertEquals("https://auth.example.com/auth/api/v1/sessions/", env.url("auth", "sessions/"))
        val gateway = StapelEnvironment("g", Posture.Live, mapOf("auth" to ServiceEndpoint("https://gw.example.com/identity/")), client = client)
        assertEquals("https://gw.example.com/identity/otp/", gateway.url("auth", "/auth/api/v2/otp/"))
        assertEquals("https://gw.example.com/health/", gateway.url("auth", "/health/"))
        // A bare-origin endpoint (monolith) serves every canonical mount as is.
        assertEquals("https://api.example.com/profiles/api/v1/me/", env.url("profiles", "/profiles/api/v1/me/"))
        assertEquals("https://api.example.com/me/", env.url("profiles", "me/"))
    }

    @Test
    fun websocketOriginDropsThePath() {
        assertEquals("wss://auth.example.com", env.websocketOrigin("auth"))
        val local = StapelEnvironment.single("local", "http://127.0.0.1:8000/x/", Posture.Prototype, client)
        assertEquals("ws://127.0.0.1:8000", local.websocketOrigin("any"))
    }

    @Test
    fun liveRejectsCleartextAtConstruction() {
        assertFailsWith<IllegalArgumentException> {
            StapelEnvironment.single("prod", "http://10.0.0.5/", Posture.Live, client)
        }
        assertFailsWith<IllegalArgumentException> {
            StapelEnvironment("prod", Posture.Live, mapOf("auth" to ServiceEndpoint("http://localhost/")), client = client)
        }
    }

    @Test
    fun prototypeAllowsCleartextOnlyToLocalHosts() {
        for (url in listOf("http://localhost:8000/", "http://127.0.0.1/", "http://10.0.2.2:8000/", "http://192.168.1.20/", "http://172.20.0.3/", "http://box.local/", "http://[::1]:8000/")) {
            StapelEnvironment.single("local", url, Posture.Prototype, client)
        }
        for (url in listOf("http://api.example.com/", "http://8.8.8.8/", "http://172.32.0.1/")) {
            assertFailsWith<IllegalArgumentException>(url) { StapelEnvironment.single("local", url, Posture.Prototype, client) }
        }
        StapelEnvironment.single("local", "https://api.example.com/", Posture.Prototype, client)
    }

    @Test
    fun endpointValidation() {
        assertFailsWith<IllegalArgumentException> { ServiceEndpoint("api.example.com/") }
        assertFailsWith<IllegalArgumentException> { ServiceEndpoint("ftp://api.example.com/") }
        assertEquals("api.example.com", ServiceEndpoint("https://api.example.com:8443/v1").host)
        assertEquals("https://api.example.com:8443", ServiceEndpoint("https://api.example.com:8443/v1").origin)
        assertEquals(ServiceEndpoint("https://a.example.com"), ServiceEndpoint("https://a.example.com/"))
        assertFalse(isLocalHost("example.com"))
    }

    @Test
    fun userAgentAndClientHints() {
        assertEquals("com.example.app/1.2.0 (Android 15; Pixel 9) stapel-kmp/0.1.0", client.userAgent)
        assertEquals(
            mapOf("Sec-CH-UA-Platform" to "\"Android\"", "Sec-CH-UA-Platform-Version" to "\"15\"", "Sec-CH-UA-Model" to "\"Pixel 9\""),
            client.clientHints,
        )
        val desktop = client.copy(platform = ClientPlatform.Desktop, osName = "Mac OS X", deviceModel = null)
        assertEquals("com.example.app/1.2.0 (Mac OS X 15) stapel-kmp/0.1.0", desktop.userAgent)
        assertFalse("Sec-CH-UA-Model" in desktop.clientHints)
    }

    @Test
    fun platformClientInfoCarriesTheBuildVersion() {
        val info = platformClientInfo("com.example.app", "1.0", "1")
        assertTrue(info.sdkVersion.isNotBlank())
        assertTrue(info.userAgent.startsWith("com.example.app/1.0 ("))
    }
}
