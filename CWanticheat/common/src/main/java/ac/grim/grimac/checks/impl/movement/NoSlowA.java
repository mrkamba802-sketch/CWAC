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

@CheckData(name = "NoSlowA", stableKey = "grim.movement.no_slow_a", description = "Exceeded item-use movement limits across consecutive predictions", setback = 5, decay = 0.02)
public class NoSlowA extends Check implements PostPredictionListener {
    private static final Verbose V = Verbose.of("bestOffset={f64}, threshold={f64}, streak={uint}");
    private double bestOffset = Double.POSITIVE_INFINITY;
    private double threshold = 0.0005;
    private int suspiciousTicks;
    private boolean sawCandidate;

    public NoSlowA(GrimPlayer player) {
        super(player);
    }

    public void handlePredictionAnalysis(double offset) {
        sawCandidate = true;
        bestOffset = Math.min(bestOffset, offset);
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked() || !player.packetStateData.isSlowedByUsingItem()
                || player.inVehicle() || player.isFlying || player.isGliding
                || player.packetStateData.lastPacketWasTeleport
                || player.getSetbackTeleportUtil().blockOffsets
                || (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8)
                    && player.checkManager.getNoSlow().didSlotChangeLastTick)) {
            suspiciousTicks = 0;
            resetSample();
            return;
        }

        if (sawCandidate && bestOffset > threshold) {
            suspiciousTicks++;
            if (suspiciousTicks >= 3) {
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
        threshold = config.getDoubleElse(getConfigName() + ".threshold", 0.0005);
    }
}
