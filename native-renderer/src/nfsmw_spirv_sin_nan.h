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

// xdroid360_posicion_invariante. Marks the vertex shader's Position output as Invariant, in place. State of Decay
// draws characters twice (depth first, then color with depth func EQUAL) with different vertex shaders that both
// skin the vertex. Mali's compiler optimizes each shader its own way, the two depths differ in the last bits and
// the EQUAL test fails in flickering patches that show the background. Invariant makes the driver compute the
// position the same way in every shader. Returns 1 if it added the decoration (0: not a vertex shader, already
// invariant, or SPIR-V it does not understand).
uint32_t PosicionInvariante(std::vector<uint32_t>& spirv);

// xdroid360_posicion_invariante, second half. Marks every float arithmetic result of the vertex shader `spirv`
// (add, subtract, multiply, divide, negate, the vector and matrix products, dot) NoContraction, in place: the driver
// may not fuse a multiply and an add into one fused multiply-add. Invariant alone was not enough on Mali: its
// compiler fuses differently in the depth pass's shader and in the color pass's, and one ulp of difference fails
// depth func EQUAL. Xenia's own translator marks all of its arithmetic the same way. Returns how many results were
// marked (0: not a vertex shader, nothing to mark, or SPIR-V it does not understand).
uint32_t SinContraccion(std::vector<uint32_t>& spirv);

}  // namespace nfsmw::nativo
