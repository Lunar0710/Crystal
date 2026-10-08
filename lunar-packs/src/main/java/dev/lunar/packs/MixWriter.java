package dev.lunar.packs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Writes the mix: a pack folder with, per slot, only the files of the pack
 * chosen for it, plus whatever those files point to inside the same pack
 * (a model's textures, a custom parent model, an animation's .mcmeta).
 */
public final class MixWriter {

    public static final String FOLDER = "Lunar Mix";

    /** "minecraft:item/foo" or "item/foo" inside model/item JSON. */
    private static final Pattern REF = Pattern.compile("\"((?:minecraft:)?(?:item|block|entity|particle|custom|lunar)[a-z0-9_/.\\-]*)\"");

    private MixWriter() {}

    /** Returns the number of files written. */
    public static int write(Path resourcepacks, Map<String, String> choices, List<PackFiles> packs) throws IOException {
        Path out = resourcepacks.resolve(FOLDER);
        deleteTree(out);
        Files.createDirectories(out);
        Files.writeString(out.resolve("pack.mcmeta"), "{\n  \"pack\": {\n    \"description\": \"Lunar Packs: deine Auswahl\",\n"
                + "    \"min_format\": 69,\n    \"max_format\": 99\n  }\n}\n", StandardCharsets.UTF_8);
        int written = 0;
        for (Slot slot : Slot.ALL) {
            String packName = choices.get(slot.id());
            if (packName == null) continue;
            PackFiles pack = packs.stream().filter(p -> p.name.equals(packName)).findFirst().orElse(null);
            if (pack == null) continue;
            for (String entry : filesFor(pack, slot)) {
                Path target = out.resolve(entry);
                Files.createDirectories(target.getParent());
                Files.write(target, pack.read(entry));
                written++;
            }
        }
        return written;
    }

    /** The slot's files in this pack and, transitively, the pack's own files they reference. */
    static Set<String> filesFor(PackFiles pack, Slot slot) throws IOException {
        Set<String> files = new LinkedHashSet<>();
        Deque<String> todo = new ArrayDeque<>();
        for (String e : pack.entries()) if (slot.matches(e)) todo.add(e);
        while (!todo.isEmpty()) {
            String e = todo.poll();
            if (!files.add(e)) continue;
            String mcmeta = e + ".mcmeta";
            if (e.endsWith(".png") && pack.has(mcmeta)) todo.add(mcmeta);
            if (!e.endsWith(".json")) continue;
            String json = new String(pack.read(e), StandardCharsets.UTF_8);
            Matcher m = REF.matcher(json);
            while (m.find()) {
                String ref = m.group(1).replace("minecraft:", "");
                for (String candidate : new String[]{"assets/minecraft/textures/" + ref + ".png", "assets/minecraft/models/" + ref + ".json"}) {
                    if (pack.has(candidate) && !files.contains(candidate)) todo.add(candidate);
                }
            }
        }
        return files;
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
        }
    }
}
