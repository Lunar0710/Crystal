package dev.crystal.client.module.render;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import dev.crystal.client.util.OcclusionCuller;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Nexora's own performance culling: mobs, players and block entities
 * (chests, signs, banners...) that are completely behind walls are not drawn.
 * See {@link OcclusionCuller} for how it decides. On by default.
 */
public class SmartCulling extends Module {

    private boolean entities = true;
    private boolean blockEntities = true;
    private float maxDistance = 128f;

    public SmartCulling() {
        super("SmartCulling", "Skips drawing mobs and block entities hidden behind walls", ModuleCategory.RENDER);
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, e -> tick(e.getClient()));
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        OcclusionCuller.setEnabled(true);
    }

    @Override
    public void onDisable() {
        OcclusionCuller.setEnabled(false);
    }

    private void tick(Minecraft mc) {
        if (!isEnabled() || mc.gameRenderer == null) return;
        OcclusionCuller.setMaxDistance(maxDistance);
        //? if >=26 {
        /*OcclusionCuller.setCamera(mc.gameRenderer.mainCamera().position());
        *///?} else if >=1.21.9 {
        OcclusionCuller.setCamera(mc.gameRenderer.getMainCamera().position());
        //?} else {
        /*OcclusionCuller.setCamera(mc.gameRenderer.getMainCamera().getPosition());
        *///?}
    }

    public boolean cullsEntities() { return isEnabled() && entities; }
    public boolean cullsBlockEntities() { return isEnabled() && blockEntities; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new BooleanSetting("Mobs und Spieler", () -> entities, v -> entities = v, true),
                new BooleanSetting("Truhen, Schilder und Co.", () -> blockEntities, v -> blockEntities = v, true),
                new SliderSetting("Max. Entfernung", () -> maxDistance, v -> maxDistance = v, 16f, 256f, 8f, 0)
        );
    }
}
