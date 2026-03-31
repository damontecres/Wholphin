#define UTIL_EXTERN
#include "jni_utils.h"

#include <jni.h>
#include <stdlib.h>

bool acquire_jni_env(JavaVM *vm, JNIEnv **env)
{
    int ret = vm->GetEnv((void**) env, JNI_VERSION_1_6);
    if (ret == JNI_EDETACHED)
        return vm->AttachCurrentThread(env, NULL) == 0;
    else
        return ret == JNI_OK;
}

// Apparently it's considered slow to FindClass and GetMethodID every time we need them,
// so let's have a nice cache here.

static bool clear_pending_exception(JNIEnv *env)
{
    if (!env->ExceptionCheck())
        return false;

    env->ExceptionDescribe();
    env->ExceptionClear();
    return true;
}

void init_methods_cache(JNIEnv *env)
{
    static bool methods_initialized = false;
    if (methods_initialized)
        return;

    #define FIND_CLASS(dst, name) \
        do { \
            jclass localClass = env->FindClass(name); \
            if (localClass == nullptr || clear_pending_exception(env)) \
                return; \
            dst = reinterpret_cast<jclass>(env->NewGlobalRef(localClass)); \
            env->DeleteLocalRef(localClass); \
            if (dst == nullptr || clear_pending_exception(env)) \
                return; \
        } while (0)
    #define GET_METHOD_ID(dst, clazz, name, signature) \
        do { \
            dst = env->GetMethodID(clazz, name, signature); \
            if (dst == nullptr || clear_pending_exception(env)) \
                return; \
        } while (0)
    #define GET_STATIC_METHOD_ID(dst, clazz, name, signature) \
        do { \
            dst = env->GetStaticMethodID(clazz, name, signature); \
            if (dst == nullptr || clear_pending_exception(env)) \
                return; \
        } while (0)
    #define GET_STATIC_FIELD_ID(dst, clazz, name, signature) \
        do { \
            dst = env->GetStaticFieldID(clazz, name, signature); \
            if (dst == nullptr || clear_pending_exception(env)) \
                return; \
        } while (0)

    FIND_CLASS(java_Integer, "java/lang/Integer");
    GET_METHOD_ID(java_Integer_init, java_Integer, "<init>", "(I)V");
    FIND_CLASS(java_Double, "java/lang/Double");
    GET_METHOD_ID(java_Double_init, java_Double, "<init>", "(D)V");
    FIND_CLASS(java_Boolean, "java/lang/Boolean");
    GET_METHOD_ID(java_Boolean_init, java_Boolean, "<init>", "(Z)V");

    FIND_CLASS(android_graphics_Bitmap, "android/graphics/Bitmap");
    // createBitmap(int[], int, int, android.graphics.Bitmap$Config)
    GET_STATIC_METHOD_ID(android_graphics_Bitmap_createBitmap, android_graphics_Bitmap, "createBitmap", "([IIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    FIND_CLASS(android_graphics_Bitmap_Config, "android/graphics/Bitmap$Config");
    // static final android.graphics.Bitmap$Config ARGB_8888
    GET_STATIC_FIELD_ID(android_graphics_Bitmap_Config_ARGB_8888, android_graphics_Bitmap_Config, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");

    FIND_CLASS(mpv_MPVLib, "com/github/damontecres/wholphin/util/mpv/MPVLib");
    GET_STATIC_METHOD_ID(mpv_MPVLib_eventProperty_S, mpv_MPVLib, "eventProperty", "(Ljava/lang/String;)V"); // eventProperty(String)
    GET_STATIC_METHOD_ID(mpv_MPVLib_eventProperty_Sb, mpv_MPVLib, "eventProperty", "(Ljava/lang/String;Z)V"); // eventProperty(String, boolean)
    GET_STATIC_METHOD_ID(mpv_MPVLib_eventProperty_Sl, mpv_MPVLib, "eventProperty", "(Ljava/lang/String;J)V"); // eventProperty(String, long)
    GET_STATIC_METHOD_ID(mpv_MPVLib_eventProperty_Sd, mpv_MPVLib, "eventProperty", "(Ljava/lang/String;D)V"); // eventProperty(String, double)
    GET_STATIC_METHOD_ID(mpv_MPVLib_eventProperty_SS, mpv_MPVLib, "eventProperty", "(Ljava/lang/String;Ljava/lang/String;)V"); // eventProperty(String, String)
    GET_STATIC_METHOD_ID(mpv_MPVLib_event, mpv_MPVLib, "event", "(I)V"); // event(int)
    GET_STATIC_METHOD_ID(mpv_MPVLib_end_file_event, mpv_MPVLib, "eventEndFile", "(II)V"); // eventEndFile(int, int)
    GET_STATIC_METHOD_ID(mpv_MPVLib_logMessage_SiS, mpv_MPVLib, "logMessage", "(Ljava/lang/String;ILjava/lang/String;)V"); // logMessage(String, int, String)

    #undef FIND_CLASS
    #undef GET_METHOD_ID
    #undef GET_STATIC_METHOD_ID
    #undef GET_STATIC_FIELD_ID

    methods_initialized = true;
}
