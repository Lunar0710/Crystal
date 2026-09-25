package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

/**
 * Render limits: things that pile up by the hundred on farms and in PvP
 * (dropped items, experience orbs, frames, armour stands, signs) are not drawn
 * beyond a distance per kind. Far away they are a few pixels at most, but
 * each still costs its share of every frame. Players and mobs are never
 * touched. Hooked in MixinEntityRenderer and MixinBlockEntityCulling.
 */
public class RenderLimits extends Module {

    private float items = 48f;
    private float orbs = 32f;
    private float decorations = 64f;
    private float signs = 48f;

    public RenderLimits() {
        super("RenderLimits", "Stops drawing items, orbs, frames and signs beyond a distance", ModuleCategory.RENDER);
    }

    /** Whether an entity this far away (squared blocks) is left out. */
    public boolean hides(Entity entity, double distanceSq) {
        EntityType<?> type = entity.getType();
        float limit;
        if (type == net.minecraft.world.entity.EntityType.ITEM) limit = items;
        else if (type == net.minecraft.world.entity.EntityType.EXPERIENCE_ORB) limit = orbs;
        else if (type == net.minecraft.world.entity.EntityType.ARMOR_STAND || type == net.minecraft.world.entity.EntityType.ITEM_FRAME
                || type == net.minecraft.world.entity.EntityType.GLOW_ITEM_FRAME || type == net.minecraft.world.entity.EntityType.PAINTING) limit = decorations;
        else return false;
        return distanceSq > limit * limit;
    }

    /** Whether a sign, banner or head this far away (squared blocks) is left out. */
    public boolean hides(BlockEntity blockEntity, double distanceSq) {
        BlockEntityType<?> type = blockEntity.getType();
        boolean limited = type == net.minecraft.world.level.block.entity.BlockEntityType.SIGN || type == net.minecraft.world.level.block.entity.BlockEntityType.HANGING_SIGN
                || type == net.minecraft.world.level.block.entity.BlockEntityType.BANNER || type == net.minecraft.world.level.block.entity.BlockEntityType.SKULL;
        return limited && distanceSq > signs * signs;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Items bis", () -> items, v -> items = v, 8f, 128f, 4f, 0),
                new SliderSetting("Erfahrung bis", () -> orbs, v -> orbs = v, 8f, 128f, 4f, 0),
                new SliderSetting("Rahmen, Bilder, Ständer bis", () -> decorations, v -> decorations = v, 16f, 128f, 4f, 0),
                new SliderSetting("Schilder, Banner, Köpfe bis", () -> signs, v -> signs = v, 16f, 128f, 4f, 0));
    }
}
