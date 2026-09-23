package dev.crystal.client.mixin;

import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The slider's position, 0 to 1, which Nexora needs to draw its own track. */
@Mixin(AbstractSliderButton.class)
public interface SliderAccessor {
    @Accessor("value")
    double crystal$value();
}
