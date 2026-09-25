package dev.crystal.client.module.hud;

import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.Setting;
import dev.crystal.client.util.NowPlaying;

import java.util.List;

/**
 * The song playing in Spotify: a small card with the title, the artist and
 * how far into the song you are. No Spotify login needed; see NowPlaying.
 * Drawn by CrystalHUD. The lyrics are a separate element (SpotifyLyrics), so
 * both can sit wherever you like.
 */
public class SpotifyHUD extends HudModule {

    private boolean hideWhenPaused = false;
    private boolean showProgress = true;

    public SpotifyHUD() {
        super("Spotify", "Shows the song playing in Spotify, with artist and progress", 4, 150);
    }

    /** The song to show, or null when there is none (or it is paused and paused songs are hidden). */
    public NowPlaying.Track track() {
        NowPlaying.Track t = NowPlaying.current();
        if (t == null || (hideWhenPaused && !t.playing())) return null;
        return t;
    }

    public boolean showProgress() { return showProgress; }

    @Override
    public String getText() {
        NowPlaying.Track t = NowPlaying.current();
        return t == null ? "Spotify" : t.title() + " - " + t.artist();
    }

    @Override
    protected List<Setting<?>> getExtraSettings() {
        return List.of(
                new BooleanSetting("Fortschritt zeigen", () -> showProgress, v -> showProgress = v, true),
                new BooleanSetting("Pausiert ausblenden", () -> hideWhenPaused, v -> hideWhenPaused = v, false));
    }
}
