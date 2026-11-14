package com.udarmolota.xdroid360;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;

/**
 * Dragon Age: Origins' downloadable content from the player's own Xbox 360 packages: package
 * files (as copied from a console's Content\0000000000000000\454108C0\00000002\), a .zip of them,
 * or the Ultimate Edition's second disc image. Each DLC package is extracted by the STFS reader
 * (StfsExtractor) to userdata/content/454108C0/00000002/<content ID>/ with its name beside it,
 * where the game finds it (DAORecomp's content/install.cpp lays it out the same way).
 *
 * Title Update 6 or 7 goes to <instance>/title_update/ (where the game and the recipe look for
 * it), only the one made for the player's disc (its media ID: a zip may hold the updates of every
 * edition), and only an update the game is made for (its default.xexp's SHA-1). The game then
 * has to be built again ("Update game"): the recipe translates the title update's code instead
 * of the disc's. Updates are taken in a first pass, so the campaigns made for them (Awakening,
 * Leliana's Song, The Darkspawn Chronicles, The Golems of Amgarrak, Witch Hunt) picked at the same
 * time are installed too; without a title update those campaigns are passed over.
 */
final class DlcInstaller {
    private static final String TITLE_ID = Instance.DAO_TITLE_ID;
    private static final String TYPE_DLC = "0x00000002", TYPE_TITLE_UPDATE = "0x000B0000";
    /** The DLC that need Title Update 6 or 7, by words of their display names (lower case). */
    private static final String[] NEEDS_UPDATE = { "awakening", "leliana", "darkspawn", "golems", "witch hunt" };
    /**
     * The title updates the game is made for, by their default.xexp's SHA-1 (recomp's
     * kernel/title_update.cpp): Title Update 6 and 7 for the English, Italian and Spanish disc,
     * and Title Update 7 for the Russian one.
     */
    private static final String[][] KNOWN_UPDATES = {
            { "4141b608e44c4ed35eb4e24ba2c05844a2f57a54", "6" },
            { "dd5b493f4fe2fb4c99e6ef7ed6923ddddeee8291", "7" },
            { "b97ea0822418c14527298d1474428f13699d5943", "7" } };
    /** A package's header is 0xA000+ bytes; anything smaller on a disc isn't one. */
    private static final long MIN_PACKAGE = 0xB000;
    /** Enough of a package for the probe to read its header and display name. */
    private static final int HEADER = 0xC000;

    interface Report {
        /** What is being done now, and how far (0..1, or -1 when unknown). */
        void progress(String what, double fraction);
    }

    /** What happened, by package (display names). */
    static final class Result {
        final List<String> installed = new ArrayList<>(), already = new ArrayList<>(),
                needUpdate = new ArrayList<>(), failed = new ArrayList<>();
        int otherGames;
        /** The title update installed now (6 or 7), or 0. */
        int updateInstalled;
        /** A title update was found that's already installed, for another edition, or unknown. */
        boolean updateAlready, updateOtherEdition, updateUnknown;

        boolean nothingFound() {
            return installed.isEmpty() && already.isEmpty() && needUpdate.isEmpty() && failed.isEmpty()
                    && updateInstalled == 0 && !updateAlready && !updateOtherEdition && !updateUnknown;
        }
    }

    /** Pass 1 takes the title update, pass 2 the downloadable content. */
    private int pass;
    /** The player's disc's media ID (upper-case hex), or "" when it can't be read. */
    private String discMedia = "";

    private final Context context;
    private final Instance instance;
    private final Report report;
    private final AtomicBoolean cancelled;
    private final Result result = new Result();
    private final File scratch;

    private DlcInstaller(Context context, Instance instance, Report report, AtomicBoolean cancelled) {
        this.context = context;
        this.instance = instance;
        this.report = report;
        this.cancelled = cancelled;
        // On the instance's own storage: a zipped package (2 GB for Awakening) is inflated here
        // before it's extracted, which the app's small internal cache may not hold.
        this.scratch = new File(instance.root, "dlc-work");
    }

    /** Installs the DLC in the files picked. Not on the UI thread. */
    static Result install(Context context, Instance instance, List<Uri> picked, Report report,
                          AtomicBoolean cancelled) {
        DlcInstaller d = new DlcInstaller(context, instance, report, cancelled);
        d.run(picked);
        return d.result;
    }

    private void run(List<Uri> picked) {
        if (!StfsExtractor.available()) {
            result.failed.add("this launcher has no STFS reader");
            return;
        }
        deleteTree(scratch);
        discMedia = discMediaId(new File(instance.game(), "default.xex"));
        for (pass = 1; pass <= 2; pass++) {
            for (Uri uri : picked) {
                if (cancelled.get()) return;
                try {
                    fromUri(uri);
                } catch (Recipe.Failure e) {
                    if ("cancelled".equals(e.code)) return;
                    if (pass == 2) result.failed.add(uri.getLastPathSegment() + ": " + e.getMessage());
                } catch (IOException e) {
                    if (pass == 2) result.failed.add(uri.getLastPathSegment() + ": " + e);
                }
            }
        }
        deleteTree(scratch);
    }

    /** Title Update 6 or 7 installed for this game (its default.xexp), or 0. */
    static int installedUpdate(Instance instance) {
        File xexp = new File(instance.root, "title_update/default.xexp");
        if (!xexp.isFile()) return 0;
        try {
            String sha = sha1(xexp);
            for (String[] k : KNOWN_UPDATES) if (k[0].equals(sha)) return Integer.parseInt(k[1]);
        } catch (IOException ignored) {
        }
        return 0;
    }

    private static String sha1(File f) throws IOException {
        java.security.MessageDigest md;
        try {
            md = java.security.MessageDigest.getInstance("SHA-1");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] b = new byte[1 << 16];
        try (FileInputStream in = new FileInputStream(f)) {
            for (int n; (n = in.read(b)) > 0; ) md.update(b, 0, n);
        }
        StringBuilder s = new StringBuilder();
        for (byte x : md.digest()) s.append(String.format(Locale.ROOT, "%02x", x));
        return s.toString();
    }

    /** The media ID in a default.xex's execution info (optional header 0x00040006), or "". */
    private static String discMediaId(File xex) {
        byte[] h = new byte[0x4000];
        int n;
        try (FileInputStream in = new FileInputStream(xex)) {
            n = in.read(h);
        } catch (IOException e) {
            return "";
        }
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(h, 0, Math.max(0, n));
        if (n < 0x20 || b.getInt(0) != 0x58455832) return ""; // "XEX2", big-endian
        int count = b.getInt(0x14);
        for (int i = 0; i < count && 0x18 + i * 8 + 8 <= n; i++) {
            int key = b.getInt(0x18 + i * 8), value = b.getInt(0x18 + i * 8 + 4);
            if (key == 0x00040006 && value > 0 && value + 4 <= n) {
                return String.format(Locale.ROOT, "%08X", b.getInt(value));
            }
        }
        return "";
    }

    /** A package, a .zip of packages, or a disc image holding them. */
    private void fromUri(Uri uri) throws IOException, Recipe.Failure {
        try (ParcelFileDescriptor pfd = GameSource.open(context, uri)) {
            String probe = StfsExtractor.nativeProbe(pfd.getFd());
            if (probe != null) {
                fromPackage(pfd, probe);
                return;
            }
        }
        if (ZipSource.looksLikeZip(context, uri)) {
            fromZip(uri);
            return;
        }
        try (ParcelFileDescriptor pfd = GameSource.open(context, uri);
             FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            fromImage(in.getChannel());
        }
    }

    private void fromZip(Uri uri) throws IOException, Recipe.Failure {
        try (ZipSource zip = ZipSource.open(context, uri)) {
            List<ZipEntry> inFolders = zip.packages();
            for (ZipEntry e : zip.files()) {
                if (cancelled.get()) return;
                String name = new File(e.getName()).getName();
                if (inFolders.contains(e)) {
                    String type = ZipSource.contentType(e);
                    if (!"00000002".equals(type) && !"000B0000".equals(type)) continue;
                } else if (e.getSize() < MIN_PACKAGE) {
                    // Packages elsewhere in the zip go by their magic (a title update's name can
                    // have a dot: TU_12K2260_0000004000000.00000000001O1).
                    continue;
                }
                // Its header first: one that isn't wanted (Awakening: 2 GB) isn't inflated.
                File head = scratchFile();
                zip.inflateHead(e, head, HEADER);
                if (!wantedHead(head)) continue;
                File file = scratchFile();
                zip.inflate(e, file, (done, total) -> step(name, done, total, 0, 0.5));
                fromFile(file, 0.5);
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    /** The Ultimate Edition's disc 2: its packages are files in the image. */
    private void fromImage(FileChannel channel) throws IOException, Recipe.Failure {
        Xiso iso = new Xiso(channel);
        for (Xiso.Entry e : iso.entries) {
            if (cancelled.get()) return;
            if (e.dir || e.size < MIN_PACKAGE) continue;
            ByteBuffer head = iso.head(e, HEADER);
            if (head.remaining() < 4) continue;
            byte[] bytes = new byte[head.remaining()];
            head.get(bytes);
            if (!isPackageMagic(bytes)) continue;
            File headFile = scratchFile();
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(headFile)) {
                o.write(bytes);
            }
            if (!wantedHead(headFile)) continue;
            File file = scratchFile();
            String name = new File(e.path).getName();
            iso.copy(e, file, (done, total) -> step(name, done, total, 0, 0.5));
            fromFile(file, 0.5);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private void fromFile(File file, double from) throws IOException, Recipe.Failure {
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) {
            String probe = StfsExtractor.nativeProbe(pfd.getFd());
            if (probe != null) fromPackage(pfd, probe, from);
        }
    }

    private void fromPackage(ParcelFileDescriptor pfd, String probe) throws Recipe.Failure {
        fromPackage(pfd, probe, 0);
    }

    /**
     * A package's header (the first HEADER bytes, in a file) probed before the whole package is
     * copied: false, with the reason noted, when it isn't one to install. A header the probe can't
     * read alone is let through, to be judged on the whole package.
     */
    private boolean wantedHead(File head) throws IOException {
        if (!isPackageMagic(readHead(head))) return false;
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(head, ParcelFileDescriptor.MODE_READ_ONLY)) {
            String probe = StfsExtractor.nativeProbe(pfd.getFd());
            if (probe == null) return true;
            return judge(new JSONObject(probe));
        } catch (JSONException e) {
            return true;
        }
    }

    /**
     * Whether a package is one to install in this pass (pass 1: this disc's title update; pass 2:
     * this game's DLC); the reason noted if not (each package is noted in its own pass only).
     */
    private boolean judge(JSONObject h) {
        String title = h.optString("title_id").toUpperCase(Locale.ROOT);
        String type = h.optString("content_type");
        String name = h.optString("display_name", h.optString("content_id"));
        if (!TITLE_ID.equals(title)) {
            if (pass == 2) result.otherGames++;
            return false;
        }
        if (TYPE_TITLE_UPDATE.equalsIgnoreCase(type)) {
            if (pass != 1) return false;
            String media = h.optString("media_id").toUpperCase(Locale.ROOT);
            if (!discMedia.isEmpty() && !discMedia.equals(media)) {
                result.updateOtherEdition = true; // another edition's update, in a zip of them all
                return false;
            }
            if (h.optString("content_id").equalsIgnoreCase(installedUpdateId()) && installedUpdate(instance) > 0) {
                result.updateAlready = true;
                return false;
            }
            return true;
        }
        if (pass != 2 || !TYPE_DLC.equalsIgnoreCase(type)) return false; // a save, the disc's art...
        String lower = name.toLowerCase(Locale.ROOT);
        for (String word : NEEDS_UPDATE) {
            if (lower.contains(word) && installedUpdate(instance) == 0) {
                result.needUpdate.add(name);
                return false;
            }
        }
        String id = h.optString("content_id");
        if (!id.isEmpty() && new File(dlcFolder(), id).isDirectory()) {
            result.already.add(name);
            return false;
        }
        return true;
    }

    private File dlcFolder() {
        return new File(instance.userdata(), "content/" + TITLE_ID + "/00000002");
    }

    private void fromPackage(ParcelFileDescriptor pfd, String probe, double from) throws Recipe.Failure {
        JSONObject h;
        try {
            h = new JSONObject(probe);
        } catch (JSONException e) {
            result.failed.add(e.toString());
            return;
        }
        if (!judge(h)) return;
        if (TYPE_TITLE_UPDATE.equalsIgnoreCase(h.optString("content_type"))) {
            installUpdate(pfd, h, from);
            return;
        }
        String name = h.optString("display_name", h.optString("content_id"));
        File folder = dlcFolder();
        String id = h.optString("content_id");
        long size = h.optLong("size");
        //noinspection ResultOfMethodCallIgnored
        instance.userdata().mkdirs();
        if (size > 0 && new StatFs(instance.userdata().getPath()).getAvailableBytes() < size + (64L << 20)) {
            result.failed.add(name + ": " + context.getString(R.string.dlc_no_space));
            return;
        }
        double span = 1 - from;
        int code = StfsExtractor.nativeExtract(pfd.getFd(), instance.root.getPath(),
                (done, total) -> step(name, done, total, from, span));
        if (code == 0) {
            result.installed.add(name);
        } else if (code == 5) {
            throw new Recipe.Failure("cancelled", "");
        } else {
            // A half-extracted folder would read as installed next time.
            if (!id.isEmpty()) deleteTree(new File(folder, id));
            String why = code > 0 && code < StfsExtractor.ERRORS.length ? StfsExtractor.ERRORS[code] : "error " + code;
            result.failed.add(name + ": " + why);
        }
    }

    /**
     * This disc's title update to <instance>/title_update/ (the STFS reader's place for it), in
     * place of one installed before; kept only if its default.xexp is one the game is made for.
     */
    private void installUpdate(ParcelFileDescriptor pfd, JSONObject h, double from) throws Recipe.Failure {
        String name = h.optString("display_name", "Title Update");
        File folder = new File(instance.root, "title_update");
        deleteTree(folder);
        double span = 1 - from;
        int code = StfsExtractor.nativeExtract(pfd.getFd(), instance.root.getPath(),
                (done, total) -> step(name, done, total, from, span));
        if (code == 5) {
            deleteTree(folder);
            throw new Recipe.Failure("cancelled", "");
        }
        int number = code == 0 ? installedUpdate(instance) : 0;
        if (number == 0) {
            deleteTree(folder);
            if (code == 0) {
                result.updateUnknown = true;
            } else {
                String why = code > 0 && code < StfsExtractor.ERRORS.length ? StfsExtractor.ERRORS[code] : "error " + code;
                result.failed.add(name + ": " + why);
            }
            return;
        }
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(folder, ".content_id"))) {
            out.write(h.optString("content_id").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        } catch (IOException ignored) {
        }
        result.updateInstalled = number;
    }

    /** The content ID of the title update installed here (written by installUpdate), or "". */
    private String installedUpdateId() {
        File f = new File(instance.root, "title_update/.content_id");
        if (!f.isFile()) return "";
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.US_ASCII).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private boolean step(String name, long done, long total, double from, double span) {
        report.progress(name, total > 0 ? from + span * done / total : -1);
        return !cancelled.get();
    }

    private File scratchFile() {
        //noinspection ResultOfMethodCallIgnored
        scratch.mkdirs();
        return new File(scratch, "package.stfs");
    }

    /** LIVE and PIRS (Xbox Live's read-only packages) or CON (a console's own). */
    private static boolean isPackageMagic(byte[] b) {
        if (b == null || b.length < 4) return false;
        String m = new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return m.equals("LIVE") || m.equals("PIRS") || m.equals("CON ");
    }

    private static byte[] readHead(File f) throws IOException {
        byte[] b = new byte[4];
        try (FileInputStream in = new FileInputStream(f)) {
            return in.read(b) == 4 ? b : null;
        }
    }

    private static void deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteTree(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
