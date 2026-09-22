package dev.crystal.client.module.render;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
//? if >=1.21.9
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * The Nexora logo in front of the names of Nexora players, in their nametag
 * and in the tab list (like Lunar Client's icon). The logo is a character of
 * Nexora's own icon font, so it lines up with the text wherever names show.
 *
 * For now only you are known to play Nexora; other players follow once the
 * Nexora server reports who is online with Nexora.
 */
public class CrystalLogo extends Module {

    //? if >=1.21.9 {
    private static final FontDescription ICON_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath(CrystalClient.MOD_ID, "icons"));
    //?} else {
    /*private static final Identifier ICON_FONT = Identifier.fromNamespaceAndPath(CrystalClient.MOD_ID, "icons");
    *///?}
    private static final String LOGO_CHAR = "\uE000";

    private boolean inNametag = true;
    private boolean inTabList = true;

    public CrystalLogo() {
        super("CrystalLogo", "Shows the Nexora logo next to the names of Nexora players", ModuleCategory.RENDER);
        setEnabled(true);
    }

    /** Whether this player plays Nexora. */
    public static boolean usesCrystal(UUID player) {
        var self = Minecraft.getInstance().player;
        return self != null && self.getUUID().equals(player);
    }

    /** The logo, a space, then the name. */
    public static Component withLogo(Component name) {
        MutableComponent logo = Component.literal(LOGO_CHAR).withStyle(style -> style.withFont(ICON_FONT).withColor(0xFFFFFF));
        return Component.empty().append(logo).append(" ").append(name);
    }

    public boolean isInNametag() { return inNametag; }
    public boolean isInTabList() { return inTabList; }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new BooleanSetting("Im Nametag", () -> inNametag, v -> inNametag = v, true),
                new BooleanSetting("In der Tab-Liste", () -> inTabList, v -> inTabList = v, true)
        );
    }
}
