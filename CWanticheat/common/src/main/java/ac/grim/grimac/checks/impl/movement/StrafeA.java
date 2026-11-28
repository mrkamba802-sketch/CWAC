package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "StrafeA", stableKey = "grim.movement.strafe_a", experimental = false description = "Detects repeated horizontal speed excess against vanilla movement prediction", setback = 2, decay = 0.02)
public class StrafeA extends Check implements PostPredictionListener {
    private static final Verbose V = Verbose.of("actualSpeed={f64}, predictedSpeed={f64}, excess={f64}, offset={f64}, evidence={uint}");
    private double horizontalExcessThreshold = 0.025;
    private double predictionOffsetThreshold = 0.012;
    private int requiredStreak = 4;
    private int evidence;

    public StrafeA(GrimPlayer player) {
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

        double actualSpeed = Math.hypot(player.actualMovement.getX(), player.actualMovement.getZ());
        double predictedSpeed = Math.hypot(player.predictedVelocity.vector.getX(), player.predictedVelocity.vector.getZ());
        double excess = actualSpeed - predictedSpeed;
        boolean abnormalAcceleration = actualSpeed > 0.10
                && excess > horizontalExcessThreshold
                && complete.getOffset() > predictionOffsetThreshold;

        if (abnormalAcceleration) {
            evidence++;
            if (evidence >= requiredStreak) {
                flagWithSetback(V.write(verbose()).f64(actualSpeed).f64(predictedSpeed)
                        .f64(excess).f64(complete.getOffset()).uint(evidence));
                evidence = 0;
            }
        } else {
            evidence = Math.max(0, evidence - 1);
            reward();
        }
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        horizontalExcessThreshold = Math.max(0.005,
                config.getDoubleElse(getConfigName() + ".horizontal-excess-threshold", 0.025));
        predictionOffsetThreshold = Math.max(0.002,
                config.getDoubleElse(getConfigName() + ".prediction-offset-threshold", 0.012));
        requiredStreak = Math.max(2, config.getIntElse(getConfigName() + ".required-streak", 4));
    }
}
