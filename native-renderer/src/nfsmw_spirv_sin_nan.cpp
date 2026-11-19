// nfsmw - native renderer: NaN to zero in what the pixel shaders write (see the header).

#include "nfsmw_spirv_sin_nan.h"

#include <cstddef>
#include <map>

namespace nfsmw::nativo {
namespace {

// SPIR-V opcodes and enumerants used here (spirv.hpp).
constexpr uint32_t kOpEntryPoint = 15;
constexpr uint32_t kOpTypeBool = 20;
constexpr uint32_t kOpTypeFloat = 22;
constexpr uint32_t kOpTypeVector = 23;
constexpr uint32_t kOpTypePointer = 32;
constexpr uint32_t kOpConstantNull = 46;
constexpr uint32_t kOpFunction = 54;
constexpr uint32_t kOpVariable = 59;
constexpr uint32_t kOpStore = 62;
constexpr uint32_t kOpAccessChain = 65;
constexpr uint32_t kOpInBoundsAccessChain = 66;
constexpr uint32_t kOpIsNan = 156;
constexpr uint32_t kOpSelect = 169;
constexpr uint32_t kExecutionModelFragment = 4;
constexpr uint32_t kStorageClassOutput = 3;
constexpr uint32_t kMaxBound = 1u << 22;

uint32_t Palabra(uint32_t n, uint32_t op) { return (n << 16) | op; }

}  // namespace

uint32_t ColorSinNan(std::vector<uint32_t>& spirv) {
  if (spirv.size() < 5 || spirv[0] != 0x07230203u || spirv[3] == 0 || spirv[3] > kMaxBound) {
    return 0;
  }
  uint32_t limite = spirv[3];
  struct Instr {
    size_t pos;
    uint32_t op;
    uint32_t n;
  };
  std::vector<Instr> instrucciones;
  for (size_t pos = 5; pos < spirv.size();) {
    const uint32_t n = spirv[pos] >> 16;
    if (n == 0 || pos + n > spirv.size()) {
      return 0;
    }
    instrucciones.push_back({pos, spirv[pos] & 0xFFFF, n});
    pos += n;
  }
  bool fragmento = false;
  uint32_t tipo_bool = 0;
  size_t primera_funcion = 0;
  std::vector<uint8_t> flotante(limite, 0);       // 32-bit float scalar
  std::vector<uint32_t> componentes(limite, 0);   // float type -> 1 (scalar) or N (vector of 32-bit floats)
  std::map<uint32_t, uint32_t> bool_vector;       // component count -> existing bool vector type
  std::map<uint32_t, uint32_t> nulo;              // float type -> existing OpConstantNull of it
  std::vector<uint32_t> apunta_a(limite, 0);      // Output pointer type -> pointee type
  std::vector<uint32_t> salida(limite, 0);        // Output variable or access chain -> pointee type
  for (const Instr& in : instrucciones) {
    const uint32_t* w = spirv.data() + in.pos;
    switch (in.op) {
      case kOpEntryPoint:
        if (in.n >= 2 && w[1] == kExecutionModelFragment) fragmento = true;
        break;
      case kOpTypeBool:
        if (in.n >= 2) tipo_bool = w[1];
        break;
      case kOpTypeFloat:
        if (in.n >= 3 && w[1] < limite && w[2] == 32) {
          flotante[w[1]] = 1;
          componentes[w[1]] = 1;
        }
        break;
      case kOpTypeVector:
        if (in.n >= 4 && w[1] < limite && w[2] < limite) {
          if (flotante[w[2]]) {
            componentes[w[1]] = w[3];
          } else if (w[2] == tipo_bool && tipo_bool) {
            bool_vector.emplace(w[3], w[1]);
          }
        }
        break;
      case kOpTypePointer:
        if (in.n >= 4 && w[1] < limite && w[2] == kStorageClassOutput) apunta_a[w[1]] = w[3];
        break;
      case kOpConstantNull:
        if (in.n >= 3 && w[1] < limite && componentes[w[1]]) nulo.emplace(w[1], w[2]);
        break;
      case kOpVariable:
        if (in.n >= 4 && w[1] < limite && w[2] < limite && w[3] == kStorageClassOutput && apunta_a[w[1]]) {
          salida[w[2]] = apunta_a[w[1]];
        }
        break;
      case kOpAccessChain:
      case kOpInBoundsAccessChain:
        if (in.n >= 4 && w[1] < limite && w[2] < limite && w[3] < limite && salida[w[3]] && apunta_a[w[1]]) {
          salida[w[2]] = apunta_a[w[1]];
        }
        break;
      case kOpFunction:
        if (!primera_funcion) primera_funcion = in.pos;
        break;
      default:
        break;
    }
  }
  if (!fragmento || !primera_funcion) {
    return 0;
  }
  // The stores to wrap: a float scalar or vector (of 32-bit floats) to an output.
  std::vector<size_t> almacenes;
  for (size_t i = 0; i < instrucciones.size(); ++i) {
    const Instr& in = instrucciones[i];
    const uint32_t* w = spirv.data() + in.pos;
    if (in.op == kOpStore && in.n >= 3 && w[1] < limite) {
      const uint32_t tipo = salida[w[1]];
      if (tipo && tipo < limite && componentes[tipo]) {
        almacenes.push_back(i);
      }
    }
  }
  if (almacenes.empty()) {
    return 0;
  }
  // Declarations that are missing, before the first function (types and constants may go there). SPIR-V does not
  // allow a second OpTypeBool or a second identical bool vector type, so the existing ones are reused.
  std::vector<uint32_t> declaraciones;
  if (!tipo_bool) {
    tipo_bool = limite++;
    declaraciones.insert(declaraciones.end(), {Palabra(2, kOpTypeBool), tipo_bool});
  }
  std::map<uint32_t, uint32_t> tipo_condicion;  // float type -> bool type of the same shape
  for (size_t i : almacenes) {
    const uint32_t* w = spirv.data() + instrucciones[i].pos;
    const uint32_t tipo = salida[w[1]];
    if (!tipo_condicion.count(tipo)) {
      const uint32_t n = componentes[tipo];
      uint32_t condicion = tipo_bool;
      if (n > 1) {
        auto it = bool_vector.find(n);
        if (it == bool_vector.end()) {
          const uint32_t nuevo = limite++;
          declaraciones.insert(declaraciones.end(), {Palabra(4, kOpTypeVector), nuevo, tipo_bool, n});
          it = bool_vector.emplace(n, nuevo).first;
        }
        condicion = it->second;
      }
      tipo_condicion[tipo] = condicion;
    }
    if (!nulo.count(tipo)) {
      const uint32_t nuevo = limite++;
      declaraciones.insert(declaraciones.end(), {Palabra(3, kOpConstantNull), tipo, nuevo});
      nulo[tipo] = nuevo;
    }
  }
  if (limite > kMaxBound) {
    return 0;
  }
  // Rebuild: the declarations before the first function, and each wrapped store preceded by its isnan + select.
  std::vector<uint32_t> nuevo;
  nuevo.reserve(spirv.size() + declaraciones.size() + almacenes.size() * 9);
  nuevo.insert(nuevo.end(), spirv.begin(), spirv.begin() + 5);
  size_t siguiente = 0;
  for (size_t i = 0; i < instrucciones.size(); ++i) {
    const Instr& in = instrucciones[i];
    if (in.pos == primera_funcion) {
      nuevo.insert(nuevo.end(), declaraciones.begin(), declaraciones.end());
    }
    const uint32_t* w = spirv.data() + in.pos;
    if (siguiente < almacenes.size() && almacenes[siguiente] == i) {
      ++siguiente;
      const uint32_t tipo = salida[w[1]];
      const uint32_t es_nan = limite++;
      const uint32_t limpio = limite++;
      nuevo.insert(nuevo.end(), {Palabra(4, kOpIsNan), tipo_condicion[tipo], es_nan, w[2]});
      nuevo.insert(nuevo.end(), {Palabra(6, kOpSelect), tipo, limpio, es_nan, nulo[tipo], w[2]});
      const size_t inicio = nuevo.size();
      nuevo.insert(nuevo.end(), w, w + in.n);
      nuevo[inicio + 2] = limpio;
      continue;
    }
    nuevo.insert(nuevo.end(), w, w + in.n);
  }
  if (limite > kMaxBound) {
    return 0;
  }
  nuevo[3] = limite;
  spirv.swap(nuevo);
  return uint32_t(almacenes.size());
}

uint32_t PosicionInvariante(std::vector<uint32_t>& spirv) {
  constexpr uint32_t kOpDecorate = 71, kOpMemberDecorate = 72, kOpTypeVoid = 19, kOpTypeForwardPointer = 39;
  constexpr uint32_t kDecorationBuiltIn = 11, kDecorationInvariant = 18, kBuiltInPosition = 0;
  constexpr uint32_t kExecutionModelVertex = 0;
  if (spirv.size() < 5 || spirv[0] != 0x07230203u || spirv[3] == 0 || spirv[3] > kMaxBound) {
    return 0;
  }
  bool vertices = false, ya = false;
  size_t donde_insertar = 0;
  std::vector<uint32_t> decoraciones;
  for (size_t pos = 5; pos < spirv.size();) {
    const uint32_t n = spirv[pos] >> 16, op = spirv[pos] & 0xFFFF;
    if (n == 0 || pos + n > spirv.size()) {
      return 0;
    }
    const uint32_t* w = spirv.data() + pos;
    if (op == kOpEntryPoint && n >= 2 && w[1] == kExecutionModelVertex) {
      vertices = true;
    } else if (op == kOpDecorate && n >= 3) {
      if (w[2] == kDecorationInvariant) {
        ya = true;
      } else if (w[2] == kDecorationBuiltIn && n >= 4 && w[3] == kBuiltInPosition) {
        decoraciones.insert(decoraciones.end(), {Palabra(3, kOpDecorate), w[1], kDecorationInvariant});
      }
    } else if (op == kOpMemberDecorate && n >= 4) {
      if (w[3] == kDecorationInvariant) {
        ya = true;
      } else if (w[3] == kDecorationBuiltIn && n >= 5 && w[4] == kBuiltInPosition) {
        decoraciones.insert(decoraciones.end(), {Palabra(4, kOpMemberDecorate), w[1], w[2], kDecorationInvariant});
      }
    } else if (!donde_insertar && op >= kOpTypeVoid && op <= kOpTypeForwardPointer) {
      donde_insertar = pos;  // the first type: the annotations end right before it
    }
    pos += n;
  }
  if (!vertices || ya || decoraciones.empty() || !donde_insertar) {
    return 0;
  }
  spirv.insert(spirv.begin() + ptrdiff_t(donde_insertar), decoraciones.begin(), decoraciones.end());
  return 1;
}

uint32_t SinContraccion(std::vector<uint32_t>& spirv) {
  constexpr uint32_t kOpDecorate = 71, kOpTypeVoid = 19, kOpTypeForwardPointer = 39;
  constexpr uint32_t kOpFNegate = 127, kOpFAdd = 129, kOpFSub = 131, kOpFMul = 133, kOpFDiv = 136, kOpFRem = 140;
  constexpr uint32_t kOpDot = 148;  // 141-148: FMod, the vector and matrix products, OuterProduct, Dot
  constexpr uint32_t kDecorationNoContraction = 42;
  constexpr uint32_t kExecutionModelVertex = 0;
  if (spirv.size() < 5 || spirv[0] != 0x07230203u || spirv[3] == 0 || spirv[3] > kMaxBound) {
    return 0;
  }
  const uint32_t limite = spirv[3];
  bool vertices = false;
  size_t donde_insertar = 0;
  std::vector<uint8_t> ya(limite, 0);
  std::vector<uint32_t> decoraciones;
  for (size_t pos = 5; pos < spirv.size();) {
    const uint32_t n = spirv[pos] >> 16, op = spirv[pos] & 0xFFFF;
    if (n == 0 || pos + n > spirv.size()) {
      return 0;
    }
    const uint32_t* w = spirv.data() + pos;
    if (op == kOpEntryPoint && n >= 2 && w[1] == kExecutionModelVertex) {
      vertices = true;
    } else if (op == kOpDecorate && n >= 3 && w[2] == kDecorationNoContraction && w[1] < limite) {
      ya[w[1]] = 1;
    } else if (!donde_insertar && op >= kOpTypeVoid && op <= kOpTypeForwardPointer) {
      donde_insertar = pos;  // the first type: the annotations end right before it
    } else if (n >= 4 && w[2] < limite && !ya[w[2]] &&
               (op == kOpFNegate || op == kOpFAdd || op == kOpFSub || op == kOpFMul || op == kOpFDiv ||
                (op >= kOpFRem && op <= kOpDot))) {
      ya[w[2]] = 1;  // result type, result id, operands
      decoraciones.insert(decoraciones.end(), {Palabra(3, kOpDecorate), w[2], kDecorationNoContraction});
    }
    pos += n;
  }
  if (!vertices || decoraciones.empty() || !donde_insertar) {
    return 0;
  }
  const uint32_t marcados = uint32_t(decoraciones.size() / 3);
  spirv.insert(spirv.begin() + ptrdiff_t(donde_insertar), decoraciones.begin(), decoraciones.end());
  return marcados;
}

}  // namespace nfsmw::nativo
