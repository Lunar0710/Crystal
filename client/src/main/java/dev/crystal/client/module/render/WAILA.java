package dev.crystal.client.module.render;

import dev.crystal.client.module.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** "What Am I Looking At" — a HUD line naming whatever's under the crosshair. */
public class WAILA extends HudModule {

    public WAILA() {
        super("WAILA", "\"What Am I Looking At\" — shows the name of the block/entity under your crosshair", 4, 304);
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof EntityHitResult hit) {
            Entity entity = hit.getEntity();
            if (entity instanceof LivingEntity living) {
                return String.format("%s (%.1f/%.1f HP)", entity.getName().getString(), living.getHealth(), living.getMaxHealth());
            }
            return entity.getName().getString();
        }
        if (mc.hitResult instanceof BlockHitResult hit && mc.level != null) {
            Block block = mc.level.getBlockState(hit.getBlockPos()).getBlock();
            return block.getName().getString();
        }
        return "";
    }
}
