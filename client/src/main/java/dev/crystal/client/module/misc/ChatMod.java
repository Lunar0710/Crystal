package dev.crystal.client.module.misc;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.BooleanSetting;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import java.util.List;
import java.util.function.Consumer;

/**
 * Wraps vanilla's own chat scale/opacity/width options — restores them on disable.
 *
 * Modules are constructed (and default-enabled ones call onEnable()) during
 * Fabric's client entrypoint, which some modpacks/loader orderings reach
 * before MinecraftClient.options exists yet. So this can't touch options
 * synchronously in onEnable() — it defers the actual read/write to the first
 * client tick, by which point options is always initialized.
 */
public class ChatMod extends Module {

    private float scale = 1f;
    private float opacity = 1f;
    private float width = 1f;
    private boolean stackDuplicates = true;
    private boolean timestamps = false;
    private boolean highlightName = true;
    private boolean highlightSound = true;
    private int highlightColor = 0xFFFACC15;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private double previousScale, previousOpacity, previousWidth;
    private boolean capturedPrevious = false;

    private final Consumer<TickEvent> tickListener = this::onTick;

    public ChatMod() {
        super("Chat", "Chat size and opacity, stacked repeats, timestamps and name highlight", ModuleCategory.MISC);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, tickListener);
    }

    @Override
    public void onDisable() {
        CrystalClient.getInstance().getEventBus().unsubscribe(TickEvent.class, tickListener);
        var options = MinecraftClient.getInstance().options;
        if (options == null || !capturedPrevious) return;
        options.getChatScale().setValue(previousScale);
        options.getChatOpacity().setValue(previousOpacity);
        options.getChatWidth().setValue(previousWidth);
    }

    private void onTick(TickEvent event) {
        if (capturedPrevious) return;
        var options = event.getClient().options;
        if (options == null) return;
        previousScale = options.getChatScale().getValue();
        previousOpacity = options.getChatOpacity().getValue();
        previousWidth = options.getChatWidth().getValue();
        capturedPrevious = true;
        apply();
    }

    private void apply() {
        var options = MinecraftClient.getInstance().options;
        if (options == null) return;
        options.getChatScale().setValue((double) scale);
        options.getChatOpacity().setValue((double) opacity);
        options.getChatWidth().setValue((double) width);
    }

    public boolean isStackDuplicates() { return stackDuplicates; }

    /**
     * The line as it should appear: optional time in front, a coloured marker
     * when your name is mentioned (by someone else), and "(x3)" for repeats.
     * Called from MixinChatHud for every incoming chat line.
     */
    /**
     * Your name somewhere in the line as a whole word, but not at the start.
     * Lines that start with your name are about you, not to you: your own chat
     * ("&lt;Name&gt; hi", "Name: hi") and server notices like "Name joined the
     * game" or "Name has made the advancement". Rank prefixes such as
     * "[VIP] Name: hi" count as starting with the name too.
     */
    static boolean isMention(String plain, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?<![A-Za-z0-9_])" + java.util.regex.Pattern.quote(name) + "(?![A-Za-z0-9_])", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(plain);
        if (!m.find()) return false;
        String before = plain.substring(0, m.start()).replaceAll("\\[[^\\]]*\\]", "").replace("<", "").trim();
        if (before.isEmpty()) return m.find(); // starts with the name: only a second mention counts
        return true;
    }

    public Text decorate(Text message, int repeat) {
        MutableText out = Text.empty();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (timestamps) out.append(Text.literal("[" + LocalTime.now().format(TIME) + "] ").formatted(Formatting.GRAY));
        if (highlightName && mc.player != null && repeat == 1) {
            String name = mc.player.getName().getString();
            String plain = message.getString();
            if (name.length() >= 3 && isMention(plain, name)) {
                out.append(Text.literal("▌ ").styled(st -> st.withColor(highlightColor & 0xFFFFFF)));
                if (highlightSound) mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.6f, 0.4f));
            }
        }
        out.append(message);
        if (repeat > 1) out.append(Text.literal(" (x" + repeat + ")").formatted(Formatting.GRAY));
        return out;
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new BooleanSetting("Stack Duplicates", () -> stackDuplicates, v -> stackDuplicates = v, true),
                new BooleanSetting("Timestamps", () -> timestamps, v -> timestamps = v, false),
                new BooleanSetting("Highlight Your Name", () -> highlightName, v -> highlightName = v, true),
                new BooleanSetting("Mention Sound", () -> highlightSound, v -> highlightSound = v, true),
                new ColorSetting("Highlight Color", () -> highlightColor, v -> highlightColor = v, 0xFFFACC15),
                new SliderSetting("Scale", () -> scale, v -> { scale = v; if (isEnabled()) apply(); }, 0.5f, 2f, 0.1f, 1),
                new SliderSetting("Background Opacity", () -> opacity, v -> { opacity = v; if (isEnabled()) apply(); }, 0f, 1f, 0.05f, 2),
                new SliderSetting("Width", () -> width, v -> { width = v; if (isEnabled()) apply(); }, 0.4f, 1f, 0.05f, 2)
        );
    }
}
