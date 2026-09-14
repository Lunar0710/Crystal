package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.gui.GuiRender;
import dev.crystal.client.module.hud.Scoreboard;
import dev.crystal.client.module.misc.ActionBarDisplay;
import dev.crystal.client.module.render.Crosshair;
import dev.crystal.client.module.render.Titles;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Mixin(InGameHud.class)
public class MixinInGameHud {

    @Shadow private Text overlayMessage;
    @Shadow private int overlayRemaining;
    @Shadow private Text title;
    @Shadow private Text subtitle;
    @Shadow private int titleRemainTicks;
    @Shadow private int titleFadeInTicks;
    @Shadow private int titleStayTicks;
    @Shadow private int titleFadeOutTicks;

    @Unique private List<ScoreboardEntry> crystal$sidebarEntries = List.of();
    @Unique private String crystal$sidebarTitle = "";
    @Unique private int crystal$sidebarWidth;
    @Unique private ScoreboardObjective crystal$sidebarObjective;
    @Unique private boolean crystal$sidebarShowScores;
    @Unique private long crystal$sidebarBuiltAt;

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void onRenderOverlayMessage(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null || overlayMessage == null || overlayRemaining <= 0) return;

        ActionBarDisplay module = CrystalClient.getInstance().getModuleManager().getModuleByName("ActionBar")
                .filter(m -> m.isEnabled())
                .map(m -> (ActionBarDisplay) m)
                .orElse(null);
        if (module == null) return;

        ci.cancel();

        InGameHud self = (InGameHud) (Object) this;
        TextRenderer textRenderer = self.getTextRenderer();

        // Same fade curve as vanilla: fully visible, then fading out over the last 20 ticks.
        int alpha = overlayRemaining > 20 ? 255 : Math.max(0, overlayRemaining * 255 / 20);

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        int textWidth = textRenderer.getWidth(overlayMessage);
        int x = (width - textWidth) / 2;
        int y = height - 68;

        if (module.isShowBackground()) {
            int bgAlpha = (alpha / 2) << 24;
            context.fill(x - 4, y - 3, x + textWidth + 4, y + 10, bgAlpha | 0x000000);
        }

        int color = (module.getTextColor() & 0x00FFFFFF) | (alpha << 24);
        context.drawText(textRenderer, overlayMessage, x, y, color, true);
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
    private void onRenderTitleAndSubtitle(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null || title == null || titleRemainTicks <= 0) return;

        Titles module = CrystalClient.getInstance().getModuleManager().getModuleByName("Titles")
                .filter(m -> m.isEnabled())
                .map(m -> (Titles) m)
                .orElse(null);
        if (module == null) return;

        ci.cancel();

        InGameHud self = (InGameHud) (Object) this;
        TextRenderer textRenderer = self.getTextRenderer();
        float remaining = titleRemainTicks - tickCounter.getTickProgress(false);

        int alpha;
        if (titleRemainTicks > titleFadeOutTicks + titleStayTicks) {
            float fadeIn = 1f - (remaining - (titleFadeOutTicks + titleStayTicks)) / titleFadeInTicks;
            alpha = Math.round(Math.max(0f, Math.min(1f, fadeIn)) * 255f);
        } else if (remaining <= titleFadeOutTicks) {
            alpha = Math.round(Math.max(0f, remaining / Math.max(1, titleFadeOutTicks)) * 255f);
        } else {
            alpha = 255;
        }

        float scale = module.getScale();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(width / 2f, height / 2f);
        context.getMatrices().scale(scale, scale);

        int titleColor = (module.getTitleColor() & 0x00FFFFFF) | (alpha << 24);
        context.drawCenteredTextWithShadow(textRenderer, title, 0, -20, titleColor);

        if (subtitle != null) {
            int subtitleColor = (module.getSubtitleColor() & 0x00FFFFFF) | (alpha << 24);
            context.drawCenteredTextWithShadow(textRenderer, subtitle, 0, 5, subtitleColor);
        }

        context.getMatrices().popMatrix();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;

        Crosshair module = CrystalClient.getInstance().getModuleManager().getModuleByName("Crosshair")
                .filter(m -> m.isEnabled())
                .map(m -> (Crosshair) m)
                .orElse(null);
        if (module == null) return;

        ci.cancel();

        int cx = context.getScaledWindowWidth() / 2;
        int cy = context.getScaledWindowHeight() / 2;
        int size = Math.round(module.getSize());
        int thickness = Math.round(module.getThickness());
        int color = module.getColor();

        if (module.isDot()) {
            context.fill(cx - thickness, cy - thickness, cx + thickness, cy + thickness, color);
            return;
        }

        context.fill(cx - size, cy - thickness, cx + size, cy + thickness, color);
        context.fill(cx - thickness, cy - size, cx + thickness, cy + size, color);
    }

    @Inject(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void onRenderScoreboardSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;

        Scoreboard module = CrystalClient.getInstance().getModuleManager().getModuleByName("Scoreboard")
                .filter(m -> m.isEnabled())
                .map(m -> (Scoreboard) m)
                .orElse(null);
        if (module == null) return;

        ci.cancel();

        InGameHud self = (InGameHud) (Object) this;
        TextRenderer textRenderer = self.getTextRenderer();

        // Copy, filter, sort and measure the sidebar at most every 50 ms (one
        // server tick) instead of every frame. On servers with a full
        // scoreboard that was noticeable garbage at high frame rates.
        long now = System.currentTimeMillis();
        boolean showScores = module.isShowScores();
        if (objective != crystal$sidebarObjective || showScores != crystal$sidebarShowScores || now - crystal$sidebarBuiltAt >= 50) {
            List<ScoreboardEntry> built = new ArrayList<>(objective.getScoreboard().getScoreboardEntries(objective));
            built.removeIf(ScoreboardEntry::hidden);
            built.sort(Comparator.comparingInt(ScoreboardEntry::value).reversed());
            if (built.size() > 15) built = new ArrayList<>(built.subList(0, 15));

            String builtTitle = objective.getDisplayName().getString();
            int builtWidth = textRenderer.getWidth(builtTitle) + 8;
            for (ScoreboardEntry entry : built) {
                int lineWidth = textRenderer.getWidth(entry.name());
                if (showScores) lineWidth += textRenderer.getWidth(" " + entry.value()) + 4;
                builtWidth = Math.max(builtWidth, lineWidth + 8);
            }

            crystal$sidebarEntries = built;
            crystal$sidebarTitle = builtTitle;
            crystal$sidebarWidth = builtWidth;
            crystal$sidebarObjective = objective;
            crystal$sidebarShowScores = showScores;
            crystal$sidebarBuiltAt = now;
        }

        List<ScoreboardEntry> entries = crystal$sidebarEntries;
        String title = crystal$sidebarTitle;
        int width = crystal$sidebarWidth;
        int lineHeight = 9;

        int screenHeight = context.getScaledWindowHeight();
        int panelHeight = lineHeight * (entries.size() + 1) + 4;
        int x = context.getScaledWindowWidth() - width - 4;
        int y = Math.max(4, (screenHeight - panelHeight) / 3);

        GuiRender.roundedRect(context, x, y, x + width, y + panelHeight, module.getBackgroundColor());
        context.drawCenteredTextWithShadow(textRenderer, title, x + width / 2, y + 2, module.getTitleColor());

        int rowY = y + lineHeight + 4;
        for (ScoreboardEntry entry : entries) {
            context.drawTextWithShadow(textRenderer, entry.name(), x + 4, rowY, module.getTextColor());
            if (module.isShowScores()) {
                String value = String.valueOf(entry.value());
                context.drawTextWithShadow(textRenderer, value, x + width - textRenderer.getWidth(value) - 4, rowY, module.getTitleColor());
            }
            rowY += lineHeight;
        }
    }
}
