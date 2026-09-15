package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Rendered specially by {@link dev.crystal.client.gui.CrystalHUD} — a WASD box grid, not a single text line. */
public class Keystrokes extends HudModule {

    private int pressedColor = 0xFF5B8AF5;
    private int idleColor = 0xB0202632;
    private int pressedTextColor = 0xFFFFFFFF;
    private int idleTextColor = 0xFFAAB2C0;
    private float keySize = 14f;
    private boolean showClicks = true;

    public Keystrokes() {
        super("Keystrokes", "Shows WASD and click inputs on screen", 4, 200);
    }

    public boolean forward() { return Minecraft.getInstance().options.keyUp.isDown(); }
    public boolean left() { return Minecraft.getInstance().options.keyLeft.isDown(); }
    public boolean back() { return Minecraft.getInstance().options.keyDown.isDown(); }
    public boolean right() { return Minecraft.getInstance().options.keyRight.isDown(); }
    public boolean jump() { return Minecraft.getInstance().options.keyJump.isDown(); }
    public boolean attack() { return Minecraft.getInstance().options.keyAttack.isDown(); }
    public boolean use() { return Minecraft.getInstance().options.keyUse.isDown(); }

    public int getPressedColor() { return pressedColor; }
    public int getIdleColor() { return idleColor; }
    public int getPressedTextColor() { return pressedTextColor; }
    public int getIdleTextColor() { return idleTextColor; }
    public int getKeySize() { return Math.round(keySize); }
    public boolean isShowClicks() { return showClicks; }

    /** Unused — the key grid draws itself; only here to satisfy the HUD contract. */
    @Override
    public String getText() {
        return "";
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new ColorSetting("Pressed Color", () -> pressedColor, v -> pressedColor = v, 0xFF5B8AF5),
                new ColorSetting("Idle Color", () -> idleColor, v -> idleColor = v, 0xB0202632),
                new ColorSetting("Pressed Text", () -> pressedTextColor, v -> pressedTextColor = v, 0xFFFFFFFF),
                new ColorSetting("Idle Text", () -> idleTextColor, v -> idleTextColor = v, 0xFFAAB2C0),
                new SliderSetting("Key Size", () -> keySize, v -> keySize = v, 10f, 24f, 1f, 0),
                new BooleanSetting("Show Clicks", () -> showClicks, v -> showClicks = v, true)
        );
    }
}
