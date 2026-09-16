package dev.crystal.client.module.player;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.mixin.KeyMappingAccessor;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.KeybindSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Crystal+: one key throws an ender pearl from the hotbar.
 *
 * It presses the game's own keys, one per tick, exactly as if you pressed
 * them: the hotbar key of the pearl's slot, then "use item", then the hotbar
 * key of the slot you had. The game handles each press itself, so nothing is
 * sent that a normal key press wouldn't send.
 *
 * Off on Hypixel, whose rules count one key doing several actions as a macro.
 */
public class KeyPearls extends Module {

    private enum Step { IDLE, SELECT_PEARL, THROW, SELECT_BACK }

    private int hotkey = GLFW.GLFW_KEY_UNKNOWN;
    private boolean switchBack = true;

    private boolean wasPressed = false;
    private Step step = Step.IDLE;
    private int pearlSlot = -1;
    private int previousSlot = -1;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public KeyPearls() {
        super("KeyPearls", "Throws an ender pearl from the hotbar with one key (not on Hypixel)", ModuleCategory.PLAYER);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        wasPressed = false;
        step = Step.IDLE;
    }

    /**
     * Runs at the end of each tick; a key pressed here is handled at the start
     * of the next one, so the three presses land on three consecutive ticks.
     */
    private void onTick(TickEvent event) {
        Minecraft mc = event.getClient();
        LocalPlayer player = mc.player;
        if (player == null) {
            wasPressed = false;
            step = Step.IDLE;
            return;
        }

        switch (step) {
            case SELECT_PEARL -> {
                if (player.getInventory().getSelectedSlot() == pearlSlot) {
                    press(mc.options.keyUse);
                    step = Step.THROW;
                } else {
                    step = Step.IDLE; // the slot didn't change (menu opened, item moved)
                }
            }
            case THROW -> {
                if (switchBack && previousSlot != pearlSlot) {
                    press(mc.options.keyHotbarSlots[previousSlot]);
                    step = Step.SELECT_BACK;
                } else {
                    step = Step.IDLE;
                }
            }
            case SELECT_BACK -> step = Step.IDLE;
            case IDLE -> { }
        }

        // Only while playing: not in menus or chat, and not for a locked Crystal+ module.
        boolean pressed = hotkey != GLFW.GLFW_KEY_UNKNOWN && isEnabled() && mc.screen == null
                && InputConstants.isKeyDown(mc.getWindow(), hotkey);
        if (pressed && !wasPressed && step == Step.IDLE) start(mc, player);
        wasPressed = pressed;
    }

    private void start(Minecraft mc, LocalPlayer player) {
        if (onHypixel(mc)) {
            player.displayClientMessage(Component.literal("KeyPearls ist auf Hypixel nicht erlaubt"), true);
            return;
        }
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(Items.ENDER_PEARL)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            player.displayClientMessage(Component.literal("Keine Enderperle in der Hotbar"), true);
            return;
        }
        if (player.getCooldowns().isOnCooldown(player.getInventory().getItem(slot))) return;

        pearlSlot = slot;
        previousSlot = player.getInventory().getSelectedSlot();
        if (previousSlot == pearlSlot) {
            press(mc.options.keyUse);
            step = Step.THROW;
        } else {
            press(mc.options.keyHotbarSlots[pearlSlot]);
            step = Step.SELECT_PEARL;
        }
    }

    /** The automated world test: same as pressing the module's key. */
    public void pressForTest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && step == Step.IDLE) start(mc, mc.player);
    }

    /** One press of a game key, handled by the game on its next tick. */
    private static void press(KeyMapping key) {
        KeyMappingAccessor clicks = (KeyMappingAccessor) key;
        clicks.crystal$setClickCount(clicks.crystal$getClickCount() + 1);
    }

    private static boolean onHypixel(Minecraft mc) {
        var server = mc.getCurrentServer();
        if (server == null) return false;
        String host = server.ip.toLowerCase(Locale.ROOT).split(":")[0];
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new KeybindSetting("Taste", () -> hotkey, v -> hotkey = v),
                new BooleanSetting("Zurückwechseln", () -> switchBack, v -> switchBack = v, true)
        );
    }
}
