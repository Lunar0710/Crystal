package dev.crystal.client.module.render;

import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;

import java.util.List;

public class FOVChanger extends Module {
    private float fov = 90f;

    public FOVChanger() {
        super("FOVChanger", "Overrides your field of view independently of the vanilla slider", ModuleCategory.RENDER);
    }

    public float getFov() { return fov; }
    public void setFov(float f) { this.fov = Math.max(30f, Math.min(110f, f)); }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new SliderSetting("FOV", () -> fov, v -> fov = v, 30f, 110f, 5f));
    }
}
