// nfsmw - native renderer: NaN to zero in what the pixel shaders write (see the header).

#include "nfsmw_spirv_sin_nan.h"

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

}  // namespace nfsmw::nativo
