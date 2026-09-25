package dev.crystal.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * What happens in a fight, for the PvP HUD: confirmed hits, swings, combos,
 * and a summary once the fight is over.
 *
 * A swing that reaches an entity is only a hit if the server accepts it. The
 * sign of that on the client is the target starting its hurt animation right
 * after the attack, so an attack becomes a hit when that happens within a few
 * ticks. Swings into the air count as misses for the hit rate.
 *
 * A fight ends when neither side has hit for {@link #FIGHT_OVER_TICKS}, or
 * when the opponent dies; then the round's numbers become the last summary.
 *
 * Every fight is also recorded for the launcher's fight replay: each tick
 * both sides' position, facing and health, and whether a hit landed or was
 * taken. Saved as .crystal/fights/<start>.json when the fight ends.
 */
public final class CombatTracker {

    private static final int CONFIRM_WINDOW_TICKS = 5;
    private static final int FIGHT_OVER_TICKS = 160; // 8 seconds

    /** One finished (or running) round. */
    public record Round(String opponent, int hits, int swings, int longestCombo, int hitsTaken, long durationMs, boolean won) {
        public int accuracy() { return swings == 0 ? 0 : Math.round(100f * hits / swings); }
    }

    private static LivingEntity pendingTarget = null;
    private static int pendingSince = 0;
    private static int pendingHurtBefore = 0;

    private static boolean inFight = false;
    private static String opponent = null;
    private static LivingEntity opponentEntity = null;
    private static int hits, swings, combo, longestCombo, hitsTaken;
    private static long fightStart = 0;
    private static int lastActionTick = 0;
    private static float lastHealth = -1;
    private static int tick = 0;

    /** Since the game started: rounds won (the opponent died) and own deaths. */
    private static int sessionKills = 0, sessionDeaths = 0;
    private static boolean wasDead = false;

    private static long lastHitAt = 0;

    /** Longest recording kept (5 minutes of ticks); a longer fight keeps its start. */
    private static final int MAX_FRAMES = 20 * 60 * 5;
    /** Fights kept on disk; older ones are deleted. */
    private static final int KEEP_FIGHTS = 100;
    private static final java.util.List<float[]> frames = new java.util.ArrayList<>();
    /** Set during a tick when a hit is confirmed or damage taken, written into that tick's frame. */
    private static boolean hitThisTick = false, hurtThisTick = false;
    private static Round lastRound = null;
    private static long lastRoundAt = 0;

    private CombatTracker() {}

    public static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(CombatTracker::tick);
    }

    /**
     * Fights are against players only: a mob farm would otherwise fill the
     * kill count and push real PvP recordings out. FightSummary's "Mobs
     * mitzählen" (and the world test, which fights a pig) turn mobs on.
     */
    public static boolean countMobs = false;

    /** From MultiPlayerGameMode.attack: the player swung at an entity. */
    public static void onAttack(Entity target) {
        if (!(target instanceof LivingEntity living)) return;
        if (!countMobs && !(living instanceof net.minecraft.world.entity.player.Player)) return;
        startFightIfNeeded(living);
        swings++;
        pendingTarget = living;
        pendingSince = tick;
        pendingHurtBefore = living.hurtTime;
        lastActionTick = tick;
    }

    /** A swing that hit nothing (attack key pressed, no entity under the crosshair). */
    public static void onMiss() {
        if (!inFight) return;
        swings++;
        combo = 0;
    }

    public static void tick(Minecraft mc) {
        tick++;
        if (mc.player == null) { reset(); return; }

        // The attack is confirmed once the target's hurt animation starts.
        if (pendingTarget != null) {
            // The hurt animation only counts down, so a jump up means a fresh, accepted hit.
            if (pendingTarget.hurtTime > pendingHurtBefore) {
                hits++;
                hitThisTick = true;
                combo++;
                longestCombo = Math.max(longestCombo, combo);
                lastHitAt = System.currentTimeMillis();
                pendingTarget = null;
            } else if (tick - pendingSince > CONFIRM_WINDOW_TICKS) {
                combo = 0;
                pendingTarget = null;
            }
        }

        // Taking damage breaks the combo and keeps the fight going.
        float health = mc.player.getHealth();
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead) sessionDeaths++;
        wasDead = dead;

        if (inFight && lastHealth >= 0 && health < lastHealth) {
            hitsTaken++;
            hurtThisTick = true;
            combo = 0;
            lastActionTick = tick;
        }
        lastHealth = health;

        if (!inFight) return;
        record(mc);
        // Dead, not merely gone: an opponent running out of view ends the fight by the timeout instead.
        boolean opponentDead = opponentEntity != null && opponentEntity.isDeadOrDying();
        if (opponentDead || tick - lastActionTick > FIGHT_OVER_TICKS || !mc.player.isAlive()) {
            endFight(opponentDead && mc.player.isAlive());
        }
    }

    private static void startFightIfNeeded(LivingEntity target) {
        if (inFight && target == opponentEntity) return;
        if (inFight) endFight(false);
        inFight = true;
        opponentEntity = target;
        opponent = target.getName().getString();
        hits = swings = combo = longestCombo = hitsTaken = 0;
        fightStart = System.currentTimeMillis();
        frames.clear();
    }

    private static void endFight(boolean won) {
        if (won) sessionKills++;
        if (won && opponentEntity != null) {
            var client = dev.crystal.client.CrystalClient.getInstance();
            var effect = client == null ? null : client.getModuleManager().getEnabled(dev.crystal.client.module.render.KillEffect.class);
            if (effect != null) effect.play(opponentEntity);
        }
        if (swings > 0) {
            lastRound = new Round(opponent, hits, swings, longestCombo, hitsTaken, System.currentTimeMillis() - fightStart, won);
            lastRoundAt = System.currentTimeMillis();
            save(lastRound);
        }
        inFight = false;
        opponentEntity = null;
        pendingTarget = null;
    }

    private static void reset() {
        inFight = false;
        opponentEntity = null;
        pendingTarget = null;
        lastHealth = -1;
    }

    /** One tick of the fight: me x,y,z,yaw,health, opponent x,y,z,yaw,health, then 1 for a hit landed and 2 for a hit taken. */
    private static void record(Minecraft mc) {
        if (frames.size() >= MAX_FRAMES || opponentEntity == null) {
            hitThisTick = hurtThisTick = false;
            return;
        }
        var me = mc.player;
        var op = opponentEntity;
        frames.add(new float[]{
                (float) me.getX(), (float) me.getY(), (float) me.getZ(), me.getYRot(), me.getHealth(),
                (float) op.getX(), (float) op.getY(), (float) op.getZ(), op.getYRot(), op.getHealth(),
                (hitThisTick ? 1 : 0) | (hurtThisTick ? 2 : 0)});
        hitThisTick = hurtThisTick = false;
    }

    /** Writes the fight for the launcher, off the game thread, and trims old ones. */
    private static void save(Round round) {
        if (frames.size() < 20) return; // under a second: nothing worth watching
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("version", 1);
        json.addProperty("start", fightStart);
        json.addProperty("opponent", round.opponent());
        json.addProperty("won", round.won());
        json.addProperty("hits", round.hits());
        json.addProperty("swings", round.swings());
        json.addProperty("longestCombo", round.longestCombo());
        json.addProperty("hitsTaken", round.hitsTaken());
        json.addProperty("durationMs", round.durationMs());
        var server = Minecraft.getInstance().getCurrentServer();
        json.addProperty("server", server != null ? server.ip : "Einzelspieler");
        com.google.gson.JsonArray list = new com.google.gson.JsonArray();
        for (float[] f : frames) {
            com.google.gson.JsonArray row = new com.google.gson.JsonArray();
            for (float v : f) row.add(Math.round(v * 100) / 100f);
            list.add(row);
        }
        json.add("frames", list);
        String text = json.toString();
        long start = fightStart;
        Thread.ofVirtual().start(() -> {
            try {
                java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve(".crystal").resolve("fights");
                java.nio.file.Files.createDirectories(dir);
                java.nio.file.Files.writeString(dir.resolve(start + ".json"), text);
                try (var files = java.nio.file.Files.list(dir)) {
                    var all = files.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList();
                    for (int i = 0; i < all.size() - KEEP_FIGHTS; i++) java.nio.file.Files.deleteIfExists(all.get(i));
                }
            } catch (Exception e) {
                dev.crystal.client.CrystalClient.LOGGER.debug("[Nexora] Fight not saved: {}", e.toString());
            }
        });
    }

    /** When the last confirmed hit landed (System.currentTimeMillis), 0 if none yet. */
    public static long lastHitAt() { return lastHitAt; }

    /** The round going on right now, or null outside a fight. */
    public static Round current() {
        return inFight ? new Round(opponent, hits, swings, longestCombo, hitsTaken, System.currentTimeMillis() - fightStart, false) : null;
    }

    public static Round lastRound() { return lastRound; }
    public static long lastRoundAt() { return lastRoundAt; }
    public static int combo() { return inFight ? combo : 0; }
    public static int sessionKills() { return sessionKills; }
    public static int sessionDeaths() { return sessionDeaths; }
}
