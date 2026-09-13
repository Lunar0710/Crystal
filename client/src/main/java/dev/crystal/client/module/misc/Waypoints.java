package dev.crystal.client.module.misc;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.hud.HudRenderable;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** No dedicated GUI yet — press the "Add Waypoint" key to drop one at your feet; the HUD line tracks the nearest. */
public class Waypoints extends Module implements HudRenderable {

    public record Waypoint(String name, BlockPos pos) {}

    private final List<Waypoint> waypoints = new ArrayList<>();
    private int addKey = GLFW.GLFW_KEY_UNKNOWN;
    private boolean wasAddPressed = false;

    private int x = 4, y = 280;
    private final Consumer<TickEvent> tickListener = this::onTick;

    public Waypoints() {
        super("Waypoints", "Set and navigate to manually-placed waypoints", ModuleCategory.MISC);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
    }

    private void onTick(TickEvent event) {
        MinecraftClient mc = event.getClient();
        if (addKey == GLFW.GLFW_KEY_UNKNOWN || mc.player == null || mc.currentScreen != null) {
            wasAddPressed = false;
            return;
        }

        boolean pressed = InputUtil.isKeyPressed(mc.getWindow(), addKey);
        if (pressed && !wasAddPressed) {
            BlockPos pos = mc.player.getBlockPos();
            waypoints.add(new Waypoint("Waypoint " + (waypoints.size() + 1), pos));
            mc.player.sendMessage(net.minecraft.text.Text.literal(
                    "§bWaypoint gesetzt: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), true);
        }
        wasAddPressed = pressed;
    }

    @Override
    public String getText() {
        var player = MinecraftClient.getInstance().player;
        if (player == null || waypoints.isEmpty()) return "Waypoints: none";

        Waypoint nearest = waypoints.get(0);
        double nearestDist = player.getBlockPos().getSquaredDistance(nearest.pos());
        for (Waypoint wp : waypoints) {
            double dist = player.getBlockPos().getSquaredDistance(wp.pos());
            if (dist < nearestDist) {
                nearest = wp;
                nearestDist = dist;
            }
        }
        return String.format("%s: %.0fm", nearest.name(), Math.sqrt(nearestDist));
    }

    @Override
    public int getX() { return x; }
    @Override
    public int getY() { return y; }
    public void setPosition(int x, int y) { this.x = x; this.y = y; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(new KeybindSetting("Add Waypoint", () -> addKey, v -> addKey = v));
    }
}
