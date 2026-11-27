package ac.grim.grimac.checks.impl.ronex;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.nmsutil.Ray;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public final class RonexExtraChecks {
    private RonexExtraChecks() {}

    private static boolean isItem(ItemStack stack, String name) {
        if (stack == null || stack.isEmpty()) return false;
        String key = stack.getType().getName().getKey().toLowerCase(Locale.ROOT);
        return key.equals(name) || key.endsWith(":" + name);
    }

    private static int attackedEntityId(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            return new WrapperPlayClientAttack(event).getEntityId();
        }
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            return packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK
                    ? packet.getEntityId() : -1;
        }
        return -1;
    }

    private static double wrapDegrees(double value) {
        value %= 360.0;
        if (value >= 180.0) value -= 360.0;
        if (value < -180.0) value += 360.0;
        return value;
    }

    private static double coefficientOfVariation(Deque<Long> values) {
        if (values.isEmpty()) return Double.POSITIVE_INFINITY;
        double mean = 0.0;
        for (long value : values) mean += value;
        mean /= values.size();
        if (mean <= 0.0) return Double.POSITIVE_INFINITY;
        double variance = 0.0;
        for (long value : values) {
            double difference = value - mean;
            variance += difference * difference;
        }
        return Math.sqrt(variance / values.size()) / mean;
    }

    @CheckData(name = "BlockReach", stableKey = "ronexgrim.world.block_reach",
            description = "Detected block interaction beyond the conservative reach limit")
    public static class BlockReach extends Check implements PacketReceiveListener {
        private double maximumDistance;
        public BlockReach(GrimPlayer player) { super(player); }
        private double distance(Vector3i block) {
            double dx = block.getX() + 0.5 - player.x;
            double dy = block.getY() + 0.5 - (player.y + player.getEyeHeight());
            double dz = block.getZ() + 0.5 - player.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        private void reject(PacketReceiveEvent event, String detail) {
            if (flag(detail) && shouldModifyPackets()) {
                event.setCancelled(true);
                player.onPacketCancel();
            }
        }
        @Override public void onPacketReceive(PacketReceiveEvent event) {
            if (event.isCancelled() || player.getSetbackTeleportUtil().insideUnloadedChunk()) return;
            if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
                WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement(event);
                if (packet.getFace() != BlockFace.OTHER) {
                    double distance = distance(packet.getBlockPosition());
                    if (distance > maximumDistance) reject(event, "block-place-distance=" + formatOffset(distance));
                }
            } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
                WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
                if (packet.getAction() == DiggingAction.START_DIGGING) {
                    double distance = distance(packet.getBlockPosition());
                    if (distance > maximumDistance) reject(event, "block-break-distance=" + formatOffset(distance));
                }
            }
        }
        @Override public void onReload(ConfigManager config) {
            maximumDistance = Math.max(5.5, config.getDoubleElse("BlockReach.maximum-distance", 6.0));
        }
    }

    @CheckData(name = "ScaffoldB", stableKey = "ronexgrim.world.scaffold_air_bridge",
            description = "Repeated block placements beneath the player during descending movement", experimental = true)
    public static class ScaffoldB extends Check implements PacketReceiveListener, PostPredictionListener {
        private final Deque<Long> intervals = new ArrayDeque<>();
        private long lastPlacementAt;
        private double horizontalSpeed, verticalSpeed, minimumSpeed, minimumFallSpeed, maximumIntervalVariation;
        private int requiredPlacements;
        public ScaffoldB(GrimPlayer player) { super(player); }
        @Override public void onPredictionComplete(PredictionComplete complete) {
            horizontalSpeed = complete.isChecked()
                    ? Math.hypot(player.actualMovement.getX(), player.actualMovement.getZ()) : 0.0;
            verticalSpeed = complete.isChecked() ? player.actualMovement.getY() : 0.0;
        }
        @Override public void onPacketReceive(PacketReceiveEvent event) {
            if (event.isCancelled() || event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
                    || player.getSetbackTeleportUtil().insideUnloadedChunk()) return;
            WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement(event);
            Vector3i clicked = packet.getBlockPosition();
            if (packet.getFace() == BlockFace.OTHER || horizontalSpeed < minimumSpeed
                    || verticalSpeed > -minimumFallSpeed || player.onGround || player.lastOnGround
                    || player.inVehicle() || player.isFlying || player.isGliding || player.isSwimming
                    || clicked.getY() >= Math.floor(player.y) - 1.0) {
                if (verticalSpeed >= -minimumFallSpeed) reset();
                return;
            }
            long now = System.currentTimeMillis();
            if (lastPlacementAt != 0L) {
                long interval = now - lastPlacementAt;
                if (interval > 0L && interval <= 800L) {
                    intervals.addLast(interval);
                    while (intervals.size() > requiredPlacements - 1) intervals.removeFirst();
                } else reset();
            }
            lastPlacementAt = now;
            if (intervals.size() >= requiredPlacements - 1) {
                double variation = coefficientOfVariation(intervals);
                if (variation <= maximumIntervalVariation) {
                    flag("descending-bridge-placements=" + (intervals.size() + 1)
                            + " interval-cv=" + formatOffset(variation)
                            + " vertical-speed=" + formatOffset(verticalSpeed));
                    reset();
                }
            }
        }
        private void reset() { intervals.clear(); lastPlacementAt = 0L; }
        @Override public void onReload(ConfigManager config) {
            minimumSpeed = Math.max(0.05, config.getDoubleElse("ScaffoldB.minimum-speed", 0.10));
            minimumFallSpeed = Math.max(0.20, config.getDoubleElse("ScaffoldB.minimum-fall-speed", 0.24));
            maximumIntervalVariation = Math.max(0.05,
                    config.getDoubleElse("ScaffoldB.maximum-interval-variation", 0.35));
            requiredPlacements = Math.max(6, (int) config.getDoubleElse("ScaffoldB.required-placements", 6));
            if (intervals != null) reset();
        }
    }

    @CheckData(name = "ScaffoldC", stableKey = "ronexgrim.world.scaffold_look_mismatch",
            description = "Repeated block placements while the view ray missed the clicked block", experimental = true)
    public static class ScaffoldC extends Check implements PacketReceiveListener {
        private final Deque<Boolean> placementMisses = new ArrayDeque<>();
        private double minimumSpeed, maximumDistance;
        private int window, required;
        public ScaffoldC(GrimPlayer player) { super(player); }
        private boolean rayHitsBlock(Vector3i block, float yaw, float pitch) {
            SimpleCollisionBox box = new SimpleCollisionBox(block);
            Ray ray = new Ray(player, player.x, player.y + player.getEyeHeight(), player.z, yaw, pitch);
            return box.intersectsRay(ray, 0.0F, (float) maximumDistance) != null;
        }
        @Override public void onPacketReceive(PacketReceiveEvent event) {
            if (event.isCancelled() || event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
                    || player.getSetbackTeleportUtil().insideUnloadedChunk()
                    || player.inVehicle() || player.isFlying || player.isGliding || player.isSwimming
                    || player.packetStateData.lastPacketWasTeleport || player.hasInventoryOpen) return;
            WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement(event);
            if (packet.getFace() == BlockFace.OTHER
                    || Math.hypot(player.actualMovement.getX(), player.actualMovement.getZ()) < minimumSpeed) return;
            Vector3i clicked = packet.getBlockPosition();
            if (!player.compensatedWorld.isChunkLoaded(clicked.getX() >> 4, clicked.getZ() >> 4)
                    || !player.compensatedWorld.getBlock(clicked).getType().isBlocking()) return;
            boolean currentLook = rayHitsBlock(clicked, player.yaw, player.pitch);
            boolean previousLook = rayHitsBlock(clicked, player.lastYaw, player.lastPitch);
            boolean suspicious = !currentLook && !previousLook;
            placementMisses.addLast(suspicious);
            while (placementMisses.size() > window) placementMisses.removeFirst();
            int misses = 0;
            for (boolean sample : placementMisses) if (sample) misses++;
            if (placementMisses.size() == window && misses >= required) {
                flag("clicked-block-ray-misses=" + misses + "/" + window);
                placementMisses.clear();
            } else if (!suspicious) reward();
        }
        @Override public void onReload(ConfigManager config) {
            minimumSpeed = Math.max(0.04, config.getDoubleElse("ScaffoldC.minimum-speed", 0.08));
            maximumDistance = Math.max(3.5, config.getDoubleElse("ScaffoldC.maximum-distance", 5.0));
            window = Math.max(6, (int) config.getDoubleElse("ScaffoldC.window", 8));
            required = Math.min(window, Math.max(4, (int) config.getDoubleElse("ScaffoldC.required", 5)));
            if (placementMisses != null) placementMisses.clear();
        }
    }

    @CheckData(name = "AutoCrystalA", stableKey = "ronexgrim.combat.autocrystal_cycle",
            description = "Detected repeated rapid end-crystal placement and break cycles", experimental = true)
    public static class AutoCrystalA extends Check implements PacketReceiveListener {
        private final Deque<Long> cycles = new ArrayDeque<>();
        private long lastCrystalUse;
        private long windowMs;
        private int requiredCycles;
        public AutoCrystalA(GrimPlayer player) { super(player); }
        @Override public void onPacketReceive(PacketReceiveEvent event) {
            if (event.isCancelled()) return;
            long now = System.currentTimeMillis();
            if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
                WrapperPlayClientUseItem use = new WrapperPlayClientUseItem(event);
                if (isItem(player.inventory.getItemInHand(use.getHand()), "end_crystal")) lastCrystalUse = now;
                return;
            }
            if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
                WrapperPlayClientPlayerBlockPlacement place = new WrapperPlayClientPlayerBlockPlacement(event);
                if (isItem(player.inventory.getItemInHand(place.getHand()), "end_crystal")) lastCrystalUse = now;
                return;
            }
            int entityId = attackedEntityId(event);
            if (entityId < 0 || lastCrystalUse == 0L || now - lastCrystalUse > 1000L) return;
            var target = player.compensatedEntities.entityMap.get(entityId);
            if (target == null || target.getType() != EntityTypes.END_CRYSTAL) return;
            cycles.addLast(now);
            while (!cycles.isEmpty() && now - cycles.peekFirst() > windowMs) cycles.removeFirst();
            lastCrystalUse = 0L;
            if (cycles.size() >= requiredCycles) {
                flag("rapid-crystal-place-break-cycles=" + cycles.size() + " window-ms=" + windowMs);
                cycles.clear();
            }
        }
        @Override public void onReload(ConfigManager config) {
            windowMs = Math.max(1000L, Math.min(2500L,
                    (long) config.getDoubleElse("AutoCrystalA.window-ms", 2000)));
            requiredCycles = Math.max(6, (int) config.getDoubleElse("AutoCrystalA.required-cycles", 6));
            if (cycles != null) cycles.clear();
            lastCrystalUse = 0L;
        }
    }

    @CheckData(name = "ChestStealerA", stableKey = "ronexgrim.inventory.container_click_burst",
            description = "Moved items out of a container through an implausibly fast burst of inventory clicks",
            experimental = true)
    public static class ChestStealerA extends Check implements PacketReceiveListener {
        private final Deque<Long> clicks = new ArrayDeque<>();
        private final Deque<Long> quickMoves = new ArrayDeque<>();
        private long windowMs;
        private int maxClicks;
        private int minQuickMoves;
        public ChestStealerA(GrimPlayer player) { super(player); }
        @Override public void onPacketReceive(PacketReceiveEvent event) {
            if (event.isCancelled() || event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) return;
            WrapperPlayClientClickWindow click = new WrapperPlayClientClickWindow(event);
            if (click.getWindowId() <= 0) return;
            WindowClickType type = click.getWindowClickType();
            if (type != WindowClickType.QUICK_MOVE && type != WindowClickType.PICKUP) return;
            long now = System.currentTimeMillis();
            clicks.addLast(now);
            if (type == WindowClickType.QUICK_MOVE) quickMoves.addLast(now);
            trim(clicks, now);
            trim(quickMoves, now);
            if (clicks.size() >= maxClicks && quickMoves.size() >= minQuickMoves) {
                flag("container-clicks=" + clicks.size() + " quick-moves=" + quickMoves.size()
                        + " window-ms=" + windowMs);
                clicks.clear();
                quickMoves.clear();
            }
        }
        private void trim(Deque<Long> values, long now) {
            while (!values.isEmpty() && now - values.peekFirst() > windowMs) values.removeFirst();
        }
        @Override public void onReload(ConfigManager config) {
            windowMs = Math.max(100L, (long) config.getDoubleElse("ChestStealerA.window-ms", 250));
            maxClicks = Math.max(5, (int) config.getDoubleElse("ChestStealerA.max-clicks", 10));
            minQuickMoves = Math.max(3, (int) config.getDoubleElse("ChestStealerA.min-quick-moves", 6));
            if (clicks != null) clicks.clear();
            if (quickMoves != null) quickMoves.clear();
        }
    }

    @CheckData(name = "BaritoneB", stableKey = "ronexgrim.movement.baritone_turn_regularity",
            description = "Repeated highly uniform movement-linked rotation corrections", experimental = true)
    public static class BaritoneB extends Check
            implements RotationListener, PostPredictionListener, PacketReceiveListener {
        private final Deque<Long> turnMagnitudes = new ArrayDeque<>();
        private final Deque<Long> turnIntervals = new ArrayDeque<>();
        private long lastMovingAt, lastTurnAt, lastCombatAt;
        private int requiredTurns;
        private double minimumTurnDegrees, maximumTurnDegrees, maximumAngleVariation, maximumIntervalVariation, minimumSpeed;
        public BaritoneB(GrimPlayer player) { super(player); }
        @Override public void onPredictionComplete(PredictionComplete complete) {
            long now = System.nanoTime();
            double speed = Math.hypot(player.actualMovement.getX(), player.actualMovement.getZ());
            if (!complete.isChecked() || player.inVehicle() || player.isFlying || player.isGliding
                    || player.isSwimming || player.isClimbing || player.wasTouchingWater || player.isRiptidePose
                    || player.packetStateData.lastPacketWasTeleport || speed < minimumSpeed) {
                lastMovingAt = 0L;
                clearTurns();
                return;
            }
            lastMovingAt = now;
        }
        @Override public void onPacketReceive(PacketReceiveEvent event) {
            if (attackedEntityId(event) >= 0) {
                lastCombatAt = System.nanoTime();
                clearTurns();
            }
        }
        @Override public void process(RotationUpdate update) {
            long now = System.nanoTime();
            if (lastMovingAt == 0L || now - lastMovingAt > 300_000_000L
                    || (lastCombatAt != 0L && now - lastCombatAt < 3_000_000_000L)) {
                clearTurns();
                return;
            }
            double delta = Math.abs(wrapDegrees(update.deltaYaw()));
            if (delta < minimumTurnDegrees || delta > maximumTurnDegrees) return;
            if (lastTurnAt != 0L) {
                long interval = now - lastTurnAt;
                if (interval > 0L && interval <= 1_000_000_000L) {
                    turnMagnitudes.addLast(Math.round(delta * 1000.0));
                    turnIntervals.addLast(interval);
                    while (turnMagnitudes.size() > requiredTurns) turnMagnitudes.removeFirst();
                    while (turnIntervals.size() > requiredTurns) turnIntervals.removeFirst();
                } else clearTurns();
            }
            lastTurnAt = now;
            if (turnMagnitudes.size() >= requiredTurns && turnIntervals.size() >= requiredTurns) {
                double angleVariation = coefficientOfVariation(turnMagnitudes);
                double intervalVariation = coefficientOfVariation(turnIntervals);
                if (angleVariation <= maximumAngleVariation && intervalVariation <= maximumIntervalVariation) {
                    flag("turn-angle-cv=" + formatOffset(angleVariation)
                            + " interval-cv=" + formatOffset(intervalVariation) + " turns=" + turnMagnitudes.size());
                    clearTurns();
                }
            }
        }
        private void clearTurns() {
            turnMagnitudes.clear();
            turnIntervals.clear();
            lastTurnAt = 0L;
        }
        @Override public void onReload(ConfigManager config) {
            requiredTurns = Math.max(6, (int) config.getDoubleElse("BaritoneB.required-turns", 6));
            minimumTurnDegrees = Math.max(5.0, config.getDoubleElse("BaritoneB.minimum-turn-degrees", 12.0));
            maximumTurnDegrees = Math.max(minimumTurnDegrees + 1.0,
                    config.getDoubleElse("BaritoneB.maximum-turn-degrees", 90.0));
            maximumAngleVariation = Math.max(0.02,
                    config.getDoubleElse("BaritoneB.maximum-angle-variation", 0.10));
            maximumIntervalVariation = Math.max(0.02,
                    config.getDoubleElse("BaritoneB.maximum-interval-variation", 0.15));
            minimumSpeed = Math.max(0.04, config.getDoubleElse("BaritoneB.minimum-speed", 0.08));
        }
    }

    @CheckData(name = "BadDig", stableKey = "ronexgrim.world.bad_dig",
            description = "Detects repeated block-dig completion packets arriving implausibly soon after starts",
            experimental = true)
    public static class BadDig extends Check implements PacketReceiveListener {
        private final Deque<Boolean> samples = new ArrayDeque<>();
        private String activeBlock = "";
        private long startedAt, minimumDurationMs;
        private int window, required;
        public BadDig(GrimPlayer player) { super(player); }
        @Override public void onPacketReceive(PacketReceiveEvent event) {
            if (event.isCancelled() || event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) return;
            WrapperPlayClientPlayerDigging dig = new WrapperPlayClientPlayerDigging(event);
            String action = dig.getAction().name();
            String block = String.valueOf(dig.getBlockPosition());
            long now = System.currentTimeMillis();
            if (action.equals("START_DIGGING")) {
                activeBlock = block;
                startedAt = now;
                return;
            }
            if (action.equals("CANCELLED_DIGGING")) {
                activeBlock = "";
                startedAt = 0L;
                return;
            }
            if (!action.equals("FINISHED_DIGGING") || startedAt == 0L || !block.equals(activeBlock)) return;
            long elapsed = now - startedAt;
            boolean suspicious = elapsed >= 0L && elapsed < minimumDurationMs;
            samples.addLast(suspicious);
            while (samples.size() > window) samples.removeFirst();
            int count = 0;
            for (boolean sample : samples) if (sample) count++;
            if (samples.size() == window && count >= required) {
                flag("fast-dig-completions=" + count + "/" + window + " elapsed-ms=" + elapsed);
                samples.clear();
            } else if (!suspicious) reward();
            activeBlock = "";
            startedAt = 0L;
        }
        @Override public void onReload(ConfigManager config) {
            minimumDurationMs = Math.max(15L, (long) config.getDoubleElse("BadDig.minimum-duration-ms", 45));
            window = Math.max(5, (int) config.getDoubleElse("BadDig.window", 7));
            required = Math.min(window, Math.max(3, (int) config.getDoubleElse("BadDig.required", 5)));
            if (samples != null) samples.clear();
            activeBlock = "";
            startedAt = 0L;
        }
    }
}
