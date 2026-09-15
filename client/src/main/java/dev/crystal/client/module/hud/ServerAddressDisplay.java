package dev.crystal.client.module.hud;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.List;

/** The address of the server you're on, optionally with the server's logo in front. */
public class ServerAddressDisplay extends HudModule {

    private static final Identifier LOGO_ID = Identifier.of(CrystalClient.MOD_ID, "hud/server_logo");
    private static final Identifier UNKNOWN_LOGO = Identifier.ofVanilla("textures/misc/unknown_server.png");

    private boolean showLogo = true;

    /** The favicon bytes last uploaded (or tried) as {@link #LOGO_ID}, to spot a server change. */
    private byte[] uploadedFavicon = null;
    private boolean uploadFailed = false;

    public ServerAddressDisplay() {
        super("ServerAddress", "Displays the address of the server you're connected to", 4, 124);
    }

    @Override
    public String getText() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.isIntegratedServerRunning()) return "Singleplayer";

        var entry = mc.getCurrentServerEntry();
        return entry != null ? entry.address : "";
    }

    @Override
    public Identifier getIcon() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!showLogo || mc.isIntegratedServerRunning()) return null;
        var entry = mc.getCurrentServerEntry();
        if (entry == null) return null;

        byte[] favicon = entry.getFavicon();
        if (favicon == null) return UNKNOWN_LOGO;
        if (!Arrays.equals(favicon, uploadedFavicon)) {
            uploadedFavicon = favicon;
            try {
                NativeImage image = NativeImage.read(favicon);
                // Replaces (and frees) the previous server's logo.
                mc.getTextureManager().registerTexture(LOGO_ID, new NativeImageBackedTexture(LOGO_ID::toString, image));
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
