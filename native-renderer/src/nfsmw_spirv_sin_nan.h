#pragma once
// nfsmw - native renderer: NaN to zero in what the pixel shaders write (xdroid360_color_sin_nan).
//
// Adreno writes a NaN color as 0 when it stores it to a render target; Mali keeps it, and in State of Decay's
// lighting buffers it then spreads as flickering patches on characters (pink by day, black at night). This makes
// every store of a 32-bit float scalar or vector to a fragment output go through
//   select(isnan(value), 0, value)
// so the target gets 0 where Adreno would have it too. Nothing else in the shader changes.

#include <cstdint>
#include <vector>

namespace nfsmw::nativo {

// Rewrites the fragment shader `spirv` in place. Returns how many stores were wrapped (0: unchanged, also for
// a vertex shader or SPIR-V it does not understand).
uint32_t ColorSinNan(std::vector<uint32_t>& spirv);

}  // namespace nfsmw::nativo
