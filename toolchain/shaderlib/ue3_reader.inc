// Mass Effect: Unreal Engine 3 packages (*.xxx). Compressed chunks start with the tag 0x9E2A83C1
// twice, then the chunk's compressed and uncompressed sizes, then (compressed, uncompressed) per
// 128 KB block, then the blocks in LZO1X. Containers are taken from the decompressed chunks and the
// raw file, and kept only when their layout is one the game's loader accepts (random package bytes
// match the signature too; XenosRecomp trusts every offset). Port of SD1/me1/scripts/ue3_shaders.py.
// Included by xdroid360_shaderlib.cpp inside its anonymous namespace (Container, Be32, ReadFile).

// LZO1X decompression; false on corrupt input.
bool Lzo1x(const uint8_t* src, size_t n, std::vector<uint8_t>& out, size_t expected) {
  out.clear();
  out.reserve(expected);
  size_t ip = 0;
  int state = 0;
  auto literals = [&](size_t t) {
    if (ip + t > n || out.size() + t > expected) return false;
    out.insert(out.end(), src + ip, src + ip + t);
    ip += t;
    return true;
  };
  auto match = [&](size_t dist, size_t length) {
    if (dist == 0 || dist > out.size() || out.size() + length > expected) return false;
    const size_t start = out.size() - dist;
    for (size_t i = 0; i < length; ++i) out.push_back(out[start + i]);
    return true;
  };
  // Run-length extension: base + 255 per zero byte + the next byte.
  auto extend = [&](size_t& t, size_t base) {
    size_t z = 0;
    while (ip < n && src[ip] == 0) {
      ++ip;
      ++z;
    }
    if (ip >= n) return false;
    t = base + 255 * z + src[ip++];
    return true;
  };
  if (n == 0) return false;
  if (src[0] > 17) {
    const size_t t = src[0] - 17;
    ip = 1;
    if (!literals(t)) return false;
    state = t >= 4 ? 4 : int(t);
  }
  for (;;) {
    if (ip >= n) return false;
    size_t t = src[ip++];
    size_t dist = 0, length = 0, next = 0;
    if (t < 16) {
      if (state == 0) {
        if (t == 0 && !extend(t, 15)) return false;
        if (!literals(t + 3)) return false;
        state = 4;
        continue;
      }
      if (ip >= n) return false;
      next = t & 3;
      if (state != 4) {
        dist = 1 + (t >> 2) + (size_t(src[ip++]) << 2);
        length = 2;
      } else {
        dist = 1 + 0x800 + (t >> 2) + (size_t(src[ip++]) << 2);
        length = 3;
      }
    } else if (t >= 64) {
      if (ip >= n) return false;
      dist = 1 + ((t >> 2) & 7) + (size_t(src[ip++]) << 3);
      length = (t >> 5) + 1;
      next = t & 3;
    } else if (t >= 32) {
      length = t & 31;
      if (length == 0 && !extend(length, 31)) return false;
      length += 2;
      if (ip + 2 > n) return false;
      const size_t v = src[ip] | size_t(src[ip + 1]) << 8;
      ip += 2;
      dist = 1 + (v >> 2);
      next = v & 3;
    } else {  // 16..31
      const size_t hi = (t & 8) << 11;
      length = t & 7;
      if (length == 0 && !extend(length, 7)) return false;
      length += 2;
      if (ip + 2 > n) return false;
      const size_t v = src[ip] | size_t(src[ip + 1]) << 8;
      ip += 2;
      dist = hi + (v >> 2);
      next = v & 3;
      if (dist == 0) break;  // end of stream
      dist += 0x4000;
    }
    if (!match(dist, length)) return false;
    state = int(next);
    if (next && !literals(next)) return false;
  }
  return out.size() == expected;
}

uint32_t Be32At(const uint8_t* q) {
  return uint32_t(q[0]) << 24 | uint32_t(q[1]) << 16 | uint32_t(q[2]) << 8 | uint32_t(q[3]);
}

// The checks the game's loader makes before it uses a 2008 (0x102A11xx) container.
bool Ue3LayoutOk(const uint8_t* p, size_t size) {
  if (size < 28) return false;
  const bool vertex = p[3] & 1;
  const uint64_t vsize = Be32At(p + 4), header = Be32At(p + 24);
  if (header + 8 > size) return false;
  const uint64_t mstart = vsize + Be32At(p + header), msize = Be32At(p + header + 4);
  if (msize == 0 || msize % 4 || mstart + msize > size) return false;
  return header < vsize && header + (vertex ? 40 : 32) <= vsize;
}

void ScanUe3Buffer(const std::string& name, const std::vector<uint8_t>& b, std::set<std::vector<uint8_t>>& seen,
                   std::vector<Container>& out) {
  for (size_t i = 0; i + 28 <= b.size(); ++i) {
    if (b[i] != 0x10 || b[i + 1] != 0x2A || b[i + 2] != 0x11 || b[i + 3] > 1) continue;
    const uint32_t vs = Be32(b, i + 4), ps = Be32(b, i + 8);
    if (vs < 24 || vs > 0x40000 || ps == 0 || ps > 0x40000 || i + vs + ps > b.size()) continue;
    if (!Ue3LayoutOk(b.data() + i, vs + ps)) continue;
    std::vector<uint8_t> c(b.begin() + i, b.begin() + i + vs + ps);
    const bool vertex = c[3] & 1;
    if (seen.insert(c).second) {
      char suffix[32];
      std::snprintf(suffix, sizeof(suffix), "_%zx.%s", i, vertex ? "vs" : "ps");
      out.push_back({name + suffix, std::move(c), vertex});
    }
  }
}

// Every distinct container in the Unreal packages under `folder` (the game's folder).
std::vector<Container> ExtractFromUe3Folder(const std::string& folder) {
  std::vector<std::filesystem::path> packages;
  for (const auto& e : std::filesystem::recursive_directory_iterator(folder)) {
    if (!e.is_regular_file()) continue;
    std::string ext = e.path().extension().string();
    for (char& ch : ext) ch = char(std::tolower(uint8_t(ch)));
    if (ext == ".xxx") packages.push_back(e.path());
  }
  std::sort(packages.begin(), packages.end());
  if (packages.empty()) throw std::invalid_argument("no Unreal packages (*.xxx) in the folder");
  std::vector<Container> out;
  std::set<std::vector<uint8_t>> seen;
  size_t bad = 0;
  std::vector<uint8_t> block;
  for (const auto& path : packages) {
    const std::vector<uint8_t> d = ReadFile(path.string());
    const std::string name = path.stem().string();
    for (size_t off = 4; off + 16 <= d.size(); ++off) {
      if (Be32(d, off) != 0x9E2A83C1u || Be32(d, off + 4) != 0x9E2A83C1u) continue;
      const uint32_t csize = Be32(d, off + 8), usize = Be32(d, off + 12);
      if (usize == 0 || usize > 0x4000000 || csize == 0 || csize > d.size()) continue;
      const size_t blocks = (size_t(usize) + 0x1FFFF) / 0x20000, hdr = off + 16;
      size_t data = hdr + blocks * 8;
      if (data > d.size()) continue;
      uint64_t sum_c = 0, sum_u = 0;
      for (size_t k = 0; k < blocks; ++k) {
        sum_c += Be32(d, hdr + k * 8);
        sum_u += Be32(d, hdr + k * 8 + 4);
      }
      if (sum_c != csize || sum_u != usize || data + csize > d.size()) continue;
      std::vector<uint8_t> plain;
      plain.reserve(usize);
      bool ok = true;
      for (size_t k = 0; k < blocks && ok; ++k) {
        const uint32_t bc = Be32(d, hdr + k * 8), bu = Be32(d, hdr + k * 8 + 4);
        ok = Lzo1x(d.data() + data, bc, block, bu);
        plain.insert(plain.end(), block.begin(), block.end());
        data += bc;
      }
      if (!ok) {
        ++bad;
        continue;
      }
      ScanUe3Buffer(name, plain, seen, out);
      off = data - 1;
    }
    ScanUe3Buffer(name, d, seen, out);
  }
  if (bad) std::fprintf(stderr, "%zu compressed chunks could not be decoded\n", bad);
  return out;
}
