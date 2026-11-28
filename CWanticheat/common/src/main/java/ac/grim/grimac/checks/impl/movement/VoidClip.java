package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "VoidClip", stableKey = "grim.movement.void_clip", experimental = false description = "Detects unexplained downward movement through predicted solid collisions", setback = 1, decay = 0.01)
public class VoidClip extends Check implements PostPredictionListener {
    private static final Verbose V = Verbose.of("expectedY={f64}, actualY={f64}, verticalError={f64}, offset={f64}, streak={uint}");
    private double verticalErrorThreshold = 0.35;
    private double predictionOffsetThreshold = 0.15;
    private int requiredStreak = 2;
    private int evidence;

    public VoidClip(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(PredictionComplete complete) {
        if (!complete.isChecked() || complete.getData() == null || complete.getData().isTeleport()
                || player.getSetbackTeleportUtil().blockOffsets || player.packetStateData.lastPacketWasTeleport
                || player.inVehicle() || player.isFlying || player.isGliding || player.isSwimming
                || player.wasTouchingWater || player.wasTouchingLava || player.isClimbing
                || player.isSlowMovement || player.uncertaintyHandler.wasAffectedByStuckSpeed()
                || player.predictedVelocity.isKnockback() || player.predictedVelocity.isExplosion()
                || player.predictedVelocity.isTrident()) {
            evidence = 0;
            reward();
            return;
        }

        double expectedY = player.predictedVelocity.vector.getY();
        double actualY = player.actualMovement.getY();
        double verticalError = expectedY - actualY;
        boolean unexplainedDrop = actualY < -0.75
                && verticalError > verticalErrorThreshold
                && complete.getOffset() > predictionOffsetThreshold;

        if (unexplainedDrop) {
            evidence++;
            if (evidence >= requiredStreak) {
                flagWithSetback(V.write(verbose()).f64(expectedY).f64(actualY)
                        .f64(verticalError).f64(complete.getOffset()).uint(evidence));
                evidence = 0;
            }
        } else {
            evidence = Math.max(0, evidence - 1);
            reward();
        }
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        verticalErrorThreshold = Math.max(0.1,
                config.getDoubleElse(getConfigName() + ".vertical-error-threshold", 0.35));
        predictionOffsetThreshold = Math.max(0.02,
                config.getDoubleElse(getConfigName() + ".prediction-offset-threshold", 0.15));
        requiredStreak = Math.max(1, config.getIntElse(getConfigName() + ".required-streak", 2));
    }
}
