package dev.crystal.client.module.player;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Totem pops: how many totems each player has used up since their last
 * death, for crystal PvP. Shown after the name in their nametag and as a
 * chat line when it happens. The game tells every client about a used totem
 * (entity event 35) and a death (event 3); MixinLivingEntityEvents passes
 * both here.
 */
public class TotemPops extends Module {

    private static final Map<UUID, Integer> POPS = new HashMap<>();

    private boolean inNametag = true;
    private boolean inChat = true;
    private boolean selfAlert = true;

    public TotemPops() {
        super("TotemPops", "Counts how many totems each player has popped since they last died", ModuleCategory.PLAYER);
    }

    private static TotemPops active() {
        CrystalClient client = CrystalClient.getInstance();
        return client == null ? null : client.getModuleManager().getEnabled(TotemPops.class);
    }

    /** From the entity event: a totem was used (35) or the entity died (3). */
    public static void onEntityEvent(LivingEntity entity, byte id) {
        if (!(entity instanceof Player player) || (id != 35 && id != 3)) return;
        TotemPops module = active();
        UUID uuid = player.getUUID();
        if (id == 35) {
            int pops = POPS.merge(uuid, 1, Integer::sum);
            // Your own totem: how many are left is the number that matters now.
            Minecraft mc = Minecraft.getInstance();
            if (module != null && module.selfAlert && player == mc.player) {
                int left = 0;
                var inventory = mc.player.getInventory();
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    var stack = inventory.getItem(i);
                    if (stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) left += stack.getCount();
                }
                mc.player.displayClientMessage(Component.literal("Totem! Noch " + left + " übrig").withStyle(left <= 1 ? ChatFormatting.RED : ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
            if (module != null && module.inChat) module.say(player.getName().getString() + " hat "
                    + (pops == 1 ? "ein Totem" : pops + " Totems") + " verloren", ChatFormatting.GOLD);
        } else {
            Integer pops = POPS.remove(uuid);
            if (module != null && module.inChat && pops != null && pops > 0) {
                module.say(player.getName().getString() + " ist nach " + (pops == 1 ? "einem Totem" : pops + " Totems") + " gestorben", ChatFormatting.RED);
            }
        }
    }

    private void say(String text, ChatFormatting color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(text).withStyle(color), false);
    }

    /** The player's name with " -3" after it once they have popped a totem. */
    public static Component decorate(Component name, Player player) {
        TotemPops module = active();
        Integer pops = POPS.get(player.getUUID());
        if (module == null || !module.inNametag || pops == null || pops == 0) return name;
        MutableComponent text = name.copy();
        text.append(Component.literal(" -" + pops).withStyle(ChatFormatting.GOLD));
        return text;
    }

    /** A new world or server starts counting from zero. */
    public static void reset() {
        POPS.clear();
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new BooleanSetting("Im Namensschild", () -> inNametag, v -> inNametag = v, true),
                new BooleanSetting("Im Chat melden", () -> inChat, v -> inChat = v, true),
                new BooleanSetting("Eigene Totems warnen", () -> selfAlert, v -> selfAlert = v, true));
    }
}
