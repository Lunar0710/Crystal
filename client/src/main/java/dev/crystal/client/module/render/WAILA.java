package dev.crystal.client.module.render;

import dev.crystal.client.module.hud.HudModule;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;

/** "What Am I Looking At" — a HUD line naming whatever's under the crosshair. */
public class WAILA extends HudModule {

    public WAILA() {
        super("WAILA", "\"What Am I Looking At\" — shows the name of the block/entity under your crosshair", 4, 304);
    }

    @Override
    public String getText() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.crosshairTarget instanceof EntityHitResult hit) {
            Entity entity = hit.getEntity();
            if (entity instanceof LivingEntity living) {
                return String.format("%s (%.1f/%.1f HP)", entity.getName().getString(), living.getHealth(), living.getMaxHealth());
            }
            return entity.getName().getString();
        }
        if (mc.crosshairTarget instanceof BlockHitResult hit && mc.world != null) {
            Block block = mc.world.getBlockState(hit.getBlockPos()).getBlock();
            return block.getName().getString();
        }
        return "";
    }
}
