package dev.crystal.client.module.hud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.KeybindSetting;
import org.lwjgl.glfw.GLFW;
import dev.crystal.client.module.Setting;
import dev.crystal.client.util.NowPlaying;

import java.util.List;
import java.util.function.Consumer;

/**
 * The song playing in Spotify: a small card with the title, the artist and
 * how far into the song you are. No Spotify login needed; see NowPlaying.
 * Drawn by CrystalHUD. The lyrics are a separate element (SpotifyLyrics), so
 * both can sit wherever you like.
 */
public class SpotifyHUD extends HudModule {

    private boolean hideWhenPaused = false;
    private boolean showProgress = true;

    /** Keys that control Spotify from inside the game; none by default. */
    private int keyPlayPause = GLFW.GLFW_KEY_UNKNOWN, keyNext = GLFW.GLFW_KEY_UNKNOWN, keyPrevious = GLFW.GLFW_KEY_UNKNOWN;
    private final boolean[] wasDown = new boolean[3];
    private final Consumer<TickEvent> tickListener = this::onTick;

    @Override
    public void onEnable() {
        super.onEnable();
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
    }

    private void onTick(TickEvent event) {
        var mc = event.getClient();
        // Only while playing, not while typing in chat or a menu.
        boolean free = mc.player != null && mc.screen == null;
        int[] keys = {keyPlayPause, keyNext, keyPrevious};
        NowPlaying.Control[] actions = {NowPlaying.Control.PLAY_PAUSE, NowPlaying.Control.NEXT, NowPlaying.Control.PREVIOUS};
        for (int i = 0; i < 3; i++) {
            boolean down = free && keys[i] != GLFW.GLFW_KEY_UNKNOWN && InputConstants.isKeyDown(mc.getWindow(), keys[i]);
            if (down && !wasDown[i]) NowPlaying.control(actions[i]);
            wasDown[i] = down;
        }
    }

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
                new BooleanSetting("Pausiert ausblenden", () -> hideWhenPaused, v -> hideWhenPaused = v, false),
                new KeybindSetting("Taste: Play/Pause", () -> keyPlayPause, v -> keyPlayPause = v),
                new KeybindSetting("Taste: Nächster Song", () -> keyNext, v -> keyNext = v),
                new KeybindSetting("Taste: Voriger Song", () -> keyPrevious, v -> keyPrevious = v));
    }
}
