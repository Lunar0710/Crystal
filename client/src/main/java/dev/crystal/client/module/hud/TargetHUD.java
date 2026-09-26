package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.util.CombatTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.List;

/**
 * A card for whoever you are fighting: name, a health bar that turns from
 * green to red, their health and armour against yours, and the distance.
 * The fight's opponent comes first; otherwise the entity under the crosshair.
 * It stays a few seconds after you look away, so a strafe doesn't make it
 * flicker. Drawn by CrystalHUD.
 */
public class TargetHUD extends HudModule {

    private static final long KEEP_MS = 3000;

    private boolean onlyPlayers = true;
    private boolean compare = true;
    private LivingEntity last;
    private long lastSeen;

    public TargetHUD() {
        super("TargetHUD", "A card with your target's health, armour and distance", 4, 160);
    }

    public boolean compare() { return compare; }

    /** The target to show, or null. */
    public LivingEntity target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        LivingEntity now = CombatTracker.opponent();
        if (now == null && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) now = living;
        if (now != null && onlyPlayers && !(now instanceof net.minecraft.world.entity.player.Player)) now = null;
        long t = System.currentTimeMillis();
        if (now != null && now.isAlive()) { last = now; lastSeen = t; return now; }
        if (last != null && (t - lastSeen > KEEP_MS || last.isRemoved())) last = null;
        return last;
    }

    /** Only used by the HUD editor as a label; the card is drawn by CrystalHUD. */
    @Override
    public String getText() {
        return "TargetHUD";
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new BooleanSetting("Nur Spieler", () -> onlyPlayers, v -> onlyPlayers = v, true),
                new BooleanSetting("Mit dir vergleichen", () -> compare, v -> compare = v, true));
    }
}
