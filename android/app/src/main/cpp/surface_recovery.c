// XDroid360 launcher-only workaround for a lost Android Vulkan presentation
// surface after Home / Recents. The game library already listens for SDL3's
// WILL_ENTER_BACKGROUND and DID_ENTER_FOREGROUND lifecycle events and uses
// them to disconnect/reconnect Xenia's Vulkan presenter. Sometimes those
// callbacks arrive before SDL has a replacement ANativeWindow.
//
// When the Java SurfaceView is valid again, repeat the pair to force a new
// presenter connection. This lives outside libgame.so so existing game builds
// do not need a rebuild. No rendering or guest state is reset here.
//
// The events go straight into SDL's event queue (SDL_PushEvent). SDL's own
// nativePause/nativeResume don't do: SDL's lifecycle queue drops a pause and a
// resume that meet there, and the picture stayed black (2026-11-19).
//
// Linked against libSDL3.so like any SDL program (it's loaded before this
// library, so the dependency resolves to the same copy). It used to find SDL
// at run time with dlopen/dlsym; Google's scanner flagged APKs carrying that
// as a "dropper".
#include <jni.h>
#include <android/log.h>
#include <stdbool.h>
#include <string.h>

#include <SDL3/SDL.h>

#define LOG_TAG "XDroid360Surface"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

JNIEXPORT jboolean JNICALL
Java_com_udarmolota_xdroid360_GameActivity_nativeRequestSurfaceRecovery(
        JNIEnv* env, jclass clazz) {
    (void)env;
    (void)clazz;

    if (!(SDL_WasInit(SDL_INIT_VIDEO) & SDL_INIT_VIDEO)) {
        return JNI_FALSE;
    }

    SDL_Event event;
    memset(&event, 0, sizeof(event));
    event.type = SDL_EVENT_WILL_ENTER_BACKGROUND;
    bool lost_sent = SDL_PushEvent(&event);
    memset(&event, 0, sizeof(event));
    event.type = SDL_EVENT_DID_ENTER_FOREGROUND;
    bool ready_sent = SDL_PushEvent(&event);
    if (lost_sent && ready_sent) {
        LOGI("queued Vulkan presenter disconnect/reconnect on SDL event loop");
        return JNI_TRUE;
    }
    LOGW("SDL rejected surface recovery events (lost=%d ready=%d)", lost_sent, ready_sent);
    return JNI_FALSE;
}
