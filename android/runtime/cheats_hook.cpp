// Cheats on Android: the game's mode controller update calls UpdateCheats (ui/cheats.cpp),
// which opens and closes the cheats menu and runs its scripts on the game's thread, between
// frames. On Windows this call sits in photo_mode.cpp's hook of the same function; photo
// mode is not built here, so this is its minimal counterpart. If photo mode is ported later,
// delete this file and keep photo_mode.cpp's hook (one hook per function).
#include "stdafx.h"
#include "ui/cheats.h"
#include "kernel/function.h"
#include "kernel/game_build.h"
#include "kernel/game_modes.h"

namespace
{
    using namespace dao::kernel;

    // The game (a pointer), as photo_mode.cpp has it.
    constexpr GameAddress kGame{ 0x83211BB0, 0x832E20E8 };

    int32_t CurrentMode(uint8_t* base, uint32_t controller)
    {
        const uint32_t current = PPC_LOAD_U32(controller + kCurrentModeOffset);
        return current ? int32_t(PPC_LOAD_U32(current + kModeNumberOffset)) : -1;
    }

    // The mode controller's update: r3 is the controller.
    void CheatsModeUpdateHook(PPCContext& ctx, uint8_t* base, PPCFunc* original)
    {
        const uint32_t controller = ctx.r3.u32;
        const uint32_t game = PPC_LOAD_U32(kGame.Get());
        dao::ui::UpdateCheats(base, CurrentMode(base, controller), game);
        original(ctx, base);
    }
}

GAME_HOOK(sub_82317138, tu6_sub_82348590, CheatsModeUpdateHook)
