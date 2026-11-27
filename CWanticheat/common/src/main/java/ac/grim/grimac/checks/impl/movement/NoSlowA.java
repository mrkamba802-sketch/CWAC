package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "NoSlowA", stableKey = "grim.movement.no_slow_a", description = "Detects tiny item-use movement excess across consecutive prediction samples", setback = 2, decay = 0.01)
public class NoSlowA extends Check implements PostPredictionListener {
    private static final Verbose V = Verbose.of("bestOffset={f64}, threshold={f64}, streak={uint}");
    private double bestOffset = Double.POSITIVE_INFINITY;
    private double threshold = 0.00005;
    private int suspiciousTicks;
    private boolean sawCandidate;

    public NoSlowA(GrimPlayer player) {
        super(player);
    }

    public void handlePredictionAnalysis(double offset) {
        if (Double.isFinite(offset) && offset >= 0.0) {
            sawCandidate = true;
            bestOffset = Math.min(bestOffset, offset);
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked() || !player.packetStateData.isSlowedByUsingItem()
                || player.inVehicle() || player.isFlying || player.isGliding || player.isSwimming
                || player.packetStateData.lastPacketWasTeleport
                || player.getSetbackTeleportUtil().blockOffsets
                || player.uncertaintyHandler.wasAffectedByStuckSpeed()
                || (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8)
                    && player.checkManager.getNoSlow().didSlotChangeLastTick)) {
            suspiciousTicks = 0;
            resetSample();
            return;
        }

        if (sawCandidate && bestOffset > threshold) {
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
        sawCandidate = false;
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        // A config may make this check stricter, but must not silently loosen the built-in threshold.
        threshold = Math.max(0.0000001, Math.min(0.00005,
                config.getDoubleElse(getConfigName() + ".threshold", 0.00005)));
    }
}
