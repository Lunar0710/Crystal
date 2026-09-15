package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.render.ScrollableTooltips;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;

/**
 * Translates the whole tooltip draw up/down by ScrollableTooltips' offset,
 * rather than reimplementing this method's internal layout — every component
 * type (text, item icon, image) still renders exactly like vanilla, just
 * shifted, and there's nothing here that can desync from vanilla's own
 * positioning math since that math never gets touched.
 */
@Mixin(GuiGraphics.class)
public class MixinDrawContext {

    @Inject(
        method = "setTooltipForNextFrameInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Z)V",
        at = @At("HEAD")
    )
    private void onDrawTooltipStart(Font textRenderer, List<ClientTooltipComponent> components, int x, int y,
                                     ClientTooltipPositioner positioner, Identifier texture, boolean bordered, CallbackInfo ci) {
        ScrollableTooltips module = scrollModule();
        if (module == null || module.getOffset() == 0f) return;

        GuiGraphics self = (GuiGraphics) (Object) this;
        self.pose().pushMatrix();
        self.pose().translate(0, module.getOffset());
    }

    @Inject(
        method = "setTooltipForNextFrameInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Z)V",
        at = @At("RETURN")
    )
    private void onDrawTooltipEnd(Font textRenderer, List<ClientTooltipComponent> components, int x, int y,
                                   ClientTooltipPositioner positioner, Identifier texture, boolean bordered, CallbackInfo ci) {
        ScrollableTooltips module = scrollModule();
        if (module == null || module.getOffset() == 0f) return;

        ((GuiGraphics) (Object) this).pose().popMatrix();
    }

    private ScrollableTooltips scrollModule() {
        if (CrystalClient.getInstance() == null) return null;
        return CrystalClient.getInstance().getModuleManager().getModuleByName("ScrollableTooltips")
                .filter(m -> m.isEnabled())
                .map(m -> (ScrollableTooltips) m)
                .orElse(null);
    }
}
