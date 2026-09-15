package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.gui.CrystalClientScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboard {

    @Shadow private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void onKey(long window, int action, KeyEvent input, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;

        int key = input.key();

        // Right Shift opens Crystal GUI
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT && minecraft.screen == null) {
            minecraft.execute(() -> minecraft.setScreen(new dev.crystal.client.gui.HudEditorScreen()));
            return;
        }

        // Only while actually in the world — otherwise every character typed
        // into a text field (Crystal's own menu, chat, sign editing) would also
        // fire whatever module is bound to that key.
        if (minecraft.screen != null) return;

        if (CrystalClient.getInstance() != null) {
            CrystalClient.getInstance().getModuleManager().handleKeybind(key);
        }
    }
}
