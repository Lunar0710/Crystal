package dev.crystal.client.mixin;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.gui.CrystalClientScreen;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard {

    @Shadow private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"))
    private void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;

        int key = input.key();

        // Right Shift opens Crystal GUI
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT && client.currentScreen == null) {
            client.execute(() -> client.setScreen(new CrystalClientScreen()));
            return;
        }

        if (CrystalClient.getInstance() != null) {
            CrystalClient.getInstance().getModuleManager().handleKeybind(key);
        }
    }
}
