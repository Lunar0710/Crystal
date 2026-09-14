package dev.crystal.client.module.render;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.mixin.GameRendererAccessor;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Consumer;

/**
 * Colour grading for the world: saturation, hue, brightness and contrast.
 *
 * Runs as a post effect (assets/crystal/post_effect/color_grade.json) after
 * the world is drawn, so the HUD and menus keep their normal colours. The
 * slider values reach the shader every frame through MixinPostEffectPass.
 *
 * The module used to have only an "Amount" slider that nothing read, which is
 * why changing it did nothing.
 */
public class ColorSaturation extends Module {

    public static final Identifier EFFECT = Identifier.of("crystal", "color_grade");

    private float saturation = 1.0f;
    private float hue = 0f;
    private float brightness = 1.0f;
    private float contrast = 1.0f;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public ColorSaturation() {
        super("ColorSaturation", "Changes the world's saturation, hue, brightness and contrast", ModuleCategory.RENDER);
    }

    public float getSaturation() { return saturation; }
    public float getHue() { return hue; }
    public float getBrightness() { return brightness; }
    public float getContrast() { return contrast; }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer != null && EFFECT.equals(mc.gameRenderer.getPostProcessorId())) {
            mc.gameRenderer.clearPostProcessor();
        }
    }

    /**
     * Re-applied every tick because vanilla drops the post effect when the camera
     * entity changes (joining a world, respawning). A vanilla effect that is
     * already active, like the creeper spectator view, is left alone.
     */
    private void onTick(TickEvent event) {
        MinecraftClient mc = event.getClient();
        if (mc.gameRenderer == null || mc.world == null) return;
        if (mc.gameRenderer.getPostProcessorId() == null) {
            ((GameRendererAccessor) mc.gameRenderer).crystal$setPostProcessor(EFFECT);
        }
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new SliderSetting("Saturation", () -> saturation, v -> saturation = v, 0f, 2f, 0.05f, 2),
                new SliderSetting("Hue", () -> hue, v -> hue = v, -180f, 180f, 5f, 0),
                new SliderSetting("Brightness", () -> brightness, v -> brightness = v, 0.5f, 1.5f, 0.05f, 2),
                new SliderSetting("Contrast", () -> contrast, v -> contrast = v, 0.5f, 1.5f, 0.05f, 2)
        );
    }
}
