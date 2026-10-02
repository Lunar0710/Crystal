package dev.lunar.builder.screen;

import dev.lunar.builder.LunarBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** After corner 2: how many layers down (the selected one counts), then Start or Abbrechen. */
public final class DepthScreen extends Screen {

    public static final int DEFAULT_DEPTH = 5;

    private final BlockPos corner1, corner2;
    private EditBox depthBox;
    private int depth = DEFAULT_DEPTH;
    private String error = null;

    public DepthScreen(BlockPos corner1, BlockPos corner2) {
        super(Component.literal("Wie viele Schichten nach unten?"));
        this.corner1 = corner1;
        this.corner2 = corner2;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 10;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> set(depth - 1)).bounds(cx - 82, y, 20, 20).build());
        depthBox = new EditBox(font, cx - 55, y, 110, 20, Component.literal("Schichten"));
        depthBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,2}"));
        depthBox.setValue(String.valueOf(depth));
        depthBox.setResponder(s -> {
            error = null;
            try {
                depth = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                depth = 0;
            }
        });
        addRenderableWidget(depthBox);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> set(depth + 1)).bounds(cx + 62, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Start"), b -> start()).bounds(cx - 102, y + 36, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Abbrechen"), b -> {
            LunarBuilder.clearSelection();
            onClose();
        }).bounds(cx + 2, y + 36, 100, 20).build());
        setInitialFocus(depthBox);
    }

    private void set(int value) {
        depth = Math.max(1, Math.min(64, value));
        depthBox.setValue(String.valueOf(depth));
    }

    /** Start pressed (also used by the CI test). */
    public void start() {
        if (depth < 1 || depth > 64) {
            error = "Bitte eine Zahl von 1 bis 64 eingeben";
            return;
        }
        boolean started = LunarBuilder.startDig(corner1, corner2, depth);
        onClose();
        if (!started) LunarBuilder.clearSelection();
    }

    public void setDepthForTest(int value) {
        set(value);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        int cx = width / 2, y = height / 2 - 10;
        graphics.drawCenteredString(font, title, cx, y - 34, 0xFFFFFFFF);
        int sizeX = Math.abs(corner1.getX() - corner2.getX()) + 1, sizeZ = Math.abs(corner1.getZ() - corner2.getZ()) + 1;
        graphics.drawCenteredString(font, "Bereich " + sizeX + " × " + sizeZ + " ab Y=" + corner1.getY()
                + " – die gewählte Schicht zählt mit (1–64)", cx, y - 20, 0xFFAAAAAA);
        if (error != null) graphics.drawCenteredString(font, error, cx, y + 62, 0xFFFF6060);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
