// The cheats menu (ui/cheats.h) as an Android dialog: cheats.cpp opens it (ShowOverlay)
// over the paused game and runs the scripts asked for; the player taps a cheat in
// GameActivity.showCheats, which calls nativeCheatsUse here. On Windows DAORecomp draws
// this menu itself with ImGui over the game window (ui/cheats_overlay.cpp); the rows and
// scripts below are that file's, on a phone's touch screen.
#include "stdafx.h"

#include <jni.h>

#include <SDL3/SDL_system.h>

#include "ui/cheats.h"

namespace
{
    using dao::ui::cheats::Scope;

    // The rows, as cheats_overlay.cpp's Row (GameActivity.showCheats uses the same numbers).
    enum Row
    {
        kImmortal = 0,
        kSkillChecks,
        kKillEnemies,
        kGold,
        kExperience,
        kStrengthen,
    };

    // The amounts the buttons of the Gold and Experience rows add.
    constexpr int kGoldAmounts[] = { 1, 10, 100, 1000 };
    constexpr int kExperienceAmounts[] = { 1000, 5000, 20000, 100000 };
    // Coppers in a gold coin (zz_money's value is in coppers).
    constexpr int kCoppersPerGold = 10000;

    std::string Grouped(int value)
    {
        std::string digits = std::to_string(value);
        for (int at = int(digits.size()) - 3; at > 0; at -= 3)
            digits.insert(size_t(at), ",");
        return digits;
    }

    // Calls a method of GameActivity with no arguments (the game's thread; the UI thread
    // does the dialog's work).
    void CallActivity(const char* method)
    {
        auto* env = static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());
        auto activity = static_cast<jobject>(SDL_GetAndroidActivity());
        if (!env || !activity)
        {
            LOGW("Cheats: no activity for {}", method);
            return;
        }
        jclass cls = env->GetObjectClass(activity);
        if (jmethodID id = env->GetMethodID(cls, method, "()V"))
            env->CallVoidMethod(activity, id);
        else
        {
            env->ExceptionClear();
            LOGW("Cheats: GameActivity.{} missing", method);
        }
        env->DeleteLocalRef(cls);
        env->DeleteLocalRef(activity);
    }
}

namespace dao::ui::cheats
{
    void ShowOverlay()
    {
        CallActivity("showCheats");
    }

    void CloseOverlay()
    {
        CallActivity("hideCheats");
    }
}

// The row the player tapped (`amount`: which of the Gold and Experience buttons). Returns the
// message to show, or null if nothing was asked for.
extern "C" JNIEXPORT jstring JNICALL Java_com_udarmolota_xdroid360_GameActivity_nativeCheatsUse(
    JNIEnv* env, jclass, jint row, jint amount)
{
    using namespace dao::ui::cheats;
    const State state = GetState();
    std::string toast;
    switch (row)
    {
    case kImmortal:
        // (The script switches its creature's: it runs for those that aren't yet, or back
        // for those it made immortal.)
        Run("pc_immortal", {}, state.immortal ? Scope::MadeImmortal : Scope::EachMortal);
        toast = state.immortal ? "The party can die again" : "The party can't die";
        break;
    case kSkillChecks:
        Run("zz_override_skills", state.skillChecks ? "0" : "1");
        toast = state.skillChecks ? "Skill checks as the game makes them" : "Every skill check succeeds";
        break;
    case kKillEnemies:
        Run("killallhostiles");
        toast = "Enemies in sight killed";
        break;
    case kGold:
        if (amount < 0 || amount >= int(std::size(kGoldAmounts)))
            return nullptr;
        Run("zz_money", std::to_string(kGoldAmounts[amount] * kCoppersPerGold));
        toast = "Added " + Grouped(kGoldAmounts[amount]) + " gold";
        break;
    case kExperience:
        if (amount < 0 || amount >= int(std::size(kExperienceAmounts)))
            return nullptr;
        Run("addxp", std::to_string(kExperienceAmounts[amount]));
        toast = "Added " + Grouped(kExperienceAmounts[amount]) + " XP";
        break;
    case kStrengthen:
        // (The script finds the creature it strengthens by its tag.)
        Run("zz_supercrit", {}, Scope::EachTag);
        toast = "The party is stronger";
        break;
    default:
        return nullptr;
    }
    return env->NewStringUTF(toast.c_str());
}

// The game's state the dialog shows: bit 0 the party is immortal, bit 1 every skill check
// succeeds (as of the game's last frame with the menu open).
extern "C" JNIEXPORT jint JNICALL Java_com_udarmolota_xdroid360_GameActivity_nativeCheatsState(JNIEnv*, jclass)
{
    const dao::ui::cheats::State state = dao::ui::cheats::GetState();
    return (state.immortal ? 1 : 0) | (state.skillChecks ? 2 : 0);
}

// The player closed the dialog (Back, a tap outside it, Close): the menu closes with it.
extern "C" JNIEXPORT void JNICALL Java_com_udarmolota_xdroid360_GameActivity_nativeCheatsClosed(JNIEnv*, jclass)
{
    if (dao::ui::CheatsOpen())
        dao::ui::ToggleCheats();
}
