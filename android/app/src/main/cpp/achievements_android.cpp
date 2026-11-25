// Achievements (Dragon Age: Origins): the game reports an unlocked achievement to the console's
// gamer services (XUserWriteAchievements, through XMsgStartIORequest: kernel/xam.cpp calls the
// callback installed below, on the game's thread). The phone passes the id on to the launcher's
// side (GameActivity.onAchievementUnlocked), which records it for the Achievements list in the
// game's settings and shows the "Achievement unlocked" banner. The list itself, with the points
// and the texts, is the game's own (read from its default.xex by XdbfAchievements.java).
#include "stdafx.h"

#include <jni.h>

#include <SDL3/SDL_system.h>

#include "kernel/achievements.h"

#include <chrono>
#include <cstdlib>
#include <sstream>
#include <string>
#include <thread>

namespace
{
    // One achievement unlocked, by its id in the game's list.
    void OnUnlocked(uint32_t id)
    {
        auto* env = static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());
        auto activity = static_cast<jobject>(SDL_GetAndroidActivity());
        if (!env || !activity)
        {
            LOGW("Achievement {}: no activity", id);
            return;
        }
        jclass cls = env->GetObjectClass(activity);
        if (jmethodID method = env->GetMethodID(cls, "onAchievementUnlocked", "(I)V"))
            env->CallVoidMethod(activity, method, jint(id));
        else
        {
            env->ExceptionClear();
            LOGW("Achievement {}: GameActivity.onAchievementUnlocked missing", id);
        }
        env->DeleteLocalRef(cls);
        env->DeleteLocalRef(activity);
    }

    // Test without playing: userdata/env.txt line DAO_TEST_ACHIEVEMENT=<id>[,<id>...] unlocks
    // those achievements through the same path the game's own call takes (callback, JNI, the list
    // and the banner), 45 seconds after the library loads, 6 seconds apart. It does not test the
    // game's XGI message itself; the log's "XGI message" lines show that one. Remove the line
    // and delete userdata/achievements.json afterwards: the test unlocks are kept like real ones.
    void RunTestUnlocks()
    {
        std::this_thread::sleep_for(std::chrono::seconds(45));
        const char* value = std::getenv("DAO_TEST_ACHIEVEMENT");
        if (!value || !*value)
            return;
        LOGI("DAO_TEST_ACHIEVEMENT={}", value);
        std::stringstream ids(value);
        std::string id;
        while (std::getline(ids, id, ','))
        {
            if (const long n = std::strtol(id.c_str(), nullptr, 10); n > 0)
                OnUnlocked(uint32_t(n));
            std::this_thread::sleep_for(std::chrono::seconds(6));
        }
    }

    // Installed when the library loads (the callback pointer is constant-initialised).
    const bool s_installed = (dao::kernel::g_achievementCallback = &OnUnlocked,
                              std::thread(RunTestUnlocks).detach(), true);
}
