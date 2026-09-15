package dev.crystal.client.module.render;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.List;

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

    public Text decorate(Text name, PlayerEntity player) {
        var self = MinecraftClient.getInstance().player;
        if (self == null || player == self) return name;
        var ownTeam = self.getScoreboardTeam();
        var theirTeam = player.getScoreboardTeam();
        if (ownTeam == null || theirTeam == null) return name;

        boolean teammate = ownTeam.isEqual(theirTeam);
        if (!teammate && !markEnemies) return name;
        int color = (teammate ? teamColor : enemyColor) & 0xFFFFFF;
        MutableText text = Text.literal("● ").styled(s -> s.withColor(color));
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
