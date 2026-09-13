package dev.crystal.client.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Writes a vanilla option's backing field directly.
 *
 * SimpleOption.setValue() runs the value through callbacks.validate() first,
 * and gamma's validator clamps to 0..1 — so Lighting's setValue(16.0) silently
 * became 1.0 and the module looked like it did nothing. Setting the field is
 * how you get a true fullbright without touching the render path every frame.
 *
 * Only meaningful for numeric options whose clamp is what's in the way; the
 * original value must always be restored on disable so vanilla's own options
 * screen doesn't end up displaying something it can never produce itself.
 */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {

    @Accessor("value")
    void crystal$setRawValue(Object value);
}
