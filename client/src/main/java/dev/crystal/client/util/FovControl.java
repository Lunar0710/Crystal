package dev.crystal.client.util;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.FOVChanger;
import dev.crystal.client.module.render.Zoom;

/**
 * FOVChanger and Zoom applied to the field of view the game computed. Called
 * once per frame from whichever class computes the FOV on this version.
 */
public final class FovControl {

    private static float zoomMultiplier = 1f;

    private FovControl() {}

    public static float adjust(float fov) {
        CrystalClient client = CrystalClient.getInstance();
        if (client == null) return fov;

        FOVChanger fovChanger = client.getModuleManager().getEnabled(FOVChanger.class);
        if (fovChanger != null) fov = fovChanger.getFov();

        Zoom zoom = client.getModuleManager().get(Zoom.class);
        if (zoom != null) {
            float target = zoom.isEnabled() ? (float) (1.0 / zoom.getFactor()) : 1f;
            zoomMultiplier = zoom.isSmooth()
                    ? zoomMultiplier + (target - zoomMultiplier) * 0.2f
                    : target;
            fov *= zoomMultiplier;
        }
        return fov;
    }
}
