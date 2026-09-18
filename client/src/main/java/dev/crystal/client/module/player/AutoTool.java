package dev.crystal.client.module.player;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import dev.crystal.client.compat.InventoryCompat;

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
        Minecraft mc = event.getClient();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        boolean pressed = mc.options.keyAttack.isDown();
        boolean justPressed = pressed && !wasAttackPressed;
        wasAttackPressed = pressed;

        // Restore the slot we came from once mining stops, when asked to.
        if (!pressed && switchBack && previousSlot >= 0) {
            InventoryCompat.setSelectedSlot(player, previousSlot);
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(previousSlot));
            previousSlot = -1;
        }

        if (!justPressed || !(mc.hitResult instanceof BlockHitResult hit)) return;
        if (onlyWhenHoldingTool && player.getMainHandItem().getDestroySpeed(
                mc.level.getBlockState(hit.getBlockPos())) <= 1.0f) return;

        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        var hotbar = InventoryCompat.nonEquipmentItems(player);

        int bestSlot = InventoryCompat.selectedSlot(player);
        float bestSpeed = hotbar.get(bestSlot).getDestroySpeed(state);

        // Only the 9 hotbar slots are switchable without opening the inventory.
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = hotbar.get(slot);
            if (stack.isEmpty()) continue;

            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }

        if (bestSlot != InventoryCompat.selectedSlot(player)) {
            if (switchBack && previousSlot < 0) previousSlot = InventoryCompat.selectedSlot(player);
            InventoryCompat.setSelectedSlot(player, bestSlot);
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(bestSlot));
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
