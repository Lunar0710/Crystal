package dev.crystal.client.util;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.mixin.GameRendererAccessor;
import dev.crystal.client.module.render.ColorSaturation;
import dev.crystal.client.module.render.MotionBlur;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Minecraft runs one post effect at a time, so Crystal's world effects are
 * chosen here together: colour grading, motion blur, or both chained in one
 * pipeline. Checked every tick, because vanilla drops the post effect when the
 * camera entity changes (joining a world, respawning). A vanilla effect that is
 * active (like the creeper spectator view) is left alone.
 */
public final class PostEffects {

    public static final Identifier COLOR_GRADE = Identifier.of(CrystalClient.MOD_ID, "color_grade");
    public static final Identifier MOTION_BLUR = Identifier.of(CrystalClient.MOD_ID, "motion_blur");
    public static final Identifier BOTH = Identifier.of(CrystalClient.MOD_ID, "color_grade_motion_blur");

    private PostEffects() {}

    public static void register() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, e -> tick(e.getClient()));
    }

    private static void tick(MinecraftClient mc) {
        if (mc.gameRenderer == null || mc.world == null) return;
        var modules = CrystalClient.getInstance().getModuleManager();
        boolean grade = modules.getEnabled(ColorSaturation.class) != null;
        boolean blur = modules.getEnabled(MotionBlur.class) != null;
        Identifier wanted = grade && blur ? BOTH : grade ? COLOR_GRADE : blur ? MOTION_BLUR : null;

        Identifier current = mc.gameRenderer.getPostProcessorId();
        boolean currentIsOurs = current != null && current.getNamespace().equals(CrystalClient.MOD_ID);
        if (current != null && !currentIsOurs) return;
        if (wanted == null) {
            if (currentIsOurs) mc.gameRenderer.clearPostProcessor();
        } else if (!wanted.equals(current)) {
            ((GameRendererAccessor) mc.gameRenderer).crystal$setPostProcessor(wanted);
        }
    }
}
