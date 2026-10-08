package dev.lunar.packs;

import java.util.List;

/**
 * One thing you can take from a pack (the crystal, the anchor, ...): the
 * files inside a resource pack that make it up, as path prefixes under
 * assets/minecraft/. Everything these files point to (textures of a model,
 * a parent model of the pack's own) is taken along when the mix is written.
 */
public record Slot(String id, String label, String icon, List<String> prefixes) {

    public static final List<Slot> ALL = List.of(
            new Slot("crystal", "End Crystal", "end_crystal", List.of(
                    "textures/entity/end_crystal/", "textures/item/end_crystal", "models/item/end_crystal", "items/end_crystal")),
            new Slot("anchor", "Respawn Anchor", "respawn_anchor", List.of(
                    "textures/block/respawn_anchor", "models/block/respawn_anchor", "blockstates/respawn_anchor",
                    "models/item/respawn_anchor", "items/respawn_anchor")),
            new Slot("glowstone", "Glowstone", "glowstone", List.of(
                    "textures/block/glowstone", "models/block/glowstone", "blockstates/glowstone", "models/item/glowstone", "items/glowstone")),
            new Slot("obsidian", "Obsidian", "obsidian", List.of(
                    "textures/block/obsidian", "models/block/obsidian", "blockstates/obsidian", "models/item/obsidian", "items/obsidian")),
            new Slot("totem", "Totem", "totem_of_undying", List.of(
                    "textures/item/totem_of_undying", "models/item/totem_of_undying", "items/totem_of_undying")),
            new Slot("elytra", "Elytra", "elytra", List.of(
                    "textures/entity/equipment/wings/elytra", "textures/entity/elytra", "textures/item/elytra",
                    "textures/item/broken_elytra", "models/item/elytra", "models/item/broken_elytra", "items/elytra")),
            new Slot("shield", "Schild", "shield", List.of(
                    "textures/entity/shield", "models/item/shield", "items/shield")),
            new Slot("gapple", "Goldener Apfel", "golden_apple", List.of(
                    "textures/item/golden_apple", "textures/item/enchanted_golden_apple", "models/item/golden_apple",
                    "models/item/enchanted_golden_apple", "items/golden_apple", "items/enchanted_golden_apple")),
            new Slot("pearl", "Enderperle", "ender_pearl", List.of(
                    "textures/item/ender_pearl", "models/item/ender_pearl", "items/ender_pearl")),
            new Slot("xp", "XP-Flasche", "experience_bottle", List.of(
                    "textures/item/experience_bottle", "models/item/experience_bottle", "items/experience_bottle")),
            new Slot("sword", "Schwerter", "netherite_sword", List.of(
                    "textures/item/netherite_sword", "textures/item/diamond_sword", "models/item/netherite_sword",
                    "models/item/diamond_sword", "items/netherite_sword", "items/diamond_sword")),
            new Slot("axe", "Äxte", "netherite_axe", List.of(
                    "textures/item/netherite_axe", "textures/item/diamond_axe", "models/item/netherite_axe",
                    "models/item/diamond_axe", "items/netherite_axe", "items/diamond_axe")),
            new Slot("armor", "Netherite-Rüstung", "netherite_chestplate", List.of(
                    "textures/entity/equipment/humanoid/netherite", "textures/entity/equipment/humanoid_leggings/netherite",
                    "textures/item/netherite_helmet", "textures/item/netherite_chestplate", "textures/item/netherite_leggings",
                    "textures/item/netherite_boots")),
            new Slot("particles", "Partikel (Crits, Explosion)", "fire_charge", List.of(
                    "textures/particle/critical_hit", "textures/particle/enchanted_hit", "textures/particle/explosion",
                    "textures/particle/generic_", "particles/crit", "particles/enchanted_hit", "particles/explosion")),
            new Slot("sky", "Himmel (OptiFine-Sky)", "light_blue_stained_glass", List.of(
                    "optifine/sky/", "mcpatcher/sky/")));

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
