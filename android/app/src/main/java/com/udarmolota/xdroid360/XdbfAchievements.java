package com.udarmolota.xdroid360;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Reads a game's achievements out of its default.xex: the XEX's resource section holds the title's
 * XDBF ("SPA") file with every achievement (id, points, flags, icon, texts in each language the disc
 * has). The game defines all of it; nothing here is invented.
 *
 * Pure Java (no Android classes) so it can run on a PC too. Handles retail XEX2 files with normal
 * encryption and none/basic compression, which is what the discs' default.xex use. The title
 * update's default.xexp is a delta patch and is not read: the base executable has the base game's
 * achievements.
 */
final class XdbfAchievements {
    /** One achievement. Texts are per language code ("en", "de", ...), as the XDBF string tables. */
    static final class Entry {
        int id;
        int points;
        int flags;
        /** Not shown (name and description) until unlocked: the "show unachieved" flag is clear. */
        boolean secret;
        byte[] icon; // PNG
        final Map<String, String> name = new LinkedHashMap<>();
        final Map<String, String> unlocked = new LinkedHashMap<>();
        final Map<String, String> locked = new LinkedHashMap<>();
    }

    private static final byte[] RETAIL_KEY = hex("20B185A59D28FDC340583FBB0896BF91");
    private static final long XDBF_NS_METADATA = 1, XDBF_NS_IMAGE = 2, XDBF_NS_STRINGS = 3;
    private static final long XACH = 0x58414348L;

    private XdbfAchievements() {}

    /** The XDBF language id's two-letter code (the ids the string tables are filed under). */
    static String languageCode(int id) {
        switch (id) {
            case 1: return "en";
            case 2: return "ja";
            case 3: return "de";
            case 4: return "fr";
            case 5: return "es";
            case 6: return "it";
            case 7: return "ko";
            case 8: return "zh";
            case 9: return "pt";
            case 11: return "pl";
            case 12: return "ru";
            default: return "lang" + id;
        }
    }

    static List<Entry> readFromXex(java.io.File xex) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(xex, "r")) {
            return parse(readResource(f));
        }
    }

    // ------------------------------------------------------------------ XEX

    private static byte[] readResource(RandomAccessFile f) throws IOException {
        byte[] head = new byte[24];
        f.readFully(head);
        if (u32(head, 0) != 0x58455832L) throw new IOException("not an XEX2 file");
        long peOffset = u32(head, 8);
        long securityOffset = u32(head, 16);
        int optionalCount = (int) u32(head, 20);
        if (optionalCount < 0 || optionalCount > 64) throw new IOException("bad XEX header");
        byte[] opt = new byte[optionalCount * 8];
        f.readFully(opt);
        long resourceInfo = -1, fileFormat = -1, baseAddress = 0;
        for (int i = 0; i < optionalCount; i++) {
            long key = u32(opt, i * 8), value = u32(opt, i * 8 + 4);
            if (key == 0x2FF) resourceInfo = value;
            else if (key == 0x3FF) fileFormat = value;
            else if (key == 0x10201) baseAddress = value;
        }
        if (resourceInfo < 0 || fileFormat < 0) throw new IOException("XEX has no resource or file format info");

        byte[] fmt = new byte[8];
        f.seek(fileFormat);
        f.readFully(fmt);
        long fmtSize = u32(fmt, 0);
        int encryption = u16(fmt, 4), compression = u16(fmt, 6);
        if (compression > 1) throw new IOException("XEX compression " + compression + " is not supported");
        // Basic compression: (data size, zero size) pairs; none: the file is the image.
        List<long[]> blocks = new ArrayList<>(); // {imageStart, fileOffset, dataSize, zeroSize}
        long image = 0, file = peOffset;
        if (compression == 1) {
            byte[] raw = new byte[(int) (fmtSize - 8)];
            f.readFully(raw);
            for (int i = 0; i + 8 <= raw.length; i += 8) {
                long data = u32(raw, i), zero = u32(raw, i + 4);
                blocks.add(new long[] { image, file, data, zero });
                image += data + zero;
                file += data;
            }
        } else {
            blocks.add(new long[] { 0, peOffset, f.length() - peOffset, 0 });
        }

        // The title's resource: the first entry of the resource table (named after the title id).
        f.seek(resourceInfo);
        byte[] rhead = new byte[4];
        f.readFully(rhead);
        if (u32(rhead, 0) < 20) throw new IOException("XEX has no resources");
        byte[] entry = new byte[16];
        f.readFully(entry);
        long address = u32(entry, 8), size = u32(entry, 12);
        if (size <= 0 || size > 64L * 1024 * 1024) throw new IOException("bad resource size");

        byte[] key = null;
        if (encryption != 0) {
            byte[] enc = new byte[16];
            f.seek(securityOffset + 0x150);
            f.readFully(enc);
            try {
                Cipher c = Cipher.getInstance("AES/ECB/NoPadding");
                c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(RETAIL_KEY, "AES"));
                key = c.doFinal(enc);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
        return readImage(f, blocks, key, peOffset, address - baseAddress, (int) size);
    }

    /** Bytes [at, at+length) of the decrypted, expanded image. */
    private static byte[] readImage(RandomAccessFile f, List<long[]> blocks, byte[] key, long origin, long at, int length) throws IOException {
        byte[] out = new byte[length];
        long end = at + length;
        for (long[] b : blocks) {
            long dataStart = b[0], dataEnd = b[0] + b[2];
            long from = Math.max(at, dataStart), to = Math.min(end, dataEnd);
            if (from >= to) continue; // a zero range or outside the data: out stays zero
            readDecrypted(f, b[1] + (from - dataStart), out, (int) (from - at), (int) (to - from), key, origin);
        }
        return out;
    }

    /**
     * Reads length bytes at fileOffset, decrypting AES-CBC (zero IV; the chain runs through all the
     * encrypted data, which starts at origin, the PE offset): start at the 16-byte block holding
     * fileOffset, with the ciphertext block before it as the IV.
     */
    private static void readDecrypted(RandomAccessFile f, long fileOffset, byte[] out, int outAt, int length,
                                      byte[] key, long origin) throws IOException {
        if (key == null) {
            f.seek(fileOffset);
            f.readFully(out, outAt, length);
            return;
        }
        long start = origin + ((fileOffset - origin) / 16) * 16;
        byte[] iv = new byte[16];
        if (start > origin) {
            f.seek(start - 16);
            f.readFully(iv);
        }
        int skip = (int) (fileOffset - start);
        byte[] enc = new byte[(skip + length + 15) / 16 * 16];
        f.seek(start);
        f.readFully(enc);
        try {
            Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            System.arraycopy(c.doFinal(enc), skip, out, outAt, length);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    // ------------------------------------------------------------------ XDBF

    static List<Entry> parse(byte[] x) throws IOException {
        if (x.length < 24 || u32(x, 0) != 0x58444246L) throw new IOException("resource is not an XDBF");
        long tableLen = u32(x, 8), entryCount = u32(x, 12), freeLen = u32(x, 16);
        long dataStart = 24 + tableLen * 18 + freeLen * 8;
        Map<Long, byte[]> strings = new LinkedHashMap<>();
        Map<Long, byte[]> images = new LinkedHashMap<>();
        byte[] xach = null;
        for (int i = 0; i < entryCount; i++) {
            int p = 24 + i * 18;
            int ns = u16(x, p);
            long id = u64(x, p + 2), off = u32(x, p + 10), size = u32(x, p + 14);
            int s = (int) (dataStart + off);
            if (s < 0 || s + size > x.length) continue;
            byte[] data = java.util.Arrays.copyOfRange(x, s, s + (int) size);
            if (ns == XDBF_NS_STRINGS) strings.put(id, data);
            else if (ns == XDBF_NS_IMAGE) images.put(id, data);
            else if (ns == XDBF_NS_METADATA && id == XACH) xach = data;
        }
        if (xach == null) throw new IOException("no achievement table");

        Map<String, Map<Integer, String>> tables = new LinkedHashMap<>();
        for (Map.Entry<Long, byte[]> e : strings.entrySet()) {
            tables.put(languageCode((int) (long) e.getKey()), stringTable(e.getValue()));
        }
        int count = u16(xach, 12);
        List<Entry> out = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            int p = 14 + k * 36;
            if (p + 36 > xach.length) break;
            Entry a = new Entry();
            a.id = u16(xach, p);
            int nameId = u16(xach, p + 2), unlockedId = u16(xach, p + 4), lockedId = u16(xach, p + 6);
            long imageId = u32(xach, p + 8);
            a.points = (int) (u32(xach, p + 12) >>> 16); // the 16 high bits (the rest is padding)
            a.flags = (int) u32(xach, p + 16);
            a.secret = (a.flags & 0x8) == 0;
            a.icon = images.get(imageId);
            for (Map.Entry<String, Map<Integer, String>> t : tables.entrySet()) {
                put(a.name, t.getKey(), t.getValue().get(nameId));
                put(a.unlocked, t.getKey(), t.getValue().get(unlockedId));
                put(a.locked, t.getKey(), t.getValue().get(lockedId));
            }
            out.add(a);
        }
        return out;
    }

    private static void put(Map<String, String> m, String k, String v) {
        if (v != null) m.put(k, v);
    }

    private static Map<Integer, String> stringTable(byte[] b) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (b.length < 14) return out;
        int count = u16(b, 12), p = 14;
        for (int i = 0; i < count && p + 4 <= b.length; i++) {
            int id = u16(b, p), len = u16(b, p + 2);
            if (p + 4 + len > b.length) break;
            out.put(id, new String(b, p + 4, len, StandardCharsets.UTF_8));
            p += 4 + len;
        }
        return out;
    }

    // ------------------------------------------------------------------ bytes

    private static int u16(byte[] b, int o) { return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF); }
    private static long u32(byte[] b, int o) {
        return ((long) (b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }
    private static long u64(byte[] b, int o) { return (u32(b, o) << 32) | u32(b, o + 4); }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
}
