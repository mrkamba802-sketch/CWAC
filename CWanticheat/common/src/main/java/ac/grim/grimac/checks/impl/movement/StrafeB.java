package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "StrafeB", stableKey = "grim.movement.strafe_b", experimental = false description = "Detects repeated sideways acceleration outside the vanilla prediction envelope", setback = 2, decay = 0.02)
public class StrafeB extends Check implements PostPredictionListener {
    private static final Verbose V = Verbose.of("lateralError={f64}, horizontalError={f64}, offset={f64}, evidence={uint}");
    private double lateralErrorThreshold = 0.02;
    private double predictionOffsetThreshold = 0.015;
    private int requiredStreak = 3;
    private int evidence;

    public StrafeB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(PredictionComplete complete) {
        if (!complete.isChecked() || complete.getData() == null || complete.getData().isTeleport()
                || player.getSetbackTeleportUtil().blockOffsets || player.packetStateData.lastPacketWasTeleport
                || player.inVehicle() || player.isFlying || player.isGliding || player.isSwimming
                || player.wasTouchingWater || player.wasTouchingLava || player.isClimbing
                || player.isSlowMovement || player.uncertaintyHandler.wasAffectedByStuckSpeed()
                || player.predictedVelocity.isKnockback() || player.predictedVelocity.isExplosion()) {
            evidence = 0;
            reward();
            return;
        }

        double residualX = player.actualMovement.getX() - player.predictedVelocity.vector.getX();
        double residualZ = player.actualMovement.getZ() - player.predictedVelocity.vector.getZ();
        double yaw = Math.toRadians(player.yaw);
        double lateralError = residualX * Math.cos(yaw) + residualZ * Math.sin(yaw);
        double horizontalError = Math.hypot(residualX, residualZ);
        boolean abnormalLateralAcceleration = Math.hypot(player.actualMovement.getX(), player.actualMovement.getZ()) > 0.08
                && Math.abs(lateralError) > lateralErrorThreshold
                && horizontalError > predictionOffsetThreshold
                && complete.getOffset() > predictionOffsetThreshold;

        if (abnormalLateralAcceleration) {
            evidence++;
            if (evidence >= requiredStreak) {
                flagWithSetback(V.write(verbose()).f64(lateralError).f64(horizontalError)
                        .f64(complete.getOffset()).uint(evidence));
                evidence = 0;
            }
        } else {
            evidence = Math.max(0, evidence - 1);
            reward();
        }
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        lateralErrorThreshold = Math.max(0.005,
                config.getDoubleElse(getConfigName() + ".lateral-error-threshold", 0.02));
        predictionOffsetThreshold = Math.max(0.002,
                config.getDoubleElse(getConfigName() + ".prediction-offset-threshold", 0.015));
        requiredStreak = Math.max(2, config.getIntElse(getConfigName() + ".required-streak", 3));
    }
}
