package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

@CheckData(name = "AuraB", stableKey = "cwanticheat.combat.aura_b", experimental = false
        description = "Unusually uniform entity-attack timing repeated while switching living targets")
public class AuraB extends Check implements PacketReceiveListener {
    private final Deque<Long> intervals = new ArrayDeque<>();
    private final Deque<Integer> targets = new ArrayDeque<>();

    private long lastAttackAt;
    private int minimumIntervalMs;
    private int maximumIntervalMs;
    private int sampleCount;
    private int minimumTargetSwitches;
    private int requiredWindows;
    private int regularWindows;
    private double maximumCv;

    public AuraB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled() || player.inVehicle() || !player.cameraEntity.isSelf()) return;
        int targetId = attackedEntityId(event);
        if (targetId < 0) return;

        PacketEntity target = player.compensatedEntities.entityMap.get(targetId);
        if (target == null || !target.isLivingEntity || target.isDead) return;

        long now = System.nanoTime();
        if (lastAttackAt != 0L) {
            long intervalMs = (now - lastAttackAt) / 1_000_000L;
            if (intervalMs < minimumIntervalMs || intervalMs > maximumIntervalMs) {
                intervals.clear();
                targets.clear();
                regularWindows = 0;
            } else {
                intervals.addLast(intervalMs);
                targets.addLast(targetId);
                while (intervals.size() > sampleCount) intervals.removeFirst();
                while (targets.size() > sampleCount) targets.removeFirst();

                if (intervals.size() == sampleCount) {
                    double cv = coefficientOfVariation();
                    int switches = countTargetSwitches();
                    if (cv <= maximumCv && switches >= minimumTargetSwitches) {
                        regularWindows++;
                        if (regularWindows >= requiredWindows) {
                            flag("uniform-attack-intervals=" + intervals.size()
                                    + " cv=" + formatOffset(cv)
                                    + " target-switches=" + switches
                                    + " windows=" + regularWindows);
                            intervals.clear();
                            targets.clear();
                            regularWindows = 0;
                        }
                    } else {
                        regularWindows = 0;
                    }
                }
            }
        }
        lastAttackAt = now;
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

    private double coefficientOfVariation() {
        double mean = 0.0;
        for (long interval : intervals) mean += interval;
        mean /= intervals.size();
        if (mean <= 0.0) return Double.POSITIVE_INFINITY;
        double variance = 0.0;
        for (long interval : intervals) {
            double difference = interval - mean;
            variance += difference * difference;
        }
        return Math.sqrt(variance / intervals.size()) / mean;
    }

    private int countTargetSwitches() {
        int switches = 0;
        int previous = Integer.MIN_VALUE;
        for (int target : targets) {
            if (previous != Integer.MIN_VALUE && target != previous) switches++;
            previous = target;
        }
        return switches;
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minimumIntervalMs = Math.max(40,
                config.getIntElse(getConfigName() + ".minimum-interval-ms", 75));
        maximumIntervalMs = Math.max(minimumIntervalMs + 1,
                config.getIntElse(getConfigName() + ".maximum-interval-ms", 500));
        sampleCount = Math.max(10, config.getIntElse(getConfigName() + ".sample-count", 20));
        minimumTargetSwitches = Math.max(2,
                Math.min(sampleCount - 1, config.getIntElse(getConfigName() + ".minimum-target-switches", 5)));
        requiredWindows = Math.max(2,
                config.getIntElse(getConfigName() + ".required-windows", 3));
        maximumCv = Math.max(0.002,
                Math.min(0.10, config.getDoubleElse(getConfigName() + ".maximum-cv", 0.012)));
        if (intervals != null) intervals.clear();
        if (targets != null) targets.clear();
        lastAttackAt = 0L;
        regularWindows = 0;
    }
}
