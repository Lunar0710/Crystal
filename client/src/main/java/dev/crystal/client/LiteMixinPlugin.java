package dev.crystal.client;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Leaves out the mixins that only serve modules and looks Nexora Lite never
 * runs: skinned menus, capes and 3D skins, fog and sky colours, post effects,
 * item physics, hit colour, smooth scrolling, name tag tweaks. A mixin that is
 * not applied costs nothing, not even the check whether its module is on.
 *
 * Only mixins that no other code depends on are listed here; accessors and
 * the HUD/event hooks the Lite modules need always stay.
 */
public final class LiteMixinPlugin implements IMixinConfigPlugin {

    private static final Set<String> SKIPPED_IN_LITE = Set.of(
            "MixinMenuBackground", "MixinButtonSkin", "MixinListSkin", "MixinSliderSkin",
            "MixinSkyColor", "MixinPlayerCapeModel", "MixinCapeFeatureRenderer", "MixinPlayerEntityModel",
            "MixinAtmosphericFogModifier", "MixinFogRenderer", "MixinPostEffectPass",
            "MixinItemEntityRenderer", "MixinItemEntityRenderState", "MixinOverlayTexture",
            "MixinScrollableWidget", "MixinFireOverlay", "MixinLabelCommands", "MixinTabListName");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!Lite.ON) return true;
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        return !SKIPPED_IN_LITE.contains(simple);
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
