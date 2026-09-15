package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.gui.GuiRender;
import dev.crystal.client.module.misc.TabEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerListHud {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(GuiGraphics context, int screenWidth, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;

        TabEditor module = CrystalClient.getInstance().getModuleManager().getModuleByName("TabEditor")
                .filter(m -> m.isEnabled())
                .map(m -> (TabEditor) m)
                .orElse(null);
        if (module == null) return;

        ci.cancel();

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        PlayerTabOverlay self = (PlayerTabOverlay) (Object) this;

        List<PlayerInfo> entries = new ArrayList<>(mc.getConnection().getListedOnlinePlayers());
        entries.sort(Comparator.comparing(e -> e.getProfile().name(), String.CASE_INSENSITIVE_ORDER));

        int columnWidth = module.getColumnWidth();
        int columns = Math.max(1, Math.min(4, screenWidth / (columnWidth + 10)));
        int rows = (int) Math.ceil(entries.size() / (double) columns);
        int rowHeight = 10;

        int totalWidth = columns * columnWidth + (columns - 1) * 10;
        int x0 = (screenWidth - totalWidth) / 2;
        int y0 = 10;

        GuiRender.roundedRect(context, x0 - 4, y0 - 4, x0 + totalWidth + 4, y0 + rows * rowHeight + 4, module.getBackgroundColor());

        for (int i = 0; i < entries.size(); i++) {
            PlayerInfo entry = entries.get(i);
            int col = i / rows;
            int row = i % rows;
            int x = x0 + col * (columnWidth + 10);
            int y = y0 + row * rowHeight;

            String name = self.getNameForDisplay(entry).getString();
            context.drawString(mc.font, name, x, y, module.getTextColor());

            if (module.isShowPing()) {
                String ping = entry.getLatency() + "ms";
                int pingWidth = mc.font.width(ping);
                context.drawString(mc.font, ping, x + columnWidth - pingWidth, y, 0xFF808080);
            }
        }
    }
}
