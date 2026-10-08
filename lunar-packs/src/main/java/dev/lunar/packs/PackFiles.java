package dev.lunar.packs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** One resource pack in the resourcepacks folder, a .zip or a folder: its file list and contents. */
public final class PackFiles {

    public final Path path;
    public final String name;
    private final List<String> entries;

    private PackFiles(Path path, List<String> entries) {
        this.path = path;
        this.name = path.getFileName().toString();
        this.entries = entries;
    }

    /** Every pack in the folder; ones that can't be read are left out. The mix itself never. */
    public static List<PackFiles> scan(Path folder, String skipName) {
        List<PackFiles> packs = new ArrayList<>();
        try (Stream<Path> list = Files.list(folder)) {
            for (Path p : (Iterable<Path>) list.sorted()::iterator) {
                if (p.getFileName().toString().equals(skipName)) continue;
                try {
                    if (Files.isDirectory(p) && Files.exists(p.resolve("pack.mcmeta"))) packs.add(new PackFiles(p, listFolder(p)));
                    else if (p.toString().toLowerCase().endsWith(".zip")) packs.add(new PackFiles(p, listZip(p)));
                } catch (IOException | RuntimeException ignored) {
                    // Broken zip: not offered.
                }
            }
        } catch (IOException ignored) {
            // No resourcepacks folder yet.
        }
        return packs;
    }

    private static List<String> listZip(Path zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile z = new ZipFile(zip.toFile())) {
            var it = z.entries();
            while (it.hasMoreElements()) {
                ZipEntry e = it.nextElement();
                if (!e.isDirectory()) names.add(e.getName().replace('\\', '/'));
            }
        }
        return names;
    }

    private static List<String> listFolder(Path dir) throws IOException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(f -> names.add(dir.relativize(f).toString().replace('\\', '/')));
        }
        return names;
    }

    public List<String> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean has(String entry) {
        return entries.contains(entry);
    }

    /** Whether the pack changes anything this slot covers. */
    public boolean covers(Slot slot) {
        for (String e : entries) if (slot.matches(e)) return true;
        return false;
    }

    public byte[] read(String entry) throws IOException {
        if (Files.isDirectory(path)) return Files.readAllBytes(path.resolve(entry));
        try (ZipFile z = new ZipFile(path.toFile())) {
            ZipEntry e = z.getEntry(entry);
            if (e == null) throw new IOException("missing " + entry);
            try (InputStream in = z.getInputStream(e)) {
                return in.readAllBytes();
            }
        }
    }
}
