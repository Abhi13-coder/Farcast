package com.farcast.agent.darkalgorithm.relab

/**
 * Matches jni_bridge.cpp's current stub signatures exactly. Calling any of
 * these today returns the honest "not yet linked" string from the native
 * side — see src/main/cpp/RE_EMU_LAB.md for what has to happen before this
 * does real disassembly/assembly/emulation. Not loaded (System.loadLibrary)
 * until the externalNativeBuild block in build.gradle.kts is active, so
 * referencing this object before then will throw UnsatisfiedLinkError —
 * intentionally, rather than silently no-op.
 */
object ReLabBridge {
    init {
        System.loadLibrary("farcast_re_bridge")
    }

    external fun disassemble(code: ByteArray, arch: String): String
    external fun assemble(asm: String, arch: String): String
    external fun emulate(code: ByteArray, arch: String): String
}
