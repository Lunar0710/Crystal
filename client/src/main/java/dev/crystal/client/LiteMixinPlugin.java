package dev.crystal.client;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Leaves out the mixins that only serve modules and looks Nexora Lite never
 * runs. A mixin that is not applied costs nothing, not even the check whether
 * its module is on, and several of these sit on paths that run per entity,
 * per item or per rain column every frame (weather and time overrides, the
 * enchantment glint check, name decorations, skin and cape lookups).
 * SmartCulling's hooks are also left out in full Nexora when EntityCulling
 * is installed (ModCompat).
 *
 * Only mixins that no other code depends on are listed here; accessors and
 * the HUD, input and camera hooks the Lite modules need always stay:
 * MixinMinecraftClient (config save, misses for Reach), MixinKeyboard (menu
 * key, keybinds), MixinInGameHud (Crosshair), MixinCamera, MixinEntity and
 * MixinMouseScroll (Freelook, Zoom), MixinGameRenderer (NoHurtCam, FOV),
 * MixinClientPlayerInteractionManager (hits for Reach and Combo),
 * MixinInactivityFpsLimiter (BackgroundFps), MixinParticleEngine (PerformanceMode).
 */
public final class LiteMixinPlugin implements IMixinConfigPlugin {

    private static final Set<String> SKIPPED_IN_LITE = Set.of(
            // Menu skins, Nexora's title and pause screens, smooth scrolling.
            "MixinMenuBackground", "MixinButtonSkin", "MixinListSkin", "MixinSliderSkin",
            "MixinScrollableWidget", "MixinClickableWidget", "MixinScreenHost", "MixinDrawContext",
            // Capes, 3D skins, cosmetics, SkinChanger and other players' Nexora capes.
            "MixinPlayerCapeModel", "MixinCapeFeatureRenderer", "MixinPlayerEntityModel",
            "MixinAbstractClientPlayerEntity",
            // Sky, fog, post effects, fire, glint, hit colour, item physics.
            "MixinSkyColor", "MixinAtmosphericFogModifier", "MixinFogRenderer", "MixinPostEffectPass",
            "MixinFireOverlay", "MixinItemStack", "MixinOverlayTexture",
            "MixinItemEntityRenderer", "MixinItemEntityRenderState",
            // Time and weather overrides (queried per rain column and light update).
            "MixinClientWorld", "MixinClientClockManager",
            // Name tags, NickHider, tab list, boss bar, chat.
            "MixinLabelCommands", "MixinTabListName", "MixinPlayerEntity", "MixinLivingEntityRenderer",
            "MixinPlayerListHud", "MixinBossBarHud", "MixinChatHud",
            // TotemPops and TPSDisplay packets.
            "MixinLivingEntityEvents", "MixinTimePacket",
            // World overlays (outline, hitboxes, chunk borders) on 1.21.9.
            "MixinLevelRendererLegacy",
            // RenderLimits and the NameTags/TeamView/logo label decorations.
            "MixinEntityRenderer", "MixinBlockEntityCulling");

    /**
     * SmartCulling's per-entity and per-block-entity hooks. Left out in full
     * Nexora and in Lite when EntityCulling is installed (ModCompat): it does
     * the same job, and SmartCulling stays off then.
     */
    private static final Set<String> SMART_CULLING = Set.of("MixinSmartCullingEntity", "MixinSmartCullingBlockEntity");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (SMART_CULLING.contains(simple)) return Lite.allows("SmartCulling") && ModCompat.replacedBy("SmartCulling") == null;
        if (!Lite.ON) return true;
        return !SKIPPED_IN_LITE.contains(simple);
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
