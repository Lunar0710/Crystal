package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import dev.crystal.client.util.NowPlaying;

import java.util.List;

/**
 * The lyrics of the song playing in Spotify, in time with the music: the
 * line being sung at the top, marked, and under it every line coming up in
 * the next few seconds (four by default), fainter. A HUD
 * element of its own, apart from the Spotify card, so each can be moved on
 * its own. Lyrics come from LRCLIB; songs without synced lyrics show nothing.
 */
public class SpotifyLyrics extends HudModule {

    private boolean showNext = true;
    /** How many seconds of upcoming lyrics are shown under the current line. */
    private float window = 4f;

    public SpotifyLyrics() {
        super("Lyrics", "Shows the lyrics of the Spotify song in time with the music", 150, 30);
    }

    public boolean showNext() { return showNext; }

    /** Upcoming lines starting within this many milliseconds are shown. */
    public long windowMs() { return Math.round(window * 1000); }

    /** Index of the line being sung at {@code position}, or -1 before the first. */
    public static int lineAt(List<NowPlaying.Line> lines, long position) {
        int found = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs() <= position + 150) found = i; else break;
        }
        return found;
    }

    @Override
    public String getText() {
        return "Lyrics";
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new SliderSetting("Vorschau (Sekunden)", () -> window, v -> window = v, 1f, 8f, 0.5f, 1),
                new BooleanSetting("Kommende Zeilen zeigen", () -> showNext, v -> showNext = v, true));
    }
}
