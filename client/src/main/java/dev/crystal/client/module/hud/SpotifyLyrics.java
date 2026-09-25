package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import dev.crystal.client.util.NowPlaying;

import java.util.List;

/**
 * The lyrics of the song playing in Spotify, line by line in time with the
 * music: the line being sung, and the next one fainter underneath. A HUD
 * element of its own, apart from the Spotify card, so each can be moved on
 * its own. Lyrics come from LRCLIB; songs without synced lyrics show nothing.
 */
public class SpotifyLyrics extends HudModule {

    private boolean showNext = true;
    /** Seconds each line shows before it is sung, so you can read it in time. */
    private float lead = 2f;

    public SpotifyLyrics() {
        super("Lyrics", "Shows the lyrics of the Spotify song in time with the music", 150, 30);
    }

    public boolean showNext() { return showNext; }

    /** Where in the song the shown line is picked: the playback position plus the lead. */
    public long lookAt(long position) { return position + Math.round(lead * 1000); }

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
                new SliderSetting("Vorlauf (Sekunden)", () -> lead, v -> lead = v, 0f, 3f, 0.5f, 1),
                new BooleanSetting("Nächste Zeile zeigen", () -> showNext, v -> showNext = v, true));
    }
}
