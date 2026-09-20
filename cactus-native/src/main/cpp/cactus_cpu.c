#include <jni.h>
#include <sys/auxv.h>
#include <asm/hwcap.h>

JNIEXPORT jboolean JNICALL
Java_com_cactus_CactusCpuJNI_nativeIsCactusSupported(JNIEnv *env, jclass clazz) {
    const unsigned long hwcap = getauxval(AT_HWCAP);
    const unsigned long required = HWCAP_ASIMDHP | HWCAP_ASIMDDP;
    return (hwcap & required) == required ? JNI_TRUE : JNI_FALSE;
}
