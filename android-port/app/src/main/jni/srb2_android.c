// SRB2 Android Port - Compatibility Shims
// This file provides Android-specific workarounds and overrides.
// It is compiled ONLY for Android builds.

#ifdef ANDROID

#include <android/log.h>
#include <string.h>
#include <stdlib.h>
#include <jni.h>

#define LOG_TAG "SRB2"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Android internal storage path, set from Java side before game init
static char android_internal_path[512] = "";
static char android_external_path[512] = "";

void Android_SetInternalPath(const char *path)
{
    if (path)
        strncpy(android_internal_path, path, sizeof(android_internal_path) - 1);
}

void Android_SetExternalPath(const char *path)
{
    if (path)
        strncpy(android_external_path, path, sizeof(android_external_path) - 1);
}

const char *Android_GetInternalPath(void)
{
    return android_internal_path;
}

const char *Android_GetExternalPath(void)
{
    return android_external_path;
}

// JNI functions callable from Java
JNIEXPORT void JNICALL Java_org_srb2_android_SRB2Activity_nativeSetPaths(
    JNIEnv *env, jclass cls, jstring internalPath, jstring externalPath)
{
    const char *internal = (*env)->GetStringUTFChars(env, internalPath, NULL);
    const char *external = (*env)->GetStringUTFChars(env, externalPath, NULL);

    if (internal)
    {
        Android_SetInternalPath(internal);
        (*env)->ReleaseStringUTFChars(env, internalPath, internal);
    }
    if (external)
    {
        Android_SetExternalPath(external);
        (*env)->ReleaseStringUTFChars(env, externalPath, external);
    }

    LOGI("Internal path: %s", android_internal_path);
    LOGI("External path: %s", android_external_path);
}

#endif // ANDROID
