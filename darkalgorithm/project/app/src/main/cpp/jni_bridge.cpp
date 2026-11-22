#include <jni.h>
#include <string>

// Deliberately stubbed, not faked: each function below is where the real
// capstone_disasm()/ks_asm()/uc_emu_start() calls go, once the submodules
// in CMakeLists.txt are checked out and build successfully for this
// project's target ABIs. Returning a placeholder "not implemented" string
// is the honest state here — filling these with logic that LOOKS like a
// working disassembler without the actual libraries linked and verified
// would be a worse outcome than leaving them stubbed.

extern "C" JNIEXPORT jstring JNICALL
Java_com_farcast_agent_darkalgorithm_relab_ReLabBridge_disassemble(
        JNIEnv* env, jobject /* this */, jbyteArray /* code */, jstring /* arch */) {
    return env->NewStringUTF("RE_EMU_LAB: capstone not yet linked — see RE_EMU_LAB.md");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_farcast_agent_darkalgorithm_relab_ReLabBridge_assemble(
        JNIEnv* env, jobject /* this */, jstring /* asm */, jstring /* arch */) {
    return env->NewStringUTF("RE_EMU_LAB: keystone not yet linked — see RE_EMU_LAB.md");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_farcast_agent_darkalgorithm_relab_ReLabBridge_emulate(
        JNIEnv* env, jobject /* this */, jbyteArray /* code */, jstring /* arch */) {
    return env->NewStringUTF("RE_EMU_LAB: unicorn not yet linked — see RE_EMU_LAB.md");
}
