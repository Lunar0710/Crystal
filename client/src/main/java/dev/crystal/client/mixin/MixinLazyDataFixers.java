package dev.crystal.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.DSL;
import dev.crystal.client.util.StartupTimer;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Faster game start, like LazyDFU and ModernFix did on older versions (neither
 * exists for 1.21.11). Vanilla's Main optimises the data fixers for the world
 * list on one low-priority thread and then waits for it before the window even
 * opens. That usually takes seconds; on a busy CPU with a large mod list it was
 * measured at 149 s ("287 Datafixer optimizations took 149155 milliseconds").
 *
 * Skipping it is safe: DataFixerUpper builds every rule on first use anyway
 * (that is how all other data types, chunks and entities included, are always
 * handled). The only cost is that the first world-list or world load that
 * really has to upgrade an old world pays for its own rules then.
 * -Dnexora.eagerDfu=true restores vanilla's order.
 */
@Mixin(Main.class)
public class MixinLazyDataFixers {

    @WrapOperation(method = "main", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/datafix/DataFixers;optimize(Ljava/util/Set;)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<?> crystal$lazyDataFixers(Set<DSL.TypeReference> types, Operation<CompletableFuture<?>> original) {
        if (StartupTimer.EAGER_DFU) return original.call(types);
        LoggerFactory.getLogger("crystal").info("[Nexora] Data fixers are built on demand (skipped the startup optimisation of {} data types)", types.size());
        return CompletableFuture.completedFuture(null);
    }
}
