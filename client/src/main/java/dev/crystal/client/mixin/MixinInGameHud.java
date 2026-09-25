package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.gui.GuiRender;
import dev.crystal.client.module.hud.Scoreboard;
import dev.crystal.client.module.misc.ActionBarDisplay;
import dev.crystal.client.module.render.Crosshair;
import dev.crystal.client.module.render.Titles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26 {
/*import net.minecraft.client.gui.Hud;
*///?} else {
import net.minecraft.client.gui.Gui;
//?}
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;

// The in-game HUD; 26.1 split it out of Gui into Hud.
//? if >=26 {
/*@Mixin(Hud.class)
*///?} else {
@Mixin(Gui.class)
//?}
public class MixinInGameHud {

    @Shadow private Component overlayMessageString;
    @Shadow private int overlayMessageTime;
    @Shadow private Component title;
    @Shadow private Component subtitle;
    @Shadow private int titleTime;
    @Shadow private int titleFadeInTime;
    @Shadow private int titleStayTime;
    @Shadow private int titleFadeOutTime;

    @Unique private List<PlayerScoreEntry> crystal$sidebarEntries = List.of();
    @Unique private String crystal$sidebarTitle = "";
    @Unique private int crystal$sidebarWidth;
    @Unique private Objective crystal$sidebarObjective;
    @Unique private boolean crystal$sidebarShowScores;
    @Unique private long crystal$sidebarBuiltAt;

    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void onRenderOverlayMessage(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null || overlayMessageString == null || overlayMessageTime <= 0) return;

        ActionBarDisplay module = CrystalClient.getInstance().getModuleManager().getEnabled(ActionBarDisplay.class);
        if (module == null) return;

        ci.cancel();

        Font textRenderer = Minecraft.getInstance().font;

        // Same fade curve as vanilla: fully visible, then fading out over the last 20 ticks.
        int alpha = overlayMessageTime > 20 ? 255 : Math.max(0, overlayMessageTime * 255 / 20);

        int width = context.guiWidth();
        int height = context.guiHeight();
        int textWidth = textRenderer.width(overlayMessageString);
        int x = (width - textWidth) / 2;
        int y = height - 68;

        if (module.isShowBackground()) {
            int bgAlpha = (alpha / 2) << 24;
            context.fill(x - 4, y - 3, x + textWidth + 4, y + 10, bgAlpha | 0x000000);
        }

        int color = (module.getTextColor() & 0x00FFFFFF) | (alpha << 24);
        context.drawString(textRenderer, overlayMessageString, x, y, color, true);
    }

    @Inject(method = "renderTitle", at = @At("HEAD"), cancellable = true)
    private void onRenderTitleAndSubtitle(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null || title == null || titleTime <= 0) return;

        Titles module = CrystalClient.getInstance().getModuleManager().getEnabled(Titles.class);
        if (module == null) return;

        ci.cancel();

        Font textRenderer = Minecraft.getInstance().font;
        float remaining = titleTime - tickCounter.getGameTimeDeltaPartialTick(false);

        int alpha;
        if (titleTime > titleFadeOutTime + titleStayTime) {
            float fadeIn = 1f - (remaining - (titleFadeOutTime + titleStayTime)) / titleFadeInTime;
            alpha = Math.round(Math.max(0f, Math.min(1f, fadeIn)) * 255f);
        } else if (remaining <= titleFadeOutTime) {
            alpha = Math.round(Math.max(0f, remaining / Math.max(1, titleFadeOutTime)) * 255f);
        } else {
            alpha = 255;
        }

        float scale = module.getScale();
        int width = context.guiWidth();
        int height = context.guiHeight();

        context.pose().pushMatrix();
        context.pose().translate(width / 2f, height / 2f);
        context.pose().scale(scale, scale);

        int titleColor = (module.getTitleColor() & 0x00FFFFFF) | (alpha << 24);
        context.drawCenteredString(textRenderer, title, 0, -20, titleColor);

        if (subtitle != null) {
            int subtitleColor = (module.getSubtitleColor() & 0x00FFFFFF) | (alpha << 24);
            context.drawCenteredString(textRenderer, subtitle, 0, 5, subtitleColor);
        }

        context.pose().popMatrix();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;

        Crosshair module = CrystalClient.getInstance().getModuleManager().getEnabled(Crosshair.class);
        if (module == null) return;

        ci.cancel();

        module.draw(context, context.guiWidth() / 2, context.guiHeight() / 2);
    }

    @Inject(
        method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/scores/Objective;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void onRenderScoreboardSidebar(GuiGraphics context, Objective objective, CallbackInfo ci) {
        if (CrystalClient.getInstance() == null) return;

        Scoreboard module = CrystalClient.getInstance().getModuleManager().getEnabled(Scoreboard.class);
        if (module == null) return;

        ci.cancel();

        Font textRenderer = Minecraft.getInstance().font;

        // Copy, filter, sort and measure the sidebar at most every 50 ms (one
        // server tick) instead of every frame. On servers with a full
        // scoreboard that was noticeable garbage at high frame rates.
        long now = System.currentTimeMillis();
        boolean showScores = module.isShowScores();
        if (objective != crystal$sidebarObjective || showScores != crystal$sidebarShowScores || now - crystal$sidebarBuiltAt >= 50) {
            List<PlayerScoreEntry> built = new ArrayList<>(objective.getScoreboard().listPlayerScores(objective));
            built.removeIf(PlayerScoreEntry::isHidden);
            built.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
            if (built.size() > 15) built = new ArrayList<>(built.subList(0, 15));

            String builtTitle = objective.getDisplayName().getString();
            int builtWidth = textRenderer.width(builtTitle) + 8;
            for (PlayerScoreEntry entry : built) {
                int lineWidth = textRenderer.width(entry.ownerName());
                if (showScores) lineWidth += textRenderer.width(" " + entry.value()) + 4;
                builtWidth = Math.max(builtWidth, lineWidth + 8);
            }

            crystal$sidebarEntries = built;
            crystal$sidebarTitle = builtTitle;
            crystal$sidebarWidth = builtWidth;
            crystal$sidebarObjective = objective;
            crystal$sidebarShowScores = showScores;
            crystal$sidebarBuiltAt = now;
        }

        List<PlayerScoreEntry> entries = crystal$sidebarEntries;
        String title = crystal$sidebarTitle;
        int width = crystal$sidebarWidth;
        int lineHeight = 9;

        int screenHeight = context.guiHeight();
        int panelHeight = lineHeight * (entries.size() + 1) + 4;
        int x = context.guiWidth() - width - 4;
        int y = Math.max(4, (screenHeight - panelHeight) / 3);

        GuiRender.roundedRect(context, x, y, x + width, y + panelHeight, module.getBackgroundColor());
        context.drawCenteredString(textRenderer, title, x + width / 2, y + 2, module.getTitleColor());

        int rowY = y + lineHeight + 4;
        for (PlayerScoreEntry entry : entries) {
            context.drawString(textRenderer, entry.ownerName(), x + 4, rowY, module.getTextColor());
            if (module.isShowScores()) {
                String value = String.valueOf(entry.value());
                context.drawString(textRenderer, value, x + width - textRenderer.width(value) - 4, rowY, module.getTitleColor());
            }
            rowY += lineHeight;
        }
    }
}
