package stapel.conventions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceRulesTest {
    private fun r1(code: String) = ambientFindings(code).map { it.line }
    private fun r2(code: String) = cancellationFindings(code).map { it.line }

    @Test
    fun `each ambient source is found`() {
        listOf(
            "val d = Dispatchers.IO",
            "withContext(Dispatchers.Main.immediate) {}",
            "val now = Clock.System.now()",
            "val t = System.currentTimeMillis()",
            "val n = Random.nextInt(3)",
            "val r = Random.Default",
            "class D(val random: Random = Random)",
            "f(kotlin.random.Random)",
            "val id = Uuid.random()",
            "val id = UUID.randomUUID()",
            "val r = SecureRandom()",
            "val l = Locale.getDefault()",
            "val l = Locale.current",
            "val l = NSLocale.currentLocale",
            "val l = NSLocale.preferredLanguages",
            "val z = TimeZone.currentSystemDefault()",
            "val z = ZoneId.systemDefault()",
            "val z = NSTimeZone.localTimeZone",
        ).forEach { assertEquals(listOf(1), r1(it), it) }
    }

    @Test
    fun `injected seams, seeded randoms, comments and strings pass`() {
        listOf(
            "class D(val dispatchers: StapelDispatchers, val clock: Clock, val random: Random)",
            "val r = Random(42)",
            "fun f(r: Random): Int = r.nextInt()",
            "// uses Dispatchers.IO by default",
            "/** Never Clock.System: take a Clock. */ val x = 1",
            "val s = \"Locale.getDefault()\"",
            "val s = \"\"\"Random.Default\"\"\"",
            "val tag: LocaleTag = LocaleTag(\"en\")",
        ).forEach { assertEquals(emptyList(), r1(it), it) }
    }

    @Test
    fun `line numbers point at the hit`() {
        assertEquals(listOf(3), r1("package a\n\nval d = Dispatchers.Default\n"))
    }

    @Test
    fun `runCatching and unguarded catch-alls swallow cancellation`() {
        assertEquals(listOf(1), r2("val x = runCatching { f() }"))
        assertEquals(listOf(1), r2("val x = a.runCatching { f() }"))
        assertEquals(listOf(1), r2("try { f() } catch (e: Exception) { log(e) }"))
        assertEquals(listOf(1), r2("try { f() } catch (_: Throwable) { }"))
        assertEquals(listOf(1), r2("try { f() } catch (e: CancellationException) { log(e) }"))
        // A clause after an unrelated one is still unguarded.
        assertEquals(listOf(1), r2("try { f() } catch (e: IOException) { throw e } catch (e: Exception) { log(e) }"))
    }

    @Test
    fun `guarded catch-alls pass`() {
        listOf(
            "try { f() } catch (e: CancellationException) { throw e } catch (e: Exception) { log(e) }",
            "try { f() } catch (e: kotlinx.coroutines.CancellationException) {\n throw e\n} catch (e: Throwable) {\n log(e)\n}",
            "try { f() } catch (e: Exception) { if (e is CancellationException) throw e; log(e) }",
            "try { f() } catch (e: Exception) { close(); throw e }",
            "try { f() } catch (e: Throwable) { currentCoroutineContext().ensureActive(); log(e) }",
            "fun runCatchingStore() = 1",
            "// runCatching { }",
            "try { f() } catch (e: IOException) { log(e) }",
        ).forEach { assertEquals(emptyList(), r2(it), it) }
    }

    @Test
    fun `blanking keeps lines and length`() {
        val src = "a /* x\ny */ b \"c\nd\" // e\nf"
        val out = blankCommentsAndStrings(src)
        assertEquals(src.length, out.length)
        assertEquals(src.count { it == '\n' }, out.count { it == '\n' })
        assertTrue('x' !in out && 'e' !in out && 'a' in out && 'f' in out)
    }

    @Test
    fun `expression-bodied tests must declare Unit`() {
        assertEquals(1, testBodyFindings("@Test\nfun a() = runBlocking { list.add(1) }").size)
        assertEquals(1, testBodyFindings("@Test\n@Timeout(5)\nfun `named one`() = runBlocking { }").size)
        assertEquals(0, testBodyFindings("@Test\nfun a(): Unit = runBlocking { list.add(1) }").size)
        assertEquals(0, testBodyFindings("@Test\nfun a() = runTest { }").size)
        assertEquals(0, testBodyFindings("@Test\nfun a() {\n f()\n}").size)
    }

    private val today = java.time.LocalDate.parse("2026-11-19")
    private val hit = mapOf("src/commonMain/kotlin/A.kt" to listOf(SourceFinding("G-R1", 3, "Clock.System")))

    @Test
    fun `the baseline tolerates a listed hit until its date, then fails`() {
        val entry = parseBaseline(listOf("stapel-x src/commonMain/kotlin/A.kt G-R1 2026-12-01  # owner"))
        assertEquals(emptyList(), applyBaseline("stapel-x", hit, entry, today))
        assertEquals(1, applyBaseline("stapel-x", hit, entry, java.time.LocalDate.parse("2026-12-02")).size)
        // Another module, file or rule is not covered.
        assertEquals(1, applyBaseline("stapel-y", hit, entry, today).size)
    }

    @Test
    fun `a baseline line that matches nothing fails`() {
        val entry = parseBaseline(listOf("stapel-x src/commonMain/kotlin/B.kt G-R2 2026-12-01"))
        val errors = applyBaseline("stapel-x", emptyMap(), entry, today)
        assertTrue(errors.single().contains("remove the line"))
    }
}

