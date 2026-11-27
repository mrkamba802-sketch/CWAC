package ac.grim.grimac.checks.impl.ronex;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.impl.aim.AimHitCheck;
import ac.grim.grimac.checks.impl.aim.processor.AimProcessor;
import ac.grim.grimac.checks.type.BlockPlaceListener;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockPlace;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.nmsutil.Ray;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public final class RonexNewChecks {
    private RonexNewChecks() {}

    private static boolean isItem(ItemStack stack, String itemName) {
        if (stack == null || stack.isEmpty() || stack.getType() == null) return false;
        String key = stack.getType().getName().getKey().toLowerCase(Locale.ROOT);
        return key.equals(itemName) || key.endsWith(":" + itemName);
    }

    private static boolean isSwing(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.PUNCH) return true;
        return event.getPacketType() == PacketType.Play.Client.ANIMATION
                && new WrapperPlayClientAnimation(event).getHand() == InteractionHand.MAIN_HAND;
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

    private static double distanceToInteger(double value) {
        return Math.abs(value - Math.rint(value));
    }

    private static void addWindowSample(Deque<Boolean> samples, boolean value, int window) {
        samples.addLast(value);
        while (samples.size() > window) samples.removeFirst();
    }

    private static int countTrue(Deque<Boolean> samples) {
        int count = 0;
        for (boolean sample : samples) if (sample) count++;
        return count;
    }

    @CheckData(name = "AutoClickerA", stableKey = "cwanticheat.combat.auto_clicker_a",
            description = "Repeatedly near-identical main-hand swing intervals", experimental = true)
    public static class AutoClickerA extends Check implements PacketReceiveListener {
        private final Deque<Long> intervals = new ArrayDeque<>();
        private long lastSwingAt;
        private int sampleCount, requiredWindows, regularWindows;
        private long minimumIntervalNanos, maximumIntervalNanos;
        private double maximumCoefficientOfVariation;

        public AutoClickerA(GrimPlayer player) {
            super(player);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.isCancelled() || !isSwing(event)) return;
            long now = System.nanoTime();
            if (lastSwingAt != 0L) {
                long interval = now - lastSwingAt;
                if (interval < minimumIntervalNanos || interval > maximumIntervalNanos) {
                    intervals.clear();
                    regularWindows = 0;
                } else {
                    intervals.addLast(interval);
                    while (intervals.size() > sampleCount) intervals.removeFirst();
                    if (intervals.size() == sampleCount) {
                        double mean = 0.0;
                        for (long value : intervals) mean += value;
                        mean /= intervals.size();
                        double variance = 0.0;
                        for (long value : intervals) {
                            double difference = value - mean;
                            variance += difference * difference;
                        }
                        double coefficient = mean <= 0.0 ? 1.0
                                : Math.sqrt(variance / intervals.size()) / mean;
                        if (coefficient <= maximumCoefficientOfVariation) {
                            regularWindows++;
                            if (regularWindows >= requiredWindows) {
                                flag("regular-swing-cv=" + formatOffset(coefficient)
                                        + " intervals=" + intervals.size()
                                        + " windows=" + regularWindows);
                                intervals.clear();
                                regularWindows = 0;
                            }
                        } else {
                            regularWindows = 0;
                        }
                    }
                }
            }
            lastSwingAt = now;
        }

        @Override
        public void onReload(@NotNull ConfigManager config) {
            sampleCount = Math.max(12, config.getIntElse(getConfigName() + ".sample-count", 16));
            requiredWindows = Math.max(2, config.getIntElse(getConfigName() + ".required-windows", 3));
            minimumIntervalNanos = Math.max(20L,
                    config.getIntElse(getConfigName() + ".minimum-interval-ms", 35)) * 1_000_000L;
            maximumIntervalNanos = Math.max(60L,
                    config.getIntElse(getConfigName() + ".maximum-interval-ms", 160)) * 1_000_000L;
            maximumCoefficientOfVariation = Math.max(0.005,
                    Math.min(0.08, config.getDoubleElse(getConfigName() + ".maximum-cv", 0.025)));
            intervals.clear();
            lastSwingAt = 0L;
            regularWindows = 0;
        }
    }

    @CheckData(name = "MaceSwapA", stableKey = "cwanticheat.inventory.mace_swap_a",
            description = "Repeated rapid mace hotbar swap, attack, and return cycles", experimental = true)
    public static class MaceSwapA extends Check implements PacketReceiveListener {
        private final Deque<Long> cycles = new ArrayDeque<>();
        private int lastObservedSlot = -1;
        private int originalSlot = -1;
        private int maceSlot = -1;
        private long maceSelectedAt;
        private boolean attackedWithMace;
        private long attackAt;
        private long maximumCycleNanos, maximumAttackDelayNanos, cycleWindowNanos;
        private int requiredCycles;

        public MaceSwapA(GrimPlayer player) {
            super(player);
            lastObservedSlot = player.packetStateData.lastSlotSelected;
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            long now = System.nanoTime();
            if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
                int selectedSlot = new WrapperPlayClientHeldItemChange(event).getSlot();
                if (selectedSlot < 0 || selectedSlot > 8) return;
                if (maceSlot >= 0 && selectedSlot == originalSlot) {
                    long cycle = now - maceSelectedAt;
                    long attackAge = attackedWithMace ? now - attackAt : Long.MAX_VALUE;
                    if (attackedWithMace && cycle <= maximumCycleNanos
                            && attackAge <= maximumAttackDelayNanos) {
                        while (!cycles.isEmpty() && now - cycles.peekFirst() > cycleWindowNanos) {
                            cycles.removeFirst();
                        }
                        cycles.addLast(now);
                        if (cycles.size() >= requiredCycles) {
                            flag("mace-swap-cycles=" + cycles.size()
                                    + " cycle-ms=" + (cycle / 1_000_000L)
                                    + " attack-age-ms=" + (attackAge / 1_000_000L));
                            cycles.clear();
                        }
                    } else {
                        while (!cycles.isEmpty() && now - cycles.peekFirst() > cycleWindowNanos) {
                            cycles.removeFirst();
                        }
                    }
                    resetCycle();
                } else if (maceSlot >= 0 && selectedSlot != maceSlot) {
                    resetCycle();
                } else if (maceSlot < 0 && selectedSlot != lastObservedSlot
                        && isItem(player.inventory.getHeldItem(), "mace")) {
                    originalSlot = lastObservedSlot;
                    maceSlot = selectedSlot;
                    maceSelectedAt = now;
                    attackedWithMace = false;
                    attackAt = 0L;
                }
                lastObservedSlot = selectedSlot;
                return;
            }

            if (attackedEntityId(event) >= 0 && maceSlot >= 0 && !attackedWithMace
                    && now - maceSelectedAt <= maximumAttackDelayNanos
                    && isItem(player.inventory.getHeldItem(), "mace")) {
                attackedWithMace = true;
                attackAt = now;
            }
        }

        private void resetCycle() {
            originalSlot = -1;
            maceSlot = -1;
            maceSelectedAt = 0L;
            attackedWithMace = false;
            attackAt = 0L;
        }

        @Override
        public void onReload(@NotNull ConfigManager config) {
            maximumCycleNanos = Math.max(180L,
                    config.getIntElse(getConfigName() + ".maximum-cycle-ms", 700)) * 1_000_000L;
            maximumAttackDelayNanos = Math.max(100L,
                    config.getIntElse(getConfigName() + ".maximum-attack-delay-ms", 350)) * 1_000_000L;
            cycleWindowNanos = Math.max(1500L,
                    config.getIntElse(getConfigName() + ".cycle-window-ms", 5000)) * 1_000_000L;
            requiredCycles = Math.max(2, config.getIntElse(getConfigName() + ".required-cycles", 3));
            cycles.clear();
            resetCycle();
        }
    }

    @CheckData(name = "MorePacketsA", stableKey = "cwanticheat.packet.more_packets_a",
            description = "Sustained excess player movement packet rate", experimental = true)
    public static class MorePacketsA extends Check implements PacketReceiveListener {
        private final Deque<Long> movementPackets = new ArrayDeque<>();
        private long lastEvaluationAt;
        private long windowNanos;
        private int maximumPackets, requiredWindows, evidence;

        public MorePacketsA(GrimPlayer player) {
            super(player);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
            if (player.inVehicle() || player.packetStateData.lastPacketWasTeleport
                    || !player.cameraEntity.isSelf() || !player.isTickingReliablyFor(3)) {
                movementPackets.clear();
                evidence = 0;
                lastEvaluationAt = System.nanoTime();
                return;
            }
            long now = System.nanoTime();
            movementPackets.addLast(now);
            while (!movementPackets.isEmpty() && now - movementPackets.peekFirst() > windowNanos) {
                movementPackets.removeFirst();
            }
            if (lastEvaluationAt == 0L) lastEvaluationAt = now;
            if (now - lastEvaluationAt >= windowNanos) {
                if (movementPackets.size() >= maximumPackets) {
                    evidence++;
                    if (evidence >= requiredWindows) {
                        flag("movement-packets-per-second=" + movementPackets.size()
                                + " evidence-windows=" + evidence);
                        evidence = 0;
                    }
                } else {
                    evidence = Math.max(0, evidence - 1);
                }
                lastEvaluationAt = now;
            }
        }

        @Override
        public void onReload(@NotNull ConfigManager config) {
            windowNanos = Math.max(750L,
                    config.getIntElse(getConfigName() + ".window-ms", 1000)) * 1_000_000L;
            maximumPackets = Math.max(24,
                    config.getIntElse(getConfigName() + ".maximum-packets", 34));
            requiredWindows = Math.max(2,
                    config.getIntElse(getConfigName() + ".required-windows", 3));
            movementPackets.clear();
            evidence = 0;
            lastEvaluationAt = 0L;
        }
    }

    @CheckData(name = "ScaffoldD", stableKey = "cwanticheat.scaffold.down_snap_place",
            description = "Repeated down-snap rotations around sprinting underfoot placements", experimental = true)
    public static class ScaffoldD extends Check implements PacketReceiveListener, RotationListener, BlockPlaceListener {
        private static final long SNAP_WINDOW_NANOS = 300_000_000L;
        private static final long SWING_WINDOW_NANOS = 150_000_000L;
        private final Deque<PendingPlace> pendingPlaces = new ArrayDeque<>();
        private final Deque<Boolean> evidenceWindow = new ArrayDeque<>();
        private double minimumDownSnap, minimumReturnSnap;
        private int requiredEvidence, evidenceWindowSize;
        private long downSnapAt, lastReturnedSnapAt, lastSwingAt, lastSwingConsumedAt;
        private final boolean legacySwingBeforePlace;

        public ScaffoldD(GrimPlayer player) {
            super(player);
            legacySwingBeforePlace = player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8);
        }

        @Override
        public void process(RotationUpdate update) {
            long now = System.nanoTime();
            if (player.packetStateData.lastPacketWasTeleport || player.inVehicle()
                    || !player.cameraEntity.isSelf()) {
                downSnapAt = 0L;
                lastReturnedSnapAt = 0L;
                pendingPlaces.clear();
                return;
            }

            double pitchDelta = Math.abs(update.deltaPitch());
            if (update.oldPitch() < 65.0f && update.newPitch() >= 75.0f
                    && update.newPitch() <= 85.0f && pitchDelta >= minimumDownSnap) {
                downSnapAt = now;
            } else if (downSnapAt != 0L && now - downSnapAt <= SNAP_WINDOW_NANOS
                    && update.oldPitch() >= 70.0f && update.newPitch() < 60.0f
                    && pitchDelta >= minimumReturnSnap) {
                lastReturnedSnapAt = now;
                for (PendingPlace pending : pendingPlaces) {
                    if (now - pending.placedAt <= SNAP_WINDOW_NANOS) pending.returned = true;
                }
                downSnapAt = 0L;
            }

            finalizeExpiredPlaces(now);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            long now = System.nanoTime();
            if (isSwing(event)) {
                lastSwingAt = now;
                if (!legacySwingBeforePlace) {
                    for (PendingPlace pending : pendingPlaces) {
                        long delay = now - pending.placedAt;
                        if (delay >= 0L && delay <= SWING_WINDOW_NANOS) pending.hasSwing = true;
                    }
                }
            }
            finalizeExpiredPlaces(now);
        }

        @Override
        public void onBlockPlace(BlockPlace place) {
            long now = System.nanoTime();
            if (!player.isSprinting || player.inVehicle() || player.isFlying || player.isGliding
                    || player.isSwimming || player.hasInventoryOpen || player.packetStateData.lastPacketWasTeleport
                    || !player.isTickingReliablyFor(3)) return;

            int feetY = (int) Math.floor(player.y);
            int blockY = place.position.getY();
            double dx = place.position.getX() + 0.5 - player.x;
            double dz = place.position.getZ() + 0.5 - player.z;
            boolean underfoot = blockY <= feetY && blockY >= feetY - 1
                    && Math.hypot(dx, dz) <= 1.6;
            if (!underfoot) return;

            boolean recentDownSnap = downSnapAt != 0L && now - downSnapAt <= SNAP_WINDOW_NANOS;
            boolean recentReturn = lastReturnedSnapAt != 0L
                    && now - lastReturnedSnapAt <= SNAP_WINDOW_NANOS;
            if (!recentDownSnap && !recentReturn) return;

            boolean swingBeforePlace = legacySwingBeforePlace && lastSwingAt != 0L
                    && now >= lastSwingAt && now - lastSwingAt <= SWING_WINDOW_NANOS
                    && lastSwingAt > lastSwingConsumedAt;
            if (swingBeforePlace) lastSwingConsumedAt = lastSwingAt;
            PendingPlace pending = new PendingPlace(now, true, recentReturn, swingBeforePlace);
            pendingPlaces.addLast(pending);
            finalizeExpiredPlaces(now);
        }

        private void finalizeExpiredPlaces(long now) {
            while (!pendingPlaces.isEmpty()
                    && now - pendingPlaces.peekFirst().placedAt > SWING_WINDOW_NANOS) {
                PendingPlace pending = pendingPlaces.removeFirst();
                boolean suspicious = pending.hasSnap && pending.returned
                        && pending.sprinting && !pending.hasSwing;
                addWindowSample(evidenceWindow, suspicious, evidenceWindowSize);
                if (evidenceWindow.size() == evidenceWindowSize
                        && countTrue(evidenceWindow) >= requiredEvidence) {
                    flag("down-snap-underfoot-sprint-placements="
                            + countTrue(evidenceWindow) + "/" + evidenceWindowSize
                            + " swing-near-place=false");
                    evidenceWindow.clear();
                }
            }
        }

        @Override
        public void onReload(@NotNull ConfigManager config) {
            minimumDownSnap = Math.max(18.0,
                    config.getDoubleElse(getConfigName() + ".minimum-down-snap", 24.0));
            minimumReturnSnap = Math.max(12.0,
                    config.getDoubleElse(getConfigName() + ".minimum-return-snap", 18.0));
            evidenceWindowSize = Math.max(4,
                    config.getIntElse(getConfigName() + ".window", 6));
            requiredEvidence = Math.max(2,
                    config.getIntElse(getConfigName() + ".required-evidence", 3));
            pendingPlaces.clear();
            evidenceWindow.clear();
            downSnapAt = lastReturnedSnapAt = lastSwingAt = lastSwingConsumedAt = 0L;
        }

        private static final class PendingPlace {
            private final long placedAt;
            private final boolean hasSnap;
            private boolean returned;
            private boolean hasSwing;
            private final boolean sprinting = true;

            private PendingPlace(long placedAt, boolean hasSnap, boolean returned, boolean hasSwing) {
                this.placedAt = placedAt;
                this.hasSnap = hasSnap;
                this.returned = returned;
                this.hasSwing = hasSwing;
            }
        }
    }

    @CheckData(name = "AimC", stableKey = "cwanticheat.aim.attack_snap",
            description = "Repeated attack-timed snap turns that settle directly onto a target", experimental = true)
    public static class AimC extends AimHitCheck implements PacketReceiveListener {
        private final Deque<Boolean> attackWindow = new ArrayDeque<>();
        private double minimumSnapDegrees, maximumAimError;
        private int requiredHits, window;
        private long lastRotationAt;
        private double lastRotationDelta;

        public AimC(GrimPlayer player) {
            super(player);
        }

        @Override
        public void process(RotationUpdate update) {
            super.process(update);
            lastRotationDelta = Math.hypot(update.deltaYaw(), update.deltaPitch());
            lastRotationAt = System.nanoTime();
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.isCancelled() || attackedEntityId(event) < 0) return;
            double error = getAimError(event);
            if (!Double.isFinite(error)) return;
            long now = System.nanoTime();
            boolean suspicious = lastRotationAt != 0L && now - lastRotationAt <= 180_000_000L
                    && lastRotationDelta >= minimumSnapDegrees && error <= maximumAimError;
            addWindowSample(attackWindow, suspicious, window);
            if (attackWindow.size() == window && countTrue(attackWindow) >= requiredHits) {
                flag("attack-snap-hits=" + countTrue(attackWindow) + "/" + window
                        + " rotation-delta=" + formatOffset(lastRotationDelta)
                        + " aim-error=" + formatOffset(error));
                attackWindow.clear();
            } else if (!suspicious) {
                reward();
            }
        }

        @Override
        public void onReload(@NotNull ConfigManager config) {
            minimumSnapDegrees = Math.max(8.0,
                    config.getDoubleElse(getConfigName() + ".minimum-snap-degrees", 13.0));
            maximumAimError = Math.max(0.25,
                    config.getDoubleElse(getConfigName() + ".maximum-aim-error", 1.3));
            requiredHits = Math.max(2,
                    config.getIntElse(getConfigName() + ".required-hits", 3));
            window = Math.max(requiredHits,
                    config.getIntElse(getConfigName() + ".window", 6));
            attackWindow.clear();
            lastRotationAt = 0L;
            lastRotationDelta = 0.0;
        }
    }

    @CheckData(name = "AimD", stableKey = "cwanticheat.aim.rotation_gcd",
            description = "Repeated accurate attacks following rotation deltas outside the inferred mouse GCD grid",
            experimental = true)
    public static class AimD extends AimHitCheck implements PacketReceiveListener {
        private final Deque<Boolean> gridSamples = new ArrayDeque<>();
        private final Deque<Boolean> attackWindow = new ArrayDeque<>();
        private double gridTolerance, maximumAimError;
        private int gridWindow, minimumOffGridSamples, requiredAttacks, attackWindowSize;

        public AimD(GrimPlayer player) {
            super(player);
        }

        @Override
        public void process(RotationUpdate update) {
            super.process(update);
            AimProcessor aim = player.checkManager.get(AimProcessor.class);
            if (aim == null || !Double.isFinite(aim.modeYaw) || !Double.isFinite(aim.modePitch)
                    || aim.modeYaw <= 0.0001 || aim.modePitch <= 0.0001) {
                return;
            }

            boolean hasYaw = update.deltaYawABS() >= 0.05f && Double.isFinite(aim.deltaDotsYaw);
            boolean hasPitch = update.deltaPitchABS() >= 0.05f && Double.isFinite(aim.deltaDotsPitch);
            if (!hasYaw && !hasPitch) return;
            boolean offGrid = hasYaw && distanceToInteger(aim.deltaDotsYaw) > gridTolerance
                    || hasPitch && distanceToInteger(aim.deltaDotsPitch) > gridTolerance;
            addWindowSample(gridSamples, offGrid, gridWindow);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.isCancelled() || attackedEntityId(event) < 0) return;
            double error = getAimError(event);
            if (!Double.isFinite(error)) return;
            int offGridCount = countTrue(gridSamples);
            boolean suspicious = gridSamples.size() == gridWindow
                    && offGridCount >= minimumOffGridSamples && error <= maximumAimError;
            addWindowSample(attackWindow, suspicious, attackWindowSize);
            if (attackWindow.size() == attackWindowSize
                    && countTrue(attackWindow) >= requiredAttacks) {
                flag("accurate-off-grid-attacks=" + countTrue(attackWindow) + "/" + attackWindowSize
                        + " off-grid-samples=" + offGridCount + "/" + gridSamples.size()
                        + " aim-error=" + formatOffset(error));
                attackWindow.clear();
            } else if (!suspicious) {
                reward();
            }
        }

        @Override
        public void onReload(@NotNull ConfigManager config) {
            gridTolerance = Math.max(0.05,
                    Math.min(0.25, config.getDoubleElse(getConfigName() + ".grid-tolerance", 0.14)));
            maximumAimError = Math.max(0.5,
                    config.getDoubleElse(getConfigName() + ".maximum-aim-error", 2.0));
            gridWindow = Math.max(8, config.getIntElse(getConfigName() + ".grid-window", 12));
            minimumOffGridSamples = Math.max(5,
                    config.getIntElse(getConfigName() + ".minimum-off-grid-samples", 9));
            attackWindowSize = Math.max(4,
                    config.getIntElse(getConfigName() + ".attack-window", 6));
            requiredAttacks = Math.max(2,
                    config.getIntElse(getConfigName() + ".required-attacks", 3));
            gridSamples.clear();
            attackWindow.clear();
        }
    }

    @CheckData(name = "AimE", stableKey = "cwanticheat.aim.target_lock_inventory",
            description = "Tracks unusually precise aim on moving targets and rotations sent with an inventory open",
            experimental = true)
    public static class AimE extends AimHitCheck implements PacketReceiveListener {
        private final Deque<Boolean> targetWindow = new ArrayDeque<>();
        private int targetId = -1;
        private double lastX, lastY, lastZ;
        private double minimumTargetMovement, maximumSpatialError;
        private int requiredHits, window;
        private long inventoryWindowStart;
        private int inventoryRotationSamples;
        private boolean inventoryRotationFlagged;

        public AimE(GrimPlayer player) {
            super(player);
        }

        @Override
        public void process(RotationUpdate update) {
            super.process(update);
            double delta = Math.hypot(update.deltaYaw(), update.deltaPitch());
            if (player.hasInventoryOpen) {
                long now = System.nanoTime();
                if (inventoryWindowStart == 0L || now - inventoryWindowStart > 600_000_000L) {
                    inventoryWindowStart = now;
                    inventoryRotationSamples = 0;
                    inventoryRotationFlagged = false;
                }
                if (delta >= 0.75) inventoryRotationSamples++;
                if (!inventoryRotationFlagged && inventoryRotationSamples >= 3) {
                    flag("rotation-while-inventory-open samples=" + inventoryRotationSamples
                            + " total-delta=" + formatOffset(delta));
                    inventoryRotationFlagged = true;
                }
            } else {
                inventoryWindowStart = 0L;
                inventoryRotationSamples = 0;
                inventoryRotationFlagged = false;
            }
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.isCancelled()) return;
            int id = attackedEntityId(event);
            if (id < 0) return;
            PacketEntity target = player.compensatedEntities.entityMap.get(id);
            if (target == null || !target.isLivingEntity || target.isDead || player.inVehicle()) {
                resetTarget();
                return;
            }
            SimpleCollisionBox box = target.getPossibleCollisionBoxes();
            if (box == null) return;
            double x = (box.minX + box.maxX) * 0.5;
            double y = (box.minY + box.maxY) * 0.5;
            double z = (box.minZ + box.maxZ) * 0.5;
            if (targetId != id) {
                targetId = id;
                lastX = x;
                lastY = y;
                lastZ = z;
                targetWindow.clear();
                return;
            }

            double movement = Math.sqrt((x - lastX) * (x - lastX)
                    + (y - lastY) * (y - lastY) + (z - lastZ) * (z - lastZ));
            double error = getAimError(event);
            double dx = x - player.x;
            double dy = y - (player.y + player.getEyeHeight());
            double dz = z - player.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double spatialError = Double.isFinite(error) && distance > 0.1
                    ? Math.tan(Math.toRadians(error)) * distance : Double.POSITIVE_INFINITY;
            boolean maceInHand = isItem(player.inventory.getHeldItem(), "mace");
            boolean suspicious = movement >= minimumTargetMovement
                    && spatialError <= maximumSpatialError
                    && Double.isFinite(spatialError)
                    && (player.isGliding || maceInHand || movement >= minimumTargetMovement * 1.75);
            addWindowSample(targetWindow, suspicious, window);
            if (targetWindow.size() == window && countTrue(targetWindow) >= requiredHits) {
                flag("moving-target-lock=" + countTrue(targetWindow) + "/" + window
                        + " movement=" + formatOffset(movement)
                        + " spatial-error=" + formatOffset(spatialError));
                targetWindow.clear();
            } else if (!suspicious) {
                reward();
            }
            lastX = x;
            lastY = y;
            lastZ = z;
        }

        private void resetTarget() {
            targetId = -1;
            targetWindow.clear();
        }

        @Override
        public void onReload(@NotNull ConfigManager config) {
            minimumTargetMovement = Math.max(0.03,
                    config.getDoubleElse(getConfigName() + ".minimum-target-movement", 0.12));
            maximumSpatialError = Math.max(0.025,
                    config.getDoubleElse(getConfigName() + ".maximum-spatial-error", 0.08));
            requiredHits = Math.max(3, config.getIntElse(getConfigName() + ".required-hits", 5));
            window = Math.max(requiredHits, config.getIntElse(getConfigName() + ".window", 7));
            resetTarget();
            inventoryWindowStart = 0L;
            inventoryRotationSamples = 0;
            inventoryRotationFlagged = false;
        }
    }

    @CheckData(name = "AuraA", stableKey = "cwanticheat.combat.aura_a",
            description = "Correlates attack packet order, target ray misses, occlusion, shield use, and sprint reset state",
            experimental = true)
    public static class AuraA extends AimHitCheck implements PacketReceiveListener {
        private final Deque<Boolean> attackWindow = new ArrayDeque<>();
        private boolean swingReady;
        private long lastSprintStopAt;
        private int window, requiredEvidence;
        private double maximumDistance;

        public AuraA(GrimPlayer player) {
            super(player);
            swingReady = player.getClientVersion().isNewerThan(ClientVersion.V_1_8);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            long now = System.nanoTime();
            if (isSwing(event)) {
                swingReady = true;
                return;
            }
            if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
                if (new WrapperPlayClientEntityAction(event).getAction()
                        == WrapperPlayClientEntityAction.Action.STOP_SPRINTING) {
                    lastSprintStopAt = now;
                }
                return;
            }
            if (event.isCancelled()) return;
            int targetId = attackedEntityId(event);
            if (targetId < 0 || player.inVehicle() || player.getSetbackTeleportUtil().insideUnloadedChunk()) return;
            PacketEntity target = player.compensatedEntities.entityMap.get(targetId);
            if (target == null || !target.isLivingEntity || target.isDead) return;
            SimpleCollisionBox box = target.getPossibleCollisionBoxes();
            if (box == null) return;

            Ray ray = Ray.from(player, player.x, player.y + player.getEyeHeight(),
                    player.z, player.yaw, player.pitch);
            boolean rayHit = box.intersectsRay(ray, 0.0F, (float) maximumDistance) != null;
            double centerX = (box.minX + box.maxX) * 0.5;
            double centerY = box.minY + (box.maxY - box.minY) * 0.5;
            double centerZ = (box.minZ + box.maxZ) * 0.5;
            double upperY = box.minY + (box.maxY - box.minY) * 0.72;
            boolean blocked = occluded(centerX, centerY, centerZ)
                    && occluded(centerX, upperY, centerZ);
            boolean shieldUse = player.packetStateData.isSlowedByUsingItem()
                    && isItem(player.inventory.getItemInHand(player.packetStateData.itemInUseHand), "shield");
            boolean invalidSwingOrder = !swingReady;
            boolean sprintResetMissing = player.isSprinting
                    && (lastSprintStopAt == 0L || now - lastSprintStopAt > 250_000_000L);
            boolean suspicious = shieldUse || blocked
                    || (invalidSwingOrder && !rayHit)
                    || (sprintResetMissing && !rayHit);
            addWindowSample(attackWindow, suspicious, window);
            swingReady = false;

            if (attackWindow.size() == window && countTrue(attackWindow) >= requiredEvidence) {
                flag("aura-inconsistent-attacks=" + countTrue(attackWindow) + "/" + window
                        + " ray-hit=" + rayHit + " blocked=" + blocked
                        + " shield-use=" + shieldUse + " swing-order=" + !invalidSwingOrder
                        + " sprint-reset-missing=" + sprintResetMissing);
                attackWindow.clear();
            } else if (!suspicious) {
                reward();
            }
        }

        private boolean occluded(double targetX, double targetY, double targetZ) {
            double eyeX = player.x;
            double eyeY = player.y + player.getEyeHeight();
            double eyeZ = player.z;
            double dx = targetX - eyeX;
            double dy = targetY - eyeY;
            double dz = targetZ - eyeZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 0.1 || distance > maximumDistance) return false;
            int steps = Math.max(2, (int) Math.ceil(distance * 12.0));
            for (int i = 1; i < steps - 2; i++) {
                double t = (double) i / steps;
                int x = (int) Math.floor(eyeX + dx * t);
                int y = (int) Math.floor(eyeY + dy * t);
                int z = (int) Math.floor(eyeZ + dz * t);
                if (!player.compensatedWorld.isChunkLoaded(x >> 4, z >> 4)) return false;
                if (player.compensatedWorld.getBlock(new Vector3i(x, y, z)).getType().isBlocking()) return true;
            }
            return false;
        }

        @Override
        public void onReload(@NotNull ConfigManager config) {
            window = Math.max(4, config.getIntElse(getConfigName() + ".window", 7));
            requiredEvidence = Math.max(2,
                    config.getIntElse(getConfigName() + ".required-evidence", 3));
            maximumDistance = Math.max(3.0,
                    config.getDoubleElse(getConfigName() + ".maximum-distance", 5.5));
            attackWindow.clear();
            lastSprintStopAt = 0L;
            swingReady = player.getClientVersion().isNewerThan(ClientVersion.V_1_8);
        }
    }
}
