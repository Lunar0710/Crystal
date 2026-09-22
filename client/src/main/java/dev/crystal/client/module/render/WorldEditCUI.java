package dev.crystal.client.module.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.ColorSetting;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.ModuleCategory;
import dev.crystal.client.module.Setting;
import dev.crystal.client.module.SliderSetting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows your WorldEdit selection in the world, like the WorldEditCUI mod.
 *
 * Servers with WorldEdit send the selection over the "worldedit:cui" plugin
 * channel once the client announces itself with "v|4". Supported shapes:
 * cuboid (//wand, //pos1, //pos2) and polygon (//sel poly). Other selection
 * types are shown as the box around their points.
 */
public class WorldEditCUI extends Module {

    public record CuiPayload(String message) implements CustomPacketPayload {
        public static final Type<CuiPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("worldedit", "cui"));
        public static final StreamCodec<FriendlyByteBuf, CuiPayload> CODEC = CustomPacketPayload.codec(
                (value, buf) -> buf.writeBytes(value.message.getBytes(StandardCharsets.UTF_8)),
                buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new CuiPayload(new String(bytes, StandardCharsets.UTF_8));
                });

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    private static final int PROTOCOL = 4;

    private int boxColor = 0xFFEF4444;
    private int gridColor = 0xFF5B8AF5;
    private float lineWidth = 2f;

    private String shape = "cuboid";
    /** Cuboid corners (index 0 and 1), or null when not set yet. */
    private final int[][] corners = new int[2][];
    /** Polygon points as x,z pairs. */
    private final List<int[]> polygon = new ArrayList<>();
    private int minY, maxY;

    private boolean handshakeSent = false;
    private int ticksInWorld = 0;

    public WorldEditCUI() {
        super("WorldEditCUI", "Shows your WorldEdit selection as a box in the world", ModuleCategory.RENDER);
        PayloadTypeRegistry.playS2C().register(CuiPayload.ID, CuiPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CuiPayload.ID, CuiPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(CuiPayload.ID, (payload, context) -> handle(payload.message()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        CrystalClient.getInstance().getEventBus().subscribe(TickEvent.class, e -> tick());
    }

    private void reset() {
        handshakeSent = false;
        ticksInWorld = 0;
        clearSelection();
    }

    private void clearSelection() {
        corners[0] = corners[1] = null;
        polygon.clear();
    }

    /** Announces the client a moment after joining, once the server has registered its channels. */
    private void tick() {
        if (!isEnabled() || handshakeSent || mc.player == null) return;
        if (++ticksInWorld < 40) return;
        if (ClientPlayNetworking.canSend(CuiPayload.ID)) {
            ClientPlayNetworking.send(new CuiPayload("v|" + PROTOCOL));
        }
        handshakeSent = true;
    }

    private void handle(String message) {
        String[] parts = message.split("\\|");
        try {
            switch (parts[0]) {
                case "s" -> {
                    shape = parts.length > 1 ? parts[1] : "cuboid";
                    clearSelection();
                }
                case "p" -> {
                    int index = Integer.parseInt(parts[1]);
                    if (index == 0 || index == 1) {
                        corners[index] = new int[]{(int) Double.parseDouble(parts[2]), (int) Double.parseDouble(parts[3]), (int) Double.parseDouble(parts[4])};
                    }
                }
                case "p2" -> {
                    int index = Integer.parseInt(parts[1]);
                    int[] point = {Integer.parseInt(parts[2]), Integer.parseInt(parts[3])};
                    while (polygon.size() <= index) polygon.add(null);
                    polygon.set(index, point);
                }
                case "mm" -> {
                    minY = Integer.parseInt(parts[1]);
                    maxY = Integer.parseInt(parts[2]);
                }
                default -> { /* colours, grids and other shapes aren't drawn */ }
            }
        } catch (RuntimeException e) {
            CrystalClient.LOGGER.debug("[Nexora] Ignored WorldEdit CUI message: {}", message);
        }
    }

    /** Called from WorldRenderHandler after entities are drawn. */
    public void render(PoseStack.Pose entry, VertexConsumer lines, Vec3 camera) {
        if ("polygon2d".equals(shape) && polygon.stream().filter(p -> p != null).count() >= 2) {
            List<int[]> points = polygon.stream().filter(p -> p != null).toList();
            double bottom = minY, top = maxY + 1;
            for (int i = 0; i < points.size(); i++) {
                int[] a = points.get(i), b = points.get((i + 1) % points.size());
                double ax = a[0] + 0.5, az = a[1] + 0.5, bx = b[0] + 0.5, bz = b[1] + 0.5;
                line(entry, lines, camera, ax, bottom, az, bx, bottom, bz, boxColor);
                line(entry, lines, camera, ax, top, az, bx, top, bz, boxColor);
                line(entry, lines, camera, ax, bottom, az, ax, top, az, gridColor);
            }
            return;
        }

        int[] a = corners[0], b = corners[1];
        if (a == null && b == null) return;
        if (a == null) a = b;
        if (b == null) b = a;
        double x1 = Math.min(a[0], b[0]), y1 = Math.min(a[1], b[1]), z1 = Math.min(a[2], b[2]);
        double x2 = Math.max(a[0], b[0]) + 1, y2 = Math.max(a[1], b[1]) + 1, z2 = Math.max(a[2], b[2]) + 1;
        box(entry, lines, camera, x1 - 0.005, y1 - 0.005, z1 - 0.005, x2 + 0.005, y2 + 0.005, z2 + 0.005, boxColor);
        // The two clicked blocks themselves, so you can see which corner is which.
        if (corners[0] != null) blockMarker(entry, lines, camera, corners[0], 0xFF22C55E);
        if (corners[1] != null) blockMarker(entry, lines, camera, corners[1], gridColor);
    }

    private void blockMarker(PoseStack.Pose m, VertexConsumer lines, Vec3 cam, int[] p, int color) {
        box(m, lines, cam, p[0] + 0.02, p[1] + 0.02, p[2] + 0.02, p[0] + 0.98, p[1] + 0.98, p[2] + 0.98, color);
    }

    private void box(PoseStack.Pose m, VertexConsumer l, Vec3 c, double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        line(m, l, c, x1, y1, z1, x2, y1, z1, color); line(m, l, c, x1, y2, z1, x2, y2, z1, color);
        line(m, l, c, x1, y1, z2, x2, y1, z2, color); line(m, l, c, x1, y2, z2, x2, y2, z2, color);
        line(m, l, c, x1, y1, z1, x1, y2, z1, color); line(m, l, c, x2, y1, z1, x2, y2, z1, color);
        line(m, l, c, x1, y1, z2, x1, y2, z2, color); line(m, l, c, x2, y1, z2, x2, y2, z2, color);
        line(m, l, c, x1, y1, z1, x1, y1, z2, color); line(m, l, c, x2, y1, z1, x2, y1, z2, color);
        line(m, l, c, x1, y2, z1, x1, y2, z2, color); line(m, l, c, x2, y2, z1, x2, y2, z2, color);
    }

    private void line(PoseStack.Pose entry, VertexConsumer lines, Vec3 cam,
                      double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        Vector3f normal = new Vector3f((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1)).normalize();
        // Line width is a vertex attribute from 1.21.11 on.
        //? if >=1.21.11 {
        lines.addVertex(entry, (float) (x1 - cam.x), (float) (y1 - cam.y), (float) (z1 - cam.z)).setColor(color).setNormal(entry, normal).setLineWidth(lineWidth);
        lines.addVertex(entry, (float) (x2 - cam.x), (float) (y2 - cam.y), (float) (z2 - cam.z)).setColor(color).setNormal(entry, normal).setLineWidth(lineWidth);
        //?} else {
        /*lines.addVertex(entry, (float) (x1 - cam.x), (float) (y1 - cam.y), (float) (z1 - cam.z)).setColor(color).setNormal(entry, normal);
        lines.addVertex(entry, (float) (x2 - cam.x), (float) (y2 - cam.y), (float) (z2 - cam.z)).setColor(color).setNormal(entry, normal);
        *///?}
    }

    @Override
    public List<Setting<?>> getSettings() {
        return List.of(
                new ColorSetting("Selection Color", () -> boxColor, v -> boxColor = v, 0xFFEF4444),
                new ColorSetting("Second Corner Color", () -> gridColor, v -> gridColor = v, 0xFF5B8AF5),
                new SliderSetting("Line Width", () -> lineWidth, v -> lineWidth = v, 1f, 6f, 0.5f, 1));
    }
}
