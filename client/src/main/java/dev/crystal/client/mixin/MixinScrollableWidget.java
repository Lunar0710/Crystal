package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.misc.SmoothScroll;
import dev.crystal.client.util.SmoothScrollable;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smooth scrolling for every vanilla scroll list. The wheel moves a target;
 * {@link #crystal$animate()} (called each frame from MixinClickableWidget)
 * glides the real scroll position towards it. Anything else that sets the
 * position (dragging the scrollbar, jumping to a selected entry) cancels the
 * glide so it never fights the game.
 */
@Mixin(ScrollableWidget.class)
public abstract class MixinScrollableWidget implements SmoothScrollable {

    @Shadow public abstract double getScrollY();
    @Shadow public abstract void setScrollY(double scrollY);
    @Shadow public abstract int getMaxScrollY();
    @Shadow protected abstract double getDeltaYPerScroll();
    @Shadow protected abstract boolean overflows();

    @Unique private double crystal$target;
    @Unique private boolean crystal$gliding;
    @Unique private boolean crystal$internalSet;
    @Unique private long crystal$lastFrame;

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void crystal$onScroll(double mouseX, double mouseY, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        SmoothScroll module = crystal$module();
        if (module == null || !overflows() || !((ScrollableWidget) (Object) this).visible) return;
        if (!crystal$gliding) {
            crystal$target = getScrollY();
            crystal$lastFrame = System.nanoTime();
        }
        crystal$target = MathHelper.clamp(crystal$target - vertical * getDeltaYPerScroll() * module.getDistance(), 0, getMaxScrollY());
        crystal$gliding = true;
        cir.setReturnValue(true);
    }

    @Inject(method = "setScrollY", at = @At("TAIL"))
    private void crystal$onSetScroll(double scrollY, CallbackInfo ci) {
        if (!crystal$internalSet) crystal$gliding = false;
    }

    @Override
    public void crystal$animate() {
        if (!crystal$gliding) return;
        SmoothScroll module = crystal$module();
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - crystal$lastFrame) / 1_000_000_000f);
        crystal$lastFrame = now;

        double current = getScrollY();
        double next = module == null ? crystal$target
                : current + (crystal$target - current) * Math.min(1f, dt * module.getSpeed());
        if (Math.abs(crystal$target - next) < 0.5) {
            next = crystal$target;
            crystal$gliding = false;
        }
        crystal$internalSet = true;
        setScrollY(next);
        crystal$internalSet = false;
    }

    @Unique
    private static SmoothScroll crystal$module() {
        CrystalClient client = CrystalClient.getInstance();
        return client == null ? null : client.getModuleManager().getEnabled(SmoothScroll.class);
    }
}
