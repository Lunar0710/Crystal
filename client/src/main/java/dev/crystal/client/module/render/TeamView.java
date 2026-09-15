package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * Marks players by scoreboard team: a coloured dot in front of teammates' and
 * opponents' nametags. Uses only the team data the server already sends (the
 * same that colours names in the tab list), so it shows nothing hidden.
 */
public class TeamView extends Module {

    private int teamColor = 0xFF22C55E;
    private int enemyColor = 0xFFEF4444;
    private boolean markEnemies = true;

    public TeamView() {
        super("TeamView", "Marks teammates and opponents with a coloured dot on their nametag", ModuleCategory.RENDER);
        setEnabled(true);
    }

    public Component decorate(Component name, Player player) {
        var self = Minecraft.getInstance().player;
        if (self == null || player == self) return name;
        var ownTeam = self.getTeam();
        var theirTeam = player.getTeam();
        if (ownTeam == null || theirTeam == null) return name;

        boolean teammate = ownTeam.isAlliedTo(theirTeam);
        if (!teammate && !markEnemies) return name;
        int color = (teammate ? teamColor : enemyColor) & 0xFFFFFF;
        MutableComponent text = Component.literal("● ").withStyle(s -> s.withColor(color));
        return text.append(name);
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Team Color", () -> teamColor, v -> teamColor = v, 0xFF22C55E),
                new ColorSetting("Enemy Color", () -> enemyColor, v -> enemyColor = v, 0xFFEF4444),
                new BooleanSetting("Mark Enemies", () -> markEnemies, v -> markEnemies = v, true));
    }
}
