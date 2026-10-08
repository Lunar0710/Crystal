package dev.lunar.packs;

import java.util.List;

/**
 * One thing you can take from a pack (the crystal, the anchor, ...): the
 * files inside a resource pack that make it up, as path prefixes under
 * assets/minecraft/. Everything these files point to (textures of a model,
 * a parent model of the pack's own) is taken along when the mix is written.
 * {@code preview} is the texture shown in the menu, under assets/minecraft/.
 */
public record Slot(String id, String group, String label, String icon, String preview, List<String> prefixes) {

    private static Slot item(String id, String group, String label, String... items) {
        String[] prefixes = new String[items.length * 3];
        for (int i = 0; i < items.length; i++) {
            prefixes[i * 3] = "textures/item/" + items[i];
            prefixes[i * 3 + 1] = "models/item/" + items[i];
            prefixes[i * 3 + 2] = "items/" + items[i];
        }
        return new Slot(id, group, label, items[0], "textures/item/" + items[0] + ".png", List.of(prefixes));
    }

    private static Slot block(String id, String group, String label, String block) {
        return new Slot(id, group, label, block, "textures/block/" + block + ".png", List.of(
                "textures/block/" + block, "models/block/" + block, "blockstates/" + block, "models/item/" + block, "items/" + block));
    }

    public static final List<Slot> ALL = List.of(
            // Crystal PvP
            new Slot("crystal", "Crystal PvP", "End Crystal", "end_crystal", "textures/entity/end_crystal/end_crystal.png", List.of(
                    "textures/entity/end_crystal/", "textures/item/end_crystal", "models/item/end_crystal", "items/end_crystal")),
            new Slot("anchor", "Crystal PvP", "Respawn Anchor", "respawn_anchor", "textures/block/respawn_anchor_side4.png", List.of(
                    "textures/block/respawn_anchor", "models/block/respawn_anchor", "blockstates/respawn_anchor",
                    "models/item/respawn_anchor", "items/respawn_anchor")),
            block("glowstone", "Crystal PvP", "Glowstone", "glowstone"),
            block("obsidian", "Crystal PvP", "Obsidian", "obsidian"),
            item("totem", "Crystal PvP", "Totem", "totem_of_undying"),
            item("pearl", "Crystal PvP", "Enderperle", "ender_pearl"),
            item("xp", "Crystal PvP", "XP-Flasche", "experience_bottle"),
            item("gapple", "Crystal PvP", "Goldene Äpfel", "golden_apple", "enchanted_golden_apple"),
            new Slot("shield", "Crystal PvP", "Schild", "shield", "textures/entity/shield_base_nopattern.png", List.of(
                    "textures/entity/shield", "models/item/shield", "items/shield")),
            // Weapons and tools, one per material
            item("netherite_sword", "Waffen & Werkzeug", "Netherite-Schwert", "netherite_sword"),
            item("diamond_sword", "Waffen & Werkzeug", "Diamant-Schwert", "diamond_sword"),
            item("netherite_pickaxe", "Waffen & Werkzeug", "Netherite-Spitzhacke", "netherite_pickaxe"),
            item("diamond_pickaxe", "Waffen & Werkzeug", "Diamant-Spitzhacke", "diamond_pickaxe"),
            item("netherite_axe", "Waffen & Werkzeug", "Netherite-Axt", "netherite_axe"),
            item("diamond_axe", "Waffen & Werkzeug", "Diamant-Axt", "diamond_axe"),
            item("netherite_shovel", "Waffen & Werkzeug", "Netherite-Schaufel", "netherite_shovel"),
            item("mace", "Waffen & Werkzeug", "Streitkolben", "mace"),
            new Slot("bow", "Waffen & Werkzeug", "Bogen", "bow", "textures/item/bow.png", List.of(
                    // "bow." and "bow_pulling", never "bowl"
                    "textures/item/bow.", "textures/item/bow_pulling", "models/item/bow.", "models/item/bow_pulling", "items/bow.")),
            new Slot("crossbow", "Waffen & Werkzeug", "Armbrust", "crossbow", "textures/item/crossbow_standby.png", List.of(
                    "textures/item/crossbow", "models/item/crossbow", "items/crossbow")),
            new Slot("trident", "Waffen & Werkzeug", "Dreizack", "trident", "textures/item/trident.png", List.of(
                    "textures/item/trident", "textures/entity/trident", "models/item/trident", "items/trident")),
            new Slot("arrows", "Waffen & Werkzeug", "Pfeile", "arrow", "textures/item/arrow.png", List.of(
                    "textures/item/arrow", "textures/item/spectral_arrow", "textures/item/tipped_arrow", "textures/entity/projectiles/",
                    "models/item/arrow", "models/item/spectral_arrow", "models/item/tipped_arrow", "items/arrow", "items/spectral_arrow", "items/tipped_arrow")),
            new Slot("elytra", "Waffen & Werkzeug", "Elytra", "elytra", "textures/item/elytra.png", List.of(
                    "textures/entity/equipment/wings/elytra", "textures/entity/elytra", "textures/item/elytra",
                    "textures/item/broken_elytra", "models/item/elytra", "models/item/broken_elytra", "items/elytra")),
            new Slot("fireworks", "Waffen & Werkzeug", "Feuerwerk", "firework_rocket", "textures/item/firework_rocket.png", List.of(
                    "textures/item/firework_rocket", "textures/item/firework_star", "models/item/firework_rocket", "items/firework_rocket")),
            new Slot("potions", "Waffen & Werkzeug", "Tränke", "splash_potion", "textures/item/splash_potion.png", List.of(
                    "textures/item/potion", "textures/item/splash_potion", "textures/item/lingering_potion", "textures/item/potion_overlay",
                    "models/item/potion", "models/item/splash_potion", "models/item/lingering_potion",
                    "items/potion", "items/splash_potion", "items/lingering_potion")),
            // Armour
            new Slot("netherite_armor", "Rüstung", "Netherite-Rüstung", "netherite_chestplate", "textures/item/netherite_chestplate.png", List.of(
                    "textures/entity/equipment/humanoid/netherite", "textures/entity/equipment/humanoid_leggings/netherite",
                    "textures/item/netherite_helmet", "textures/item/netherite_chestplate", "textures/item/netherite_leggings",
                    "textures/item/netherite_boots", "models/item/netherite_helmet", "models/item/netherite_chestplate",
                    "models/item/netherite_leggings", "models/item/netherite_boots")),
            new Slot("diamond_armor", "Rüstung", "Diamant-Rüstung", "diamond_chestplate", "textures/item/diamond_chestplate.png", List.of(
                    "textures/entity/equipment/humanoid/diamond", "textures/entity/equipment/humanoid_leggings/diamond",
                    "textures/item/diamond_helmet", "textures/item/diamond_chestplate", "textures/item/diamond_leggings",
                    "textures/item/diamond_boots", "models/item/diamond_helmet", "models/item/diamond_chestplate",
                    "models/item/diamond_leggings", "models/item/diamond_boots")),
            // Food
            item("golden_carrot", "Essen", "Goldene Karotte", "golden_carrot"),
            item("steak", "Essen", "Steak", "cooked_beef"),
            // Look and feel
            new Slot("hit", "Optik", "Treffer-Partikel", "iron_sword", "textures/particle/critical_hit.png", List.of(
                    "textures/particle/critical_hit", "textures/particle/enchanted_hit", "textures/particle/sweep",
                    "particles/crit", "particles/enchanted_hit", "particles/sweep_attack")),
            new Slot("explosion", "Optik", "Explosionen", "tnt", "textures/particle/explosion_0.png", List.of(
                    "textures/particle/explosion", "particles/explosion")),
            new Slot("totem_particles", "Optik", "Totem-Partikel", "totem_of_undying", "textures/particle/glitter_0.png", List.of(
                    "textures/particle/glitter", "particles/totem_of_undying")),
            new Slot("fire", "Optik", "Feuer (auch Feuer am Bildschirm)", "flint_and_steel", "textures/block/fire_0.png", List.of(
                    "textures/block/fire_0", "textures/block/fire_1", "textures/block/soul_fire_0", "textures/block/soul_fire_1")),
            new Slot("hud", "Optik", "HUD (Hotbar, Crosshair, Herzen)", "compass", "textures/gui/sprites/hud/hotbar.png", List.of(
                    "textures/gui/sprites/hud/", "textures/gui/icons")),
            new Slot("gui", "Optik", "Inventar & Menüs", "chest", "textures/gui/container/inventory.png", List.of(
                    "textures/gui/container/", "textures/gui/sprites/container/")),
            new Slot("font", "Optik", "Schrift", "name_tag", "textures/font/ascii.png", List.of(
                    "font/", "textures/font/")),
            new Slot("sky", "Optik", "Himmel (OptiFine-Sky)", "light_blue_stained_glass", "", List.of(
                    "optifine/sky/", "mcpatcher/sky/", "textures/environment/")),
            new Slot("sounds", "Optik", "Sounds (Totem, Explosion, Treffer)", "note_block", "", List.of(
                    "sounds/item/totem/", "sounds/random/explode", "sounds/entity/player/attack/", "sounds/random/orb",
                    "sounds/random/levelup", "sounds/block/respawn_anchor/")));

    public static Slot byId(String id) {
        for (Slot s : ALL) if (s.id.equals(id)) return s;
        return null;
    }

    /** Whether a pack path (relative to the pack root) belongs to this slot. */
    public boolean matches(String path) {
        if (!path.startsWith("assets/minecraft/")) return false;
        String rest = path.substring("assets/minecraft/".length());
        for (String p : prefixes) if (rest.startsWith(p)) return true;
        return false;
    }
}
