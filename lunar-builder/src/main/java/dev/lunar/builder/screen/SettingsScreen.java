package dev.lunar.builder.screen;

import dev.lunar.builder.Config;
import dev.lunar.builder.Gate;
import dev.lunar.builder.LunarBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The Lunar Builder menu: own servers allowlist, dig speed, outline, pickaxe
 * selection and "Platzierung bauen". Adding a server always asks first whether
 * that server allows bots/macros.
 */
public final class SettingsScreen extends Screen {

    private static final int PER_PAGE = 4;

    private final Screen parent;
    private EditBox address;
    private int page = 0;
    private String info = null;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Lunar Builder"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config config = Config.get();
        int cx = width / 2, left = cx - 155, y = 34;

        addRenderableWidget(Button.builder(Component.literal("Tempo: " + config.digSpeed), b -> {
            int i = Config.SPEEDS.indexOf(config.digSpeed);
            config.digSpeed = Config.SPEEDS.get((i + 1) % Config.SPEEDS.size());
            config.save();
            rebuildWidgets();
        }).bounds(left, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Umriss anzeigen: " + (config.showOutline ? "an" : "aus")), b -> {
            config.showOutline = !config.showOutline;
            config.save();
            rebuildWidgets();
        }).bounds(left + 160, y, 150, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Auswahl mit Spitzhacke: " + (config.pickaxeSelection ? "an" : "aus")), b -> {
            config.pickaxeSelection = !config.pickaxeSelection;
            config.save();
            rebuildWidgets();
        }).bounds(left, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Platzierung bauen"), b -> {
            onClose();
            LunarBuilder.startBuild();
        }).bounds(left + 160, y, 150, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(Component.literal("AFK-Modus: " + (config.afk ? "an" : "aus")), b -> {
            config.afk = !config.afk;
            config.save();
            rebuildWidgets();
        }).bounds(left, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Auto-Update: " + (config.autoUpdate ? "an" : "aus")), b -> {
            config.autoUpdate = !config.autoUpdate;
            config.save();
            rebuildWidgets();
        }).bounds(left + 160, y, 150, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Auto-Essen: " + (config.autoEat ? "an" : "aus")), b -> {
            config.autoEat = !config.autoEat;
            config.save();
            rebuildWidgets();
        }).bounds(left, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Auto-Mending: " + (config.autoMend ? "an" : "aus")), b -> {
            config.autoMend = !config.autoMend;
            config.save();
            rebuildWidgets();
        }).bounds(left + 160, y, 150, 20).build());

        // The allowlist.
        y += 44;
        address = new EditBox(font, left, y, 200, 20, Component.literal("Serveradresse"));
        address.setHint(Component.literal("z. B. mein-server.de"));
        address.setMaxLength(100);
        addRenderableWidget(address);
        addRenderableWidget(Button.builder(Component.literal("Hinzufügen"), b -> askToAdd(address.getValue()))
                .bounds(left + 206, y, 104, 20).build());
        y += 24;
        ServerData current = minecraft == null ? null : minecraft.getCurrentServer();
        if (current != null && minecraft != null && !minecraft.hasSingleplayerServer()) {
            String ip = current.ip;
            addRenderableWidget(Button.builder(Component.literal("Diesen Server eintragen (" + Gate.normalize(ip) + ")"), b -> askToAdd(ip))
                    .bounds(left, y, 310, 20).build());
            y += 24;
        }
        List<String> list = config.allowlist;
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        for (int i = page * PER_PAGE; i < Math.min(list.size(), (page + 1) * PER_PAGE); i++) {
            String entry = list.get(i);
            addRenderableWidget(Button.builder(Component.literal("Entfernen"), b -> {
                config.allowlist.remove(entry);
                config.save();
                rebuildWidgets();
            }).bounds(left + 230, y, 80, 18).build());
            y += 20;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = (page + pages - 1) % pages; rebuildWidgets(); })
                    .bounds(left, y + 2, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { page = (page + 1) % pages; rebuildWidgets(); })
                    .bounds(left + 24, y + 2, 20, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose()).bounds(cx - 75, height - 28, 150, 20).build());
    }

    /** Only after "Ja, ist erlaubt". */
    private void askToAdd(String raw) {
        String normalized = Gate.normalize(raw);
        if (normalized.isEmpty()) {
            info = "Bitte eine Serveradresse eingeben";
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                Config config = Config.get();
                if (!config.allowlist.contains(normalized)) config.allowlist.add(normalized);
                config.save();
                info = normalized + " freigegeben";
            }
            mc.setScreen(this);
        }, Component.literal("Server freigeben: " + normalized),
                Component.literal("Erlaubt dieser Server automatisches Bauen/Abbauen (Bots/Makros)? Nur eintragen, wenn die Serverregeln das erlauben – sonst droht ein Bann."),
                Component.literal("Ja, ist erlaubt"), Component.literal("Abbrechen")));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        Minecraft mc = Minecraft.getInstance();
        int cx = width / 2, left = cx - 155;
        graphics.drawCenteredString(font, "Lunar Builder – Einstellungen", cx, 12, 0xFFFFFFFF);
        boolean here = LunarBuilder.allowed(mc);
        String where = mc.level == null ? "" : here ? "Hier aktiv" : LunarBuilder.FOREIGN;
        graphics.drawCenteredString(font, where, cx, 22, here ? 0xFF70FF70 : 0xFFFF7070);
        int y = 34 + 24 + 30;
        graphics.drawString(font, "Freigegebene Server (Automatisierung erlaubt):", left, y, 0xFFFFFFFF, true);
        y += 14 + 24;
        ServerData current = mc.getCurrentServer();
        if (current != null && !mc.hasSingleplayerServer()) y += 24;
        List<String> list = Config.get().allowlist;
        if (list.isEmpty()) {
            graphics.drawString(font, "Keine – Lunar Builder läuft nur im Einzelspieler.", left, y + 4, 0xFFAAAAAA, true);
        }
        for (int i = page * PER_PAGE; i < Math.min(list.size(), (page + 1) * PER_PAGE); i++) {
            graphics.drawString(font, list.get(i), left + 4, y + 5, 0xFFDDDDDD, true);
            y += 20;
        }
        if (info != null) graphics.drawCenteredString(font, info, cx, height - 44, 0xFFFFD060);
        graphics.drawCenteredString(font, "Tasten: " + LunarBuilder.keyName(LunarBuilder.pauseKey) + " Pause/Weiter · "
                + LunarBuilder.keyName(LunarBuilder.stopKey) + " Stopp · Auswahl: Spitzhacke Links-/Rechtsklick", cx, height - 56, 0xFF999999);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
