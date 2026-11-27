package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AirStuck", stableKey = "grim.movement.air_stuck", description = "Stopped vertical movement while vanilla prediction requires falling", setback = 8, decay = 0.02)
public class AirStuck extends Check implements PostPredictionListener {
    private static final Verbose V = Verbose.of("predictedY={f64}, actualY={f64}, offset={f64}, ticks={uint}");
    private int stagnantTicks;
    private double offsetThreshold = 0.03;

    public AirStuck(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked() || predictionComplete.getData() == null
                || predictionComplete.getData().isTeleport()
                || player.getSetbackTeleportUtil().blockOffsets
                || player.packetStateData.lastPacketWasTeleport
                || player.inVehicle() || player.isFlying || player.isGliding
                || player.wasTouchingWater || player.wasTouchingLava || player.isClimbing
                || player.isSlowMovement || player.onGround || player.fallDistance < 1.5
                || player.uncertaintyHandler.wasAffectedByStuckSpeed()) {
            stagnantTicks = 0;
            return;
        }

        double predictedY = player.predictedVelocity.vector.getY();
        double actualY = player.actualMovement.getY();
        boolean stoppedInAir = predictedY < -0.12
                && Math.abs(actualY) < 1.0E-5
                && predictionComplete.getOffset() > offsetThreshold;

        if (stoppedInAir) {
            stagnantTicks++;
            if (stagnantTicks >= 3) {
                flagWithSetback(V.write(verbose()).f64(predictedY).f64(actualY)
                        .f64(predictionComplete.getOffset()).uint(stagnantTicks));
                stagnantTicks = 0;
            }
        } else {
            stagnantTicks = Math.max(0, stagnantTicks - 1);
            reward();
        }
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        offsetThreshold = config.getDoubleElse(getConfigName() + ".threshold", 0.03);
    }
}
