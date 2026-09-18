package dev.crystal.client.module.hud;

import com.mojang.blaze3d.platform.NativeImage;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import dev.crystal.client.compat.TextureCompat;

/** The address of the server you're on, optionally with the server's logo in front. */
public class ServerAddressDisplay extends HudModule {

    private static final Identifier LOGO_ID = Identifier.fromNamespaceAndPath(CrystalClient.MOD_ID, "hud/server_logo");
    private static final Identifier UNKNOWN_LOGO = Identifier.withDefaultNamespace("textures/misc/unknown_server.png");

    private boolean showLogo = true;

    /** The favicon bytes last uploaded (or tried) as {@link #LOGO_ID}, to spot a server change. */
    private byte[] uploadedFavicon = null;
    private boolean uploadFailed = false;

    public ServerAddressDisplay() {
        // Below the icon Armor HUD, which runs from y=80 to about 153.
        super("ServerAddress", "Displays the address of the server you're connected to", 4, 158);
    }

    @Override
    public String getText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer()) return "Singleplayer";

        var entry = mc.getCurrentServer();
        return entry != null ? entry.ip : "";
    }

    @Override
    public Identifier getIcon() {
        Minecraft mc = Minecraft.getInstance();
        if (!showLogo || mc.hasSingleplayerServer()) return null;
        var entry = mc.getCurrentServer();
        if (entry == null) return null;

        byte[] favicon = entry.getIconBytes();
        if (favicon == null) return UNKNOWN_LOGO;
        if (!Arrays.equals(favicon, uploadedFavicon)) {
            uploadedFavicon = favicon;
            try {
                NativeImage image = NativeImage.read(favicon);
                // Replaces (and frees) the previous server's logo.
                mc.getTextureManager().register(LOGO_ID, TextureCompat.create(LOGO_ID::toString, image));
                uploadFailed = false;
            } catch (Exception e) {
                CrystalClient.LOGGER.warn("[Crystal] Server logo could not be read: {}", e.getMessage());
                uploadFailed = true; // same broken image isn't retried every frame
            }
        }
        return uploadFailed ? UNKNOWN_LOGO : LOGO_ID;
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(new BooleanSetting("Show Logo", () -> showLogo, v -> showLogo = v, true));
    }
}
