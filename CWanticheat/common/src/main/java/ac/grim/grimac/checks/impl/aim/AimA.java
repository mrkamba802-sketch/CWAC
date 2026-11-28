package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AimA", stableKey = "grim.aim.yaw_pitch_delta", experimental = false
        description = "Repeated accurate attacks following unusually uniform, low-jerk rotation paths", decay = 0.02)
public class AimA extends AimHitCheck implements PacketReceiveListener {
    private final Deque<RotationSample> rotations = new ArrayDeque<>();
    private final Deque<Boolean> attackWindow = new ArrayDeque<>();
    private int minimumSamples, requiredAttacks, attackWindowSize;
    private double maximumCoefficientOfVariation, minimumDirectionConsistency, maximumAimError;

    public AimA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void process(RotationUpdate update) {
        super.process(update);
        if (player.packetStateData.lastPacketWasTeleport
                || player.packetStateData.horseInteractCausedForcedRotation
                || player.vehicleData.wasVehicleSwitch || player.inVehicle()
                || !player.cameraEntity.isSelf()) {
            rotations.clear();
            return;
        }

        double yaw = wrapDegrees(update.deltaYaw());
        double pitch = update.deltaPitch();
        double magnitude = Math.hypot(yaw, pitch);
        if (magnitude < 0.18) return;
        if (magnitude > 16.0) {
            rotations.clear();
            return;
        }
        rotations.addLast(new RotationSample(yaw, pitch, magnitude));
        while (rotations.size() > minimumSamples) rotations.removeFirst();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled() || !isAttack(event)) return;
        double aimError = getAimError(event);
        boolean suspicious = Double.isFinite(aimError) && aimError <= maximumAimError
                && isUniformSmoothPath();
        attackWindow.addLast(suspicious);
        while (attackWindow.size() > attackWindowSize) attackWindow.removeFirst();

        int count = 0;
        for (boolean sample : attackWindow) if (sample) count++;
        if (attackWindow.size() == attackWindowSize && count >= requiredAttacks) {
            flag("smooth-aim-attacks=" + count + "/" + attackWindowSize
                    + " aim-error=" + formatOffset(aimError)
                    + " rotation-samples=" + rotations.size());
            attackWindow.clear();
        } else if (!suspicious) {
            reward();
        }
    }

    private boolean isUniformSmoothPath() {
        if (rotations.size() < minimumSamples) return false;
        double mean = 0.0;
        for (RotationSample sample : rotations) mean += sample.magnitude;
        mean /= rotations.size();
        if (mean < 0.25 || mean > 8.0) return false;

        double variance = 0.0;
        double directionSum = 0.0;
        RotationSample previous = null;
        for (RotationSample sample : rotations) {
            double difference = sample.magnitude - mean;
            variance += difference * difference;
            if (previous != null) {
                double denominator = previous.magnitude * sample.magnitude;
                if (denominator > 1.0E-6) {
                    directionSum += (previous.yaw * sample.yaw + previous.pitch * sample.pitch)
                            / denominator;
                }
            }
            previous = sample;
        }
        double coefficient = Math.sqrt(variance / rotations.size()) / mean;
        double directionConsistency = directionSum / Math.max(1, rotations.size() - 1);
        return coefficient <= maximumCoefficientOfVariation
                && directionConsistency >= minimumDirectionConsistency;
    }

    private boolean isAttack(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) return true;
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return false;
        return new WrapperPlayClientInteractEntity(event).getAction()
                == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
    }

    private static double wrapDegrees(double value) {
        value %= 360.0;
        if (value >= 180.0) value -= 360.0;
        if (value < -180.0) value += 360.0;
        return value;
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minimumSamples = Math.max(8, config.getIntElse(getConfigName() + ".minimum-samples", 10));
        maximumCoefficientOfVariation = Math.max(0.03,
                Math.min(0.30, config.getDoubleElse(getConfigName() + ".maximum-cv", 0.16)));
        minimumDirectionConsistency = Math.max(0.75,
                Math.min(0.999, config.getDoubleElse(getConfigName() + ".minimum-direction-consistency", 0.95)));
        maximumAimError = Math.max(0.25,
                config.getDoubleElse(getConfigName() + ".maximum-aim-error", 1.4));
        attackWindowSize = Math.max(4, config.getIntElse(getConfigName() + ".attack-window", 6));
        requiredAttacks = Math.max(2,
                config.getIntElse(getConfigName() + ".required-attacks", 3));
        if (rotations != null) rotations.clear();
        if (attackWindow != null) attackWindow.clear();
    }

    private record RotationSample(double yaw, double pitch, double magnitude) {}
}
