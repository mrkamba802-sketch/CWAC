package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

@CheckData(name = "AirStuckB", stableKey = "cwanticheat.movement.air_stuck_b", experimental = false
        description = "Repeated near-zero vertical movement across a window while gravity predicts falling",
        setback = 8, decay = 0.02)
public class AirStuckB extends Check implements PostPredictionListener {
    private static final Verbose V = Verbose.of(
            "predictedY={f64}, actualY={f64}, offset={f64}, evidence={uint}");

    private final Deque<Boolean> samples = new ArrayDeque<>();
    private double predictedYThreshold;
    private double maximumActualY;
    private double minimumOffset;
    private int window;
    private int requiredSamples;

    public AirStuckB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(PredictionComplete complete) {
        if (!complete.isChecked() || complete.getData() == null || complete.getData().isTeleport()
                || player.getSetbackTeleportUtil().blockOffsets || player.packetStateData.lastPacketWasTeleport
                || !player.cameraEntity.isSelf() || player.onGround || player.inVehicle()
                || player.isFlying || player.isGliding || player.isSwimming
                || player.wasTouchingWater || player.wasTouchingLava || player.isClimbing
                || player.isSlowMovement || player.isInBed
                || player.uncertaintyHandler.wasAffectedByStuckSpeed()
                || player.predictedVelocity.isKnockback() || player.predictedVelocity.isExplosion()
                || player.predictedVelocity.isTrident() || !player.isTickingReliablyFor(3)) {
            samples.clear();
            reward();
            return;
        }

        double predictedY = player.predictedVelocity.vector.getY();
        double actualY = player.actualMovement.getY();
        boolean ignoredGravity = predictedY <= -predictedYThreshold
                && Math.abs(actualY) <= maximumActualY
                && complete.getOffset() >= minimumOffset;

        samples.addLast(ignoredGravity);
        while (samples.size() > window) samples.removeFirst();

        int evidence = countEvidence();
        if (samples.size() == window && evidence >= requiredSamples) {
            flagWithSetback(V.write(verbose()).f64(predictedY).f64(actualY)
                    .f64(complete.getOffset()).uint(evidence));
            samples.clear();
        } else if (!ignoredGravity) {
            reward();
        }
    }

    private int countEvidence() {
        int count = 0;
        for (boolean sample : samples) {
            if (sample) count++;
        }
        return count;
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        predictedYThreshold = Math.max(0.06,
                config.getDoubleElse(getConfigName() + ".predicted-y-threshold", 0.10));
        maximumActualY = Math.max(0.005,
                config.getDoubleElse(getConfigName() + ".maximum-actual-y", 0.035));
        minimumOffset = Math.max(0.0005,
                config.getDoubleElse(getConfigName() + ".minimum-offset", 0.0025));
        window = Math.max(5, config.getIntElse(getConfigName() + ".window", 8));
        requiredSamples = Math.max(3,
                Math.min(window, config.getIntElse(getConfigName() + ".required-samples", 6)));
        if (samples != null) samples.clear();
    }
}
