package dev.crystal.client.module.player;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Where you died, as a chat line the moment it happens: coordinates and
 * dimension, and a click copies them. Your items are still lying there.
 */
public class DeathCoords extends Module {

    private boolean reported = false;
    private final Consumer<TickEvent> tickListener = this::onTick;

    public DeathCoords() {
        super("DeathCoords", "Tells you where you died, click to copy the coordinates", ModuleCategory.PLAYER);
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
        if (mc.player == null || mc.level == null) { reported = false; return; }
        if (!mc.player.isDeadOrDying()) { reported = false; return; }
        if (reported) return;
        reported = true;
        int x = mc.player.getBlockX(), y = mc.player.getBlockY(), z = mc.player.getBlockZ();
        String coords = x + " " + y + " " + z;
        String dimension = mc.level.dimension() == net.minecraft.world.level.Level.NETHER ? "Nether"
                : mc.level.dimension() == net.minecraft.world.level.Level.END ? "End" : "Oberwelt";
        Component line = Component.literal("[Nexora] ").withStyle(ChatFormatting.RED)
                .append(Component.literal("Gestorben bei ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(coords).withStyle(s -> s.withColor(ChatFormatting.WHITE).withUnderlined(true)
                        //? if >=1.21.5 {
                        .withClickEvent(new ClickEvent.CopyToClipboard(coords))))
                        //?} else {
                        /*.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, coords))))
                        *///?}
                .append(Component.literal(" (" + dimension + ")").withStyle(ChatFormatting.GRAY));
        mc.player.displayClientMessage(line, false);
    }
}
