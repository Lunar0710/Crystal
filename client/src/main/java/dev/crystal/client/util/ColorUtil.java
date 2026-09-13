package dev.crystal.client.util;

/** Small shared colour helpers for the render modules (alpha blending, rainbow cycling, health gradients). */
public final class ColorUtil {

    private ColorUtil() {}

    /** Replaces the alpha channel of an ARGB colour with a 0–100 percentage. */
    public static int withAlphaPercent(int argb, float percent) {
        int alpha = Math.round(Math.max(0f, Math.min(100f, percent)) * 2.55f);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** Hue cycling driven by wall-clock time, so every module using it stays in sync. */
    public static int rainbow(float speed) {
        float hue = (System.currentTimeMillis() % (long) (10000 / Math.max(0.1f, speed)))
                / (10000f / Math.max(0.1f, speed));
        return 0xFF000000 | hsbToRgb(hue, 0.85f, 1f);
    }

    /** Green at full health through yellow to red at empty — used by hitbox/target colouring. */
    public static int healthGradient(float healthFraction) {
        float clamped = Math.max(0f, Math.min(1f, healthFraction));
        // 0.0 (red) .. 0.33 (green) in hue space
        return 0xFF000000 | hsbToRgb(clamped * 0.33f, 0.9f, 1f);
    }

    private static int hsbToRgb(float hue, float saturation, float brightness) {
        int r = 0, g = 0, b = 0;
        if (saturation == 0) {
            r = g = b = (int) (brightness * 255f + 0.5f);
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1f - saturation);
            float q = brightness * (1f - saturation * f);
            float t = brightness * (1f - saturation * (1f - f));
            switch ((int) h) {
                case 0 -> { r = to255(brightness); g = to255(t); b = to255(p); }
                case 1 -> { r = to255(q); g = to255(brightness); b = to255(p); }
                case 2 -> { r = to255(p); g = to255(brightness); b = to255(t); }
                case 3 -> { r = to255(p); g = to255(q); b = to255(brightness); }
                case 4 -> { r = to255(t); g = to255(p); b = to255(brightness); }
                case 5 -> { r = to255(brightness); g = to255(p); b = to255(q); }
            }
        }
        return (r << 16) | (g << 8) | b;
    }

    private static int to255(float value) {
        return (int) (value * 255f + 0.5f);
    }
}
