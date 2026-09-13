package dev.crystal.client.module.hud;

/**
 * A HUD module that draws as one line of text at a fixed screen position.
 * {@link dev.crystal.client.gui.CrystalHUD} renders every enabled module that
 * implements this the same way, so adding a new text-line HUD module never
 * requires touching the renderer.
 */
public interface HudRenderable {
    String getText();
    int getX();
    int getY();
}
