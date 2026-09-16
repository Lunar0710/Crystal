package dev.crystal.client.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Presses of a key binding that the game hasn't handled yet; each one runs the key's action once. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("clickCount")
    int crystal$getClickCount();

    @Accessor("clickCount")
    void crystal$setClickCount(int clicks);
}
