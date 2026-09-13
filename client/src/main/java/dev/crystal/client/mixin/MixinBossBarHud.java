package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.gui.GuiRender;
import dev.crystal.client.module.hud.BossBar;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(BossBarHud.class)
public class MixinBossBarHud {

    @Shadow @Final Map<UUID, ClientBossBar> bossBars;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null || bossBars.isEmpty()) return;

        BossBar module = CrystalClient.getInstance().getModuleManager().getModuleByName("BossBar")
                .filter(m -> m.isEnabled())
                .map(m -> (BossBar) m)
                .orElse(null);
        if (module == null) return;

        ci.cancel();

        var mc = net.minecraft.client.MinecraftClient.getInstance();
        int width = context.getScaledWindowWidth();
        int barWidth = 200;
        int y = 12;

        for (ClientBossBar bar : bossBars.values()) {
            int x = width / 2 - barWidth / 2;

            GuiRender.roundedRect(context, x, y, x + barWidth, y + 8, module.getBackgroundColor());
            int filled = Math.round(barWidth * Math.max(0f, Math.min(1f, bar.getPercent())));
            if (filled > 0) GuiRender.roundedRect(context, x, y, x + filled, y + 8, module.getBarColor());

            String label = bar.getName().getString();
            if (module.isShowPercent()) label += " " + Math.round(bar.getPercent() * 100) + "%";
            int textWidth = mc.textRenderer.getWidth(label);
            context.drawTextWithShadow(mc.textRenderer, label, width / 2 - textWidth / 2, y - 10, 0xFFFFFFFF);

            y += 20;
        }
    }
}
