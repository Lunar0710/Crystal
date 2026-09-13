package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.ScrollableTooltips;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Translates the whole tooltip draw up/down by ScrollableTooltips' offset,
 * rather than reimplementing this method's internal layout — every component
 * type (text, item icon, image) still renders exactly like vanilla, just
 * shifted, and there's nothing here that can desync from vanilla's own
 * positioning math since that math never gets touched.
 */
@Mixin(DrawContext.class)
public class MixinDrawContext {

    @Inject(
        method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/client/gui/tooltip/TooltipPositioner;Lnet/minecraft/util/Identifier;Z)V",
        at = @At("HEAD")
    )
    private void onDrawTooltipStart(TextRenderer textRenderer, List<TooltipComponent> components, int x, int y,
                                     TooltipPositioner positioner, Identifier texture, boolean bordered, CallbackInfo ci) {
        ScrollableTooltips module = scrollModule();
        if (module == null || module.getOffset() == 0f) return;

        DrawContext self = (DrawContext) (Object) this;
        self.getMatrices().pushMatrix();
        self.getMatrices().translate(0, module.getOffset());
    }

    @Inject(
        method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;IILnet/minecraft/client/gui/tooltip/TooltipPositioner;Lnet/minecraft/util/Identifier;Z)V",
        at = @At("RETURN")
    )
    private void onDrawTooltipEnd(TextRenderer textRenderer, List<TooltipComponent> components, int x, int y,
                                   TooltipPositioner positioner, Identifier texture, boolean bordered, CallbackInfo ci) {
        ScrollableTooltips module = scrollModule();
        if (module == null || module.getOffset() == 0f) return;

        ((DrawContext) (Object) this).getMatrices().popMatrix();
    }

    private ScrollableTooltips scrollModule() {
        if (CrystalClient.getInstance() == null) return null;
        return CrystalClient.getInstance().getModuleManager().getModuleByName("ScrollableTooltips")
                .filter(m -> m.isEnabled())
                .map(m -> (ScrollableTooltips) m)
                .orElse(null);
    }
}
