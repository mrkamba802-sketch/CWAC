package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import ac.grim.grimac.utils.nmsutil.StuckSpeed;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "NoWebA", stableKey = "grim.movement.no_web_a", description = "Moved faster than compensated cobweb prediction", setback = 6, decay = 0.02)
public class NoWebA extends Check implements PostPredictionListener {
    private static final Verbose V = Verbose.of("bestOffset={f64}, threshold={f64}, streak={uint}");
    private double bestOffset = Double.POSITIVE_INFINITY;
    private double threshold = 0.0005;
    private int suspiciousTicks;
    private boolean sawWebCandidate;

    public NoWebA(GrimPlayer player) {
        super(player);
    }

    public void handlePredictionAnalysis(double offset) {
        sawWebCandidate = true;
        bestOffset = Math.min(bestOffset, offset);
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        int mask = player.uncertaintyHandler.stuckSpeedMultiplierMask;
        boolean definiteWeb = mask == StuckSpeed.COBWEB.getIndex()
                || mask == StuckSpeed.COBWEB_WEAVING.getIndex();
        if (!predictionComplete.isChecked() || !definiteWeb || !sawWebCandidate
                || predictionComplete.getData() == null || predictionComplete.getData().isTeleport()
                || player.inVehicle() || player.isFlying || player.isGliding
                || player.packetStateData.lastPacketWasTeleport || player.getSetbackTeleportUtil().blockOffsets
                || player.skippedTickInActualMovement) {
            suspiciousTicks = 0;
            resetSample();
            return;
        }

        if (bestOffset > threshold) {
            suspiciousTicks++;
            if (suspiciousTicks >= 2) {
                flagWithSetback(V.write(verbose()).f64(bestOffset).f64(threshold).uint(suspiciousTicks));
                suspiciousTicks = 0;
            }
        } else {
            suspiciousTicks = Math.max(0, suspiciousTicks - 1);
            reward();
        }
        resetSample();
    }

    private void resetSample() {
        bestOffset = Double.POSITIVE_INFINITY;
        sawWebCandidate = false;
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        threshold = config.getDoubleElse(getConfigName() + ".threshold", 0.0005);
    }
}
