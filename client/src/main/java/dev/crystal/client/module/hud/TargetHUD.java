package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.util.CombatTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.List;

public class TargetHUD extends HudModule {

    private boolean keepOpponent = true;

    public TargetHUD() {
        super("TargetHUD", "Shows health and info of your current target", 4, 160);
    }

    @Override
    public String getText() {
        LivingEntity target = null;
        if (Minecraft.getInstance().hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living) {
            target = living;
        } else if (keepOpponent) {
            // In a fight you look past your opponent all the time (strafing, a
            // pearl, a pot); keep showing them until the fight is over.
            target = CombatTracker.opponent();
        }
        if (target == null || !target.isAlive()) return "Target: none";
        return String.format("Target: %s (%.1f/%.1f HP)",
                target.getName().getString(), target.getHealth(), target.getMaxHealth());
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Gegner im Kampf halten", () -> keepOpponent, v -> keepOpponent = v, true));
    }
}
