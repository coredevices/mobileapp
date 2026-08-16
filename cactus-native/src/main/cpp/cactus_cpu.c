#include <jni.h>
#include <sys/auxv.h>
#include <asm/hwcap.h>

JNIEXPORT jboolean JNICALL
Java_com_cactus_CactusCpuJNI_nativeIsCactusSupported(JNIEnv *env, jclass clazz) {
    return (getauxval(AT_HWCAP) & HWCAP_ASIMDHP) != 0 ? JNI_TRUE : JNI_FALSE;
}
