package dev.crystal.client.compat;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.Minecraft;

/**
 * Mojang's session service, which proves to a server who you are. 1.21.9 moved
 * it from the Minecraft class into its services bundle.
 */
public final class SessionCompat {

    private SessionCompat() {}

    public static MinecraftSessionService sessionService(Minecraft mc) {
        //? if >=1.21.9 {
        return mc.services().sessionService();
        //?} else {
        /*return mc.getMinecraftSessionService();
        *///?}
    }
}
