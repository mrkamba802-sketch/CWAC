package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.impl.aim.AimHitCheck;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

@CheckData(name = "AimF", stableKey = "cwanticheat.aim.fast_target_reacquisition", experimental = false
        description = "Repeated highly accurate target switches shortly after significant rotations")
public class AimF extends AimHitCheck implements PacketReceiveListener {
    private final Deque<Boolean> switchSamples = new ArrayDeque<>();
    private final Deque<Long> reactionSamples = new ArrayDeque<>();

    private int lastTargetId = -1;
    private long lastSignificantRotationAt;
    private double lastSignificantRotation;
    private double minimumRotationDegrees;
    private double maximumAimError;
    private double maximumReactionCv;
    private int minimumReactionMs;
    private int maximumReactionMs;
    private int window;
    private int requiredSwitches;

    public AimF(GrimPlayer player) {
        super(player);
    }

    @Override
    public void process(RotationUpdate update) {
        super.process(update);
        double yaw = wrapDegrees(update.deltaYaw());
        double magnitude = Math.hypot(yaw, update.deltaPitch());
        if (magnitude >= minimumRotationDegrees) {
            lastSignificantRotationAt = System.nanoTime();
            lastSignificantRotation = magnitude;
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled() || player.inVehicle() || !player.cameraEntity.isSelf()) return;
        int targetId = attackedEntityId(event);
        if (targetId < 0) return;

        PacketEntity target = player.compensatedEntities.entityMap.get(targetId);
        if (target == null || !target.isLivingEntity || target.isDead) {
            resetTarget();
            return;
        }

        if (lastTargetId >= 0 && targetId != lastTargetId) {
            long now = System.nanoTime();
            long reactionMs = lastSignificantRotationAt == 0L
                    ? Long.MAX_VALUE : (now - lastSignificantRotationAt) / 1_000_000L;
            double aimError = getAimError(event);
            boolean suspicious = reactionMs >= minimumReactionMs && reactionMs <= maximumReactionMs
                    && lastSignificantRotation >= minimumRotationDegrees
                    && Double.isFinite(aimError) && aimError <= maximumAimError;

            switchSamples.addLast(suspicious);
            reactionSamples.addLast(suspicious ? reactionMs : 0L);
            while (switchSamples.size() > window) switchSamples.removeFirst();
            while (reactionSamples.size() > window) reactionSamples.removeFirst();

            int evidence = countEvidence();
            double reactionCv = reactionCoefficientOfVariation();
            if (switchSamples.size() == window && evidence >= requiredSwitches
                    && reactionCv <= maximumReactionCv) {
                flag("fast-accurate-target-switches=" + evidence + "/" + window
                        + " reaction-cv=" + formatOffset(reactionCv)
                        + " aim-error=" + formatOffset(aimError)
                        + " turn=" + formatOffset(lastSignificantRotation));
                switchSamples.clear();
                reactionSamples.clear();
            } else if (!suspicious) {
                reward();
            }
            lastSignificantRotationAt = 0L;
            lastSignificantRotation = 0.0;
        }

        lastTargetId = targetId;
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

    private int countEvidence() {
        int count = 0;
        for (boolean sample : switchSamples) {
            if (sample) count++;
        }
        return count;
    }

    private double reactionCoefficientOfVariation() {
        int count = 0;
        double mean = 0.0;
        for (long sample : reactionSamples) {
            if (sample > 0L) {
                mean += sample;
                count++;
            }
        }
        if (count < requiredSwitches || mean <= 0.0) return Double.POSITIVE_INFINITY;
        mean /= count;
        double variance = 0.0;
        for (long sample : reactionSamples) {
            if (sample > 0L) {
                double difference = sample - mean;
                variance += difference * difference;
            }
        }
        return Math.sqrt(variance / count) / mean;
    }

    private static double wrapDegrees(double value) {
        value %= 360.0;
        if (value >= 180.0) value -= 360.0;
        if (value < -180.0) value += 360.0;
        return value;
    }

    private void resetTarget() {
        lastTargetId = -1;
        lastSignificantRotationAt = 0L;
        lastSignificantRotation = 0.0;
        if (switchSamples != null) switchSamples.clear();
        if (reactionSamples != null) reactionSamples.clear();
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minimumRotationDegrees = Math.max(8.0,
                config.getDoubleElse(getConfigName() + ".minimum-rotation-degrees", 12.0));
        minimumReactionMs = Math.max(5,
                config.getIntElse(getConfigName() + ".minimum-reaction-ms", 20));
        maximumReactionMs = Math.max(minimumReactionMs + 1,
                config.getIntElse(getConfigName() + ".maximum-reaction-ms", 140));
        maximumAimError = Math.max(0.2,
                config.getDoubleElse(getConfigName() + ".maximum-aim-error", 0.9));
        maximumReactionCv = Math.max(0.03,
                Math.min(0.8, config.getDoubleElse(getConfigName() + ".maximum-reaction-cv", 0.20)));
        window = Math.max(5, config.getIntElse(getConfigName() + ".window", 8));
        requiredSwitches = Math.max(3,
                Math.min(window, config.getIntElse(getConfigName() + ".required-switches", 5)));
        resetTarget();
    }
}
