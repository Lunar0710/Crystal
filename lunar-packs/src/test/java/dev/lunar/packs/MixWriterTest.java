package dev.lunar.packs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixWriterTest {

    private static void zip(Path file, Map<String, String> entries) throws IOException {
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
    }

    @Test
    void takesOnlyTheChosenSlotWithItsReferences(@TempDir Path dir) throws IOException {
        zip(dir.resolve("K1RBE.zip"), Map.of(
                "pack.mcmeta", "{}",
                "assets/minecraft/models/item/totem_of_undying.json", "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"minecraft:item/k1rbe/totem\"}}",
                "assets/minecraft/textures/item/k1rbe/totem.png", "png",
                "assets/minecraft/textures/item/k1rbe/totem.png.mcmeta", "{\"animation\":{}}",
                "assets/minecraft/textures/block/respawn_anchor_top.png", "anchor",
                "assets/minecraft/textures/item/diamond_sword.png", "sword"));
        zip(dir.resolve("Other.zip"), Map.of(
                "pack.mcmeta", "{}",
                "assets/minecraft/textures/entity/end_crystal/end_crystal.png", "crystal"));

        List<PackFiles> packs = PackFiles.scan(dir, MixWriter.FOLDER);
        assertEquals(2, packs.size());
        assertTrue(packs.get(0).covers(Slot.byId("totem")));
        assertFalse(packs.get(1).covers(Slot.byId("totem")));

        int written = MixWriter.write(dir, Map.of("totem", "K1RBE.zip", "crystal", "Other.zip"), packs);
        Path mix = dir.resolve(MixWriter.FOLDER);
        assertEquals(4, written);
        assertTrue(Files.exists(mix.resolve("pack.mcmeta")));
        assertTrue(Files.exists(mix.resolve("assets/minecraft/models/item/totem_of_undying.json")));
        // The custom texture the model points to, and its animation file.
        assertTrue(Files.exists(mix.resolve("assets/minecraft/textures/item/k1rbe/totem.png")));
        assertTrue(Files.exists(mix.resolve("assets/minecraft/textures/item/k1rbe/totem.png.mcmeta")));
        assertTrue(Files.exists(mix.resolve("assets/minecraft/textures/entity/end_crystal/end_crystal.png")));
        // Nothing else from K1RBE: no anchor (not chosen), no sword.
        assertFalse(Files.exists(mix.resolve("assets/minecraft/textures/block/respawn_anchor_top.png")));
        assertFalse(Files.exists(mix.resolve("assets/minecraft/textures/item/diamond_sword.png")));
    }

    @Test
    void followsTexturesInTheirOwnNamespace(@TempDir Path dir) throws IOException {
        zip(dir.resolve("Custom.zip"), Map.of(
                "pack.mcmeta", "{}",
                "assets/minecraft/items/end_crystal.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"realm:item/crystal\"}}",
                "assets/realm/models/item/crystal.json", "{\"textures\":{\"layer0\":\"realm:item/crystal_tex\"}}",
                "assets/realm/textures/item/crystal_tex.png", "png",
                "assets/realm/textures/item/unrelated.png", "png"));
        PackFiles pack = PackFiles.scan(dir, MixWriter.FOLDER).get(0);
        var files = MixWriter.filesFor(pack, Slot.byId("crystal"));
        assertTrue(files.contains("assets/realm/models/item/crystal.json"));
        assertTrue(files.contains("assets/realm/textures/item/crystal_tex.png"));
        assertFalse(files.contains("assets/realm/textures/item/unrelated.png"));
        assertEquals("assets/realm/textures/item/crystal_tex.png", MixWriter.previewEntry(pack, Slot.byId("crystal")));
    }

    @Test
    void bowDoesNotTakeTheBowl() {
        Slot bow = Slot.byId("bow");
        assertTrue(bow.matches("assets/minecraft/textures/item/bow.png"));
        assertTrue(bow.matches("assets/minecraft/textures/item/bow_pulling_2.png"));
        assertFalse(bow.matches("assets/minecraft/textures/item/bowl.png"));
    }

    @Test
    void slotIdsAreUnique() {
        assertEquals(Slot.ALL.size(), Slot.ALL.stream().map(Slot::id).distinct().count());
    }

    @Test
    void theMixItselfIsNeverOffered(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve(MixWriter.FOLDER));
        Files.writeString(dir.resolve(MixWriter.FOLDER).resolve("pack.mcmeta"), "{}");
        assertEquals(0, PackFiles.scan(dir, MixWriter.FOLDER).size());
    }
}
