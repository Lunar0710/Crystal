package dev.crystal.client.module.player;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.hit.BlockHitResult;

import java.util.List;
import java.util.function.Consumer;

/** Switches to whichever hotbar slot mines the targeted block fastest, the moment mining starts. */
public class AutoTool extends Module {

    private boolean onlyWhenHoldingTool = false;
    private boolean switchBack = false;
    private int previousSlot = -1;
    private boolean wasAttackPressed = false;
    private final Consumer<TickEvent> tickListener = this::onTick;

    public AutoTool() {
        super("AutoTool", "Automatically switches to the best tool for the block you're mining", ModuleCategory.PLAYER);
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
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;

        boolean pressed = mc.options.attackKey.isPressed();
        boolean justPressed = pressed && !wasAttackPressed;
        wasAttackPressed = pressed;

        // Restore the slot we came from once mining stops, when asked to.
        if (!pressed && switchBack && previousSlot >= 0) {
            player.getInventory().setSelectedSlot(previousSlot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            previousSlot = -1;
        }

        if (!justPressed || !(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        if (onlyWhenHoldingTool && player.getMainHandStack().getMiningSpeedMultiplier(
                mc.world.getBlockState(hit.getBlockPos())) <= 1.0f) return;

        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        var hotbar = player.getInventory().getMainStacks();

        int bestSlot = player.getInventory().getSelectedSlot();
        float bestSpeed = hotbar.get(bestSlot).getMiningSpeedMultiplier(state);

        // Only the 9 hotbar slots are switchable without opening the inventory.
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = hotbar.get(slot);
            if (stack.isEmpty()) continue;

            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }

        if (bestSlot != player.getInventory().getSelectedSlot()) {
            if (switchBack && previousSlot < 0) previousSlot = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(bestSlot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(bestSlot));
        }
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new BooleanSetting("Only If Tool Helps", () -> onlyWhenHoldingTool, v -> onlyWhenHoldingTool = v, false),
                new BooleanSetting("Switch Back", () -> switchBack, v -> switchBack = v, false)
        );
    }
}
