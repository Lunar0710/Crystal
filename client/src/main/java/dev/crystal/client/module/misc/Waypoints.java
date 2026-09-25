package dev.crystal.client.module.misc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.hud.HudRenderable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** No dedicated GUI yet — press the "Add Waypoint" key to drop one at your feet; the HUD line tracks the nearest. */
public class Waypoints extends Module implements HudRenderable {

    public record Waypoint(String name, BlockPos pos) {}

    private final List<Waypoint> waypoints = new ArrayList<>();
    private int addKey = GLFW.GLFW_KEY_UNKNOWN;
    private boolean wasAddPressed = false;
    /** A waypoint where you last died, so the way back to your items is on screen. */
    private boolean deathPoint = true;
    private boolean wasDead = false;
    private static final String DEATH_NAME = "Tod";

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
        Minecraft mc = event.getClient();
        // The moment of dying: one waypoint there, replacing the previous one.
        boolean dead = mc.player != null && mc.player.isDeadOrDying();
        if (dead && !wasDead && deathPoint) {
            BlockPos pos = mc.player.blockPosition();
            waypoints.removeIf(wp -> wp.name().equals(DEATH_NAME));
            waypoints.add(new Waypoint(DEATH_NAME, pos));
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§cTodespunkt gesetzt: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
        }
        wasDead = dead;
        if (addKey == GLFW.GLFW_KEY_UNKNOWN || mc.player == null || mc.screen != null) {
            wasAddPressed = false;
            return;
        }

        boolean pressed = InputConstants.isKeyDown(mc.getWindow(), addKey);
        if (pressed && !wasAddPressed) {
            BlockPos pos = mc.player.blockPosition();
            waypoints.add(new Waypoint("Waypoint " + (waypoints.size() + 1), pos));
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§bWaypoint gesetzt: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), true);
        }
        wasAddPressed = pressed;
    }

    @Override
    public String getText() {
        var player = Minecraft.getInstance().player;
        if (player == null || waypoints.isEmpty()) return "Waypoints: none";

        Waypoint nearest = waypoints.get(0);
        double nearestDist = player.blockPosition().distSqr(nearest.pos());
        for (Waypoint wp : waypoints) {
            double dist = player.blockPosition().distSqr(wp.pos());
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
        return List.of(
                new KeybindSetting("Add Waypoint", () -> addKey, v -> addKey = v),
                new dev.crystal.client.module.BooleanSetting("Todespunkt setzen", () -> deathPoint, v -> deathPoint = v, true));
    }
}
