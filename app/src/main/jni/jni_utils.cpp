#define UTIL_EXTERN
#include "jni_utils.h"

#include <jni.h>
#include <stdlib.h>
#include <mutex>

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

void init_methods_cache(JNIEnv *env)
{
    static std::once_flag methods_init_flag;
    static bool methods_initialized = false;

    std::call_once(methods_init_flag, [env, &methods_initialized]() {
        #define FIND_CLASS(name) reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass(name)))
        java_Integer = FIND_CLASS("java/lang/Integer");
        java_Integer_init = env->GetMethodID(java_Integer, "<init>", "(I)V");
        java_Double = FIND_CLASS("java/lang/Double");
        java_Double_init = env->GetMethodID(java_Double, "<init>", "(D)V");
        java_Boolean = FIND_CLASS("java/lang/Boolean");
        java_Boolean_init = env->GetMethodID(java_Boolean, "<init>", "(Z)V");

        android_graphics_Bitmap = FIND_CLASS("android/graphics/Bitmap");
        // createBitmap(int[], int, int, android.graphics.Bitmap$Config)
        android_graphics_Bitmap_createBitmap = env->GetStaticMethodID(android_graphics_Bitmap, "createBitmap", "([IIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
        android_graphics_Bitmap_Config = FIND_CLASS("android/graphics/Bitmap$Config");
        // static final android.graphics.Bitmap$Config ARGB_8888
        android_graphics_Bitmap_Config_ARGB_8888 = env->GetStaticFieldID(android_graphics_Bitmap_Config, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");

        mpv_MPVLib = FIND_CLASS("com/github/damontecres/wholphin/util/mpv/MPVLib");
        mpv_MPVLib_eventProperty_S  = env->GetStaticMethodID(mpv_MPVLib, "eventProperty", "(Ljava/lang/String;)V"); // eventProperty(String)
        mpv_MPVLib_eventProperty_Sb = env->GetStaticMethodID(mpv_MPVLib, "eventProperty", "(Ljava/lang/String;Z)V"); // eventProperty(String, boolean)
        mpv_MPVLib_eventProperty_Sl = env->GetStaticMethodID(mpv_MPVLib, "eventProperty", "(Ljava/lang/String;J)V"); // eventProperty(String, long)
        mpv_MPVLib_eventProperty_Sd = env->GetStaticMethodID(mpv_MPVLib, "eventProperty", "(Ljava/lang/String;D)V"); // eventProperty(String, double)
        mpv_MPVLib_eventProperty_SS = env->GetStaticMethodID(mpv_MPVLib, "eventProperty", "(Ljava/lang/String;Ljava/lang/String;)V"); // eventProperty(String, String)
        mpv_MPVLib_event = env->GetStaticMethodID(mpv_MPVLib, "event", "(I)V"); // event(int)
        mpv_MPVLib_end_file_event = env->GetStaticMethodID(mpv_MPVLib, "eventEndFile", "(II)V"); // eventEndFile(int, int)
        mpv_MPVLib_logMessage_SiS = env->GetStaticMethodID(mpv_MPVLib, "logMessage", "(Ljava/lang/String;ILjava/lang/String;)V"); // logMessage(String, int, String)
        #undef FIND_CLASS

        methods_initialized =
            java_Integer != NULL &&
            java_Integer_init != NULL &&
            java_Double != NULL &&
            java_Double_init != NULL &&
            java_Boolean != NULL &&
            java_Boolean_init != NULL &&
            android_graphics_Bitmap != NULL &&
            android_graphics_Bitmap_createBitmap != NULL &&
            android_graphics_Bitmap_Config != NULL &&
            android_graphics_Bitmap_Config_ARGB_8888 != NULL &&
            mpv_MPVLib != NULL &&
            mpv_MPVLib_eventProperty_S != NULL &&
            mpv_MPVLib_eventProperty_Sb != NULL &&
            mpv_MPVLib_eventProperty_Sl != NULL &&
            mpv_MPVLib_eventProperty_Sd != NULL &&
            mpv_MPVLib_eventProperty_SS != NULL &&
            mpv_MPVLib_event != NULL &&
            mpv_MPVLib_end_file_event != NULL &&
            mpv_MPVLib_logMessage_SiS != NULL;
    });

    if (!methods_initialized)
        return;
}
