package stapel.conventions

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One Mach-O object: its minimum OS, whether it holds instructions, how many symbols it imports. */
internal data class MachObject(
    val name: String,
    val platform: Int?,
    val minOs: String?,
    val hasCode: Boolean,
    val undefinedSymbols: Int,
) {
    /** iOS device (2), iOS simulator (7) or Mac Catalyst (6) by LC_BUILD_VERSION, or LC_VERSION_MIN_IPHONEOS. */
    val isIos: Boolean get() = platform == PLATFORM_IOS || platform == PLATFORM_IOSSIMULATOR || platform == PLATFORM_MACCATALYST

    companion object {
        const val PLATFORM_IOS: Int = 2
        const val PLATFORM_MACCATALYST: Int = 6
        const val PLATFORM_IOSSIMULATOR: Int = 7
    }
}

/**
 * A minimal reader of the load commands that decide where an object may run: thin 64-bit Mach-O,
 * universal (fat) files and `ar` archives (static libraries and static frameworks) of them.
 */
internal object MachOReader {
    private const val MH_MAGIC_64 = 0xfeedfacf.toInt()
    private const val FAT_MAGIC = 0xcafebabe.toInt()
    private const val FAT_MAGIC_64 = 0xcafebabf.toInt()
    private const val LC_SYMTAB = 0x2
    private const val LC_SEGMENT_64 = 0x19
    private const val LC_VERSION_MIN_IPHONEOS = 0x25
    private const val LC_BUILD_VERSION = 0x32
    private const val S_ATTR_PURE_INSTRUCTIONS = 0x80000000.toInt()
    private const val S_ATTR_SOME_INSTRUCTIONS = 0x400
    private val AR_MAGIC = "!<arch>\n".toByteArray()

    fun isMachOOrArchive(bytes: ByteArray): Boolean {
        if (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(AR_MAGIC)) return true
        if (bytes.size < 4) return false
        val le = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val be = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        // A fat header shares its magic with Java class files; those never reach this reader with an
        // nfat_arch this small, and a class file's next u16s (minor, major version) make it large.
        return le == MH_MAGIC_64 || ((be == FAT_MAGIC || be == FAT_MAGIC_64) && bytes.size >= 8 &&
            ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.BIG_ENDIAN).int in 1..16)
    }

    fun read(name: String, bytes: ByteArray): List<MachObject> {
        if (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(AR_MAGIC)) return readArchive(bytes)
        val be = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        return when (be.getInt(0)) {
            FAT_MAGIC, FAT_MAGIC_64 -> {
                val wide = be.getInt(0) == FAT_MAGIC_64
                val n = be.getInt(4)
                (0 until n).flatMap { i ->
                    val at = 8 + i * (if (wide) 32 else 20)
                    val offset = if (wide) be.getLong(at + 8).toInt() else be.getInt(at + 8)
                    val size = if (wide) be.getLong(at + 16).toInt() else be.getInt(at + 12)
                    read(name, bytes.copyOfRange(offset, offset + size))
                }
            }
            else -> listOfNotNull(readThin(name, bytes))
        }
    }

    private fun readArchive(bytes: ByteArray): List<MachObject> {
        val out = mutableListOf<MachObject>()
        var at = 8
        while (at + 60 <= bytes.size) {
            val header = String(bytes, at, 60, Charsets.US_ASCII)
            var memberName = header.substring(0, 16).trim()
            val size = header.substring(48, 58).trim().toInt()
            var data = at + 60
            var dataSize = size
            if (memberName.startsWith("#1/")) { // BSD long name: stored before the data, counted in size
                val len = memberName.removePrefix("#1/").toInt()
                memberName = String(bytes, data, len, Charsets.US_ASCII).trimEnd('\u0000')
                data += len
                dataSize -= len
            }
            if (!memberName.startsWith("__.SYMDEF") && memberName != "/" && memberName != "//") {
                val member = bytes.copyOfRange(data, data + dataSize)
                if (isMachOOrArchive(member)) out += read(memberName, member)
            }
            at += 60 + size + (size and 1)
        }
        return out
    }

    private fun readThin(name: String, bytes: ByteArray): MachObject? {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 32 || b.getInt(0) != MH_MAGIC_64) return null
        val ncmds = b.getInt(16)
        var at = 32
        var platform: Int? = null
        var minOs: String? = null
        var hasCode = false
        var undefined = 0
        repeat(ncmds) {
            val cmd = b.getInt(at)
            val size = b.getInt(at + 4)
            when (cmd) {
                LC_BUILD_VERSION -> {
                    platform = b.getInt(at + 8)
                    minOs = version(b.getInt(at + 12))
                }
                LC_VERSION_MIN_IPHONEOS -> {
                    platform = MachObject.PLATFORM_IOS
                    minOs = version(b.getInt(at + 8))
                }
                LC_SEGMENT_64 -> {
                    val nsects = b.getInt(at + 64)
                    for (s in 0 until nsects) {
                        val sect = at + 72 + s * 80
                        val sectSize = b.getLong(sect + 40)
                        val flags = b.getInt(sect + 64)
                        if (sectSize > 0 && (flags and (S_ATTR_PURE_INSTRUCTIONS or S_ATTR_SOME_INSTRUCTIONS)) != 0) hasCode = true
                    }
                }
                LC_SYMTAB -> {
                    val symoff = b.getInt(at + 8)
                    val nsyms = b.getInt(at + 12)
                    for (i in 0 until nsyms) {
                        val type = bytes[symoff + i * 16 + 4].toInt() and 0xff
                        val stab = type and 0xe0
                        val kind = type and 0x0e
                        val ext = type and 0x01
                        if (stab == 0 && kind == 0 && ext == 1) undefined++
                    }
                }
            }
            at += size
        }
        return MachObject(name, platform, minOs, hasCode, undefined)
    }

    /** `xxxx.yy.zz` nibbles; the patch is dropped when zero (`15.0`, `18.5`, `15.0.1`). */
    fun version(v: Int): String {
        val major = (v ushr 16) and 0xffff
        val minor = (v ushr 8) and 0xff
        val patch = v and 0xff
        return if (patch == 0) "$major.$minor" else "$major.$minor.$patch"
    }
}
