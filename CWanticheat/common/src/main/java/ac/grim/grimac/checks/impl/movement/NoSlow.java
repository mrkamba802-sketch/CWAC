package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "NoSlow", stableKey = "grim.movement.noslow", description = "Was not slowed while using an item", setback = 5)
public class NoSlow extends Check implements PostPredictionListener {
    // Legacy clients can change slots one tick after starting or stopping item use.
    public boolean didSlotChangeLastTick = false;
    public boolean flaggedLastTick = false;
    private double offsetToFlag;
    private double bestOffset = 1;

    public NoSlow(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(final PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked()) return;

        if (player.packetStateData.isSlowedByUsingItem()) {
            if (player.getClientVersion().isOlderThanOrEquals(ClientVersion.V_1_8) && didSlotChangeLastTick) {
                didSlotChangeLastTick = false;
                flaggedLastTick = false;
            }

            // Require two consecutive prediction failures, but use a much smaller
            // residual threshold so a low-speed NoSlow advantage is not ignored.
            if (bestOffset > offsetToFlag) {
                if (flaggedLastTick) {
                    flagWithSetback();
                }
                flaggedLastTick = true;
            } else {
                reward();
                flaggedLastTick = false;
            }
        } else {
            flaggedLastTick = false;
        }
        bestOffset = 1;
    }

    public void handlePredictionAnalysis(double offset) {
        bestOffset = Math.min(bestOffset, offset);
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        offsetToFlag = config.getDoubleElse(getConfigName() + ".threshold", 0.0001);
    }
}
