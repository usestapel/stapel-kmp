package stapel.conventions

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Inversions of `checkIosFloor` (declarations) and `checkIosBinaries` (Mach-O and klibs). */
class IosFloorTest {

    // --- declarations ---

    @Test
    fun `an Xcode project above the floor fails, at the floor passes`() {
        val red = iosFloorViolations("a/iosApp.xcodeproj/project.pbxproj", "IPHONEOS_DEPLOYMENT_TARGET = 18.2;\nIPHONEOS_DEPLOYMENT_TARGET = 15.0;", "15.0")
        assertEquals(listOf("a/iosApp.xcodeproj/project.pbxproj: IPHONEOS_DEPLOYMENT_TARGET = 18.2 (must be 15.0)"), red)
        assertEquals(emptyList(), iosFloorViolations("p/project.pbxproj", "IPHONEOS_DEPLOYMENT_TARGET = 15.0;", "15.0"))
    }

    @Test
    fun `an Xcode project without a deployment target fails`() {
        assertTrue(iosFloorViolations("p/project.pbxproj", "SDKROOT = iphoneos;", "15.0").single().contains("declares no IPHONEOS_DEPLOYMENT_TARGET"))
    }

    @Test
    fun `xcconfig and Info plist above the floor fail`() {
        assertEquals(1, iosFloorViolations("c/Config.xcconfig", "IPHONEOS_DEPLOYMENT_TARGET = 16.0\n", "15.0").size)
        assertEquals(1, iosFloorViolations("c/Info.plist", "<key>MinimumOSVersion</key>\n  <string>17.0</string>", "15.0").size)
        assertEquals(0, iosFloorViolations("c/Info.plist", "<key>MinimumOSVersion</key><string>15.0</string>", "15.0").size)
    }

    @Test
    fun `an app build that links a framework must pin both konan targets to the floor`() {
        val noPin = "kotlin { iosArm64().binaries.framework { baseName = \"Shared\" } }"
        assertEquals(2, iosFloorViolations("app/build.gradle.kts", noPin, "15.0").size)
        val pinned = "val IOS_FLOOR = \"15.0\"\nkotlin { iosArm64 { binaries.framework { } } }\n" +
            "freeCompilerArgs += \"-Xoverride-konan-properties=osVersionMin.ios_arm64=\$IOS_FLOOR;osVersionMin.ios_simulator_arm64=\$IOS_FLOOR\""
        assertEquals(emptyList(), iosFloorViolations("app/build.gradle.kts", pinned, "15.0"))
        val high = pinned.replace("val IOS_FLOOR = \"15.0\"", "val IOS_FLOOR = \"16.4\"")
        assertEquals(2, iosFloorViolations("app/build.gradle.kts", high, "15.0").size)
        assertEquals(emptyList(), iosFloorViolations("lib/build.gradle.kts", "plugins { id(\"stapel.substrate\") }", "15.0"))
    }

    @Test
    fun `the library compiler argument pins both targets to the floor`() {
        assertEquals(
            "-Xoverride-konan-properties=osVersionMin.ios_arm64=15.0;osVersionMin.ios_simulator_arm64=15.0",
            iosFloorCompilerArg(),
        )
        assertEquals(emptyList(), iosFloorViolations("x/build.gradle.kts", "binaries.framework {}\n\"${iosFloorCompilerArg()}\"", IOS_FLOOR))
    }

    // --- binaries ---

    @Test
    fun `code above the floor fails, data-only above the floor is a note, code at the floor passes`(@TempDir dir: File) {
        val lib = File(dir, "libX.a").apply {
            writeBytes(
                archive(
                    "ok.o" to macho(minos = v(15, 0), code = true),
                    "a_rather_long_member_name.o" to macho(minos = v(16, 4), code = true),
                    "icudt.o" to macho(minos = v(18, 5), code = false),
                    "imports.o" to macho(minos = v(17, 0), code = false, undefined = 1),
                ),
            )
        }
        val scan = scanIosBinaries(listOf(lib), "15.0")
        assertEquals(4, scan.objects)
        assertEquals(2, scan.violations.size, scan.violations.toString())
        assertTrue(scan.violations.any { "a_rather_long_member_name.o) minos 16.4: has instructions" in it })
        assertTrue(scan.violations.any { "imports.o) minos 17.0: imports 1 symbol(s)" in it })
        assertTrue(scan.dataOnly.single().contains("icudt.o) minos 18.5: data only"))
        assertEquals(emptyList(), scanIosBinaries(listOf(File(dir, "t.kexe").apply { writeBytes(macho(v(15, 0), true)) }), "15.0").violations)
    }

    @Test
    fun `a universal binary is read per slice`(@TempDir dir: File) {
        val f = File(dir, "Fat").apply { writeBytes(fat(macho(v(15, 0), true), macho(v(16, 0), true))) }
        assertEquals(1, scanIosBinaries(listOf(f), "15.0").violations.size)
    }

    @Test
    fun `a klib with a newer version-min option or a newer bundled object fails`(@TempDir dir: File) {
        val good = klib(dir, "good.klib", "compilerOpts=-D_Float16\\=short", null)
        val opt = klib(dir, "opt.klib", "compilerOpts=-mios-version-min=16.0", null)
        val obj = klib(dir, "obj.klib", "unique_name=x", macho(v(17, 0), true))
        assertEquals(emptyList(), scanIosBinaries(listOf(good), "15.0").violations)
        assertTrue(scanIosBinaries(listOf(opt), "15.0").violations.single().contains("-mios-version-min=16.0"))
        assertTrue(scanIosBinaries(listOf(obj), "15.0").violations.single().contains("minos 17.0"))
    }

    @Test
    fun `versions compare numerically`() {
        assertTrue(compareVersions("15.0", "15") == 0)
        assertTrue(compareVersions("15.0.1", "15.0") > 0)
        assertTrue(compareVersions("9.3", "15.0") < 0)
        assertEquals("18.5", MachOReader.version(v(18, 5)))
    }

    // --- fixtures: minimal arm64 iOS-simulator objects ---

    private fun v(major: Int, minor: Int) = (major shl 16) or (minor shl 8)

    private fun macho(minos: Int, code: Boolean, undefined: Int = 0): ByteArray {
        val buildVersion = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x32).putInt(24).putInt(MachObject.PLATFORM_IOSSIMULATOR).putInt(minos).putInt(minos).putInt(0).array()
        val segment = ByteBuffer.allocate(72 + 80).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x19); putInt(152); put(ByteArray(16)); putLong(0); putLong(0); putLong(0); putLong(0)
            putInt(7); putInt(7); putInt(1); putInt(0)
            put("__text".toByteArray().copyOf(16)); put("__TEXT".toByteArray().copyOf(16))
            putLong(0); putLong(if (code) 4L else 0L); putInt(0); putInt(2); putInt(0); putInt(0)
            putInt(if (code) 0x80000400.toInt() else 0); putInt(0); putInt(0); putInt(0)
        }.array()
        val headerAndCmds = 32 + 24 + 152 + 24
        val symtab = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x2).putInt(24).putInt(headerAndCmds).putInt(undefined).putInt(headerAndCmds + undefined * 16).putInt(0).array()
        val header = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0xfeedfacf.toInt()).putInt(0x0100000c).putInt(0).putInt(1).putInt(3).putInt(24 + 152 + 24).putInt(0).putInt(0).array()
        val syms = ByteBuffer.allocate(undefined * 16).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(undefined) { putInt(0); put(0x01); put(0); putShort(0); putLong(0) }
        }.array()
        return header + buildVersion + segment + symtab + syms
    }

    private fun archive(vararg members: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("!<arch>\n".toByteArray())
        for ((name, data) in members) {
            val long = name.length > 15
            val nameField = if (long) "#1/${name.length}" else name
            val payload = if (long) name.toByteArray() + data else data
            out.write(String.format("%-16s%-12s%-6s%-6s%-8s%-10s`\n", nameField, "0", "0", "0", "644", payload.size).toByteArray())
            out.write(payload)
            if (payload.size % 2 == 1) out.write('\n'.code)
        }
        return out.toByteArray()
    }

    private fun fat(vararg slices: ByteArray): ByteArray {
        val headerSize = 8 + slices.size * 20
        val out = ByteBuffer.allocate(headerSize + slices.sumOf { it.size }).order(ByteOrder.BIG_ENDIAN)
        out.putInt(0xcafebabe.toInt()).putInt(slices.size)
        var offset = headerSize
        slices.forEach { s -> out.putInt(0x0100000c).putInt(0).putInt(offset).putInt(s.size).putInt(0); offset += s.size }
        slices.forEach { out.put(it) }
        return out.array()
    }

    private fun klib(dir: File, name: String, manifestLine: String, native: ByteArray?): File = File(dir, name).apply {
        ZipOutputStream(outputStream()).use { z ->
            z.putNextEntry(ZipEntry("default/manifest")); z.write("$manifestLine\nnative_targets=ios_arm64\n".toByteArray()); z.closeEntry()
            if (native != null) { z.putNextEntry(ZipEntry("default/targets/ios_arm64/native/lib.o")); z.write(native); z.closeEntry() }
        }
    }
}
