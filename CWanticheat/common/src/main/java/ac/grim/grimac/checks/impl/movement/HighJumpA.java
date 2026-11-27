package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "HighJumpA", stableKey = "grim.movement.high_jump_a", description = "Exceeded vanilla upward velocity after block-assisted jumps", setback = 8, decay = 0.02)
public class HighJumpA extends Check implements PostPredictionListener {
    private static final Verbose V = Verbose.of("expectedY={f64}, actualY={f64}, excess={f64}, streak={uint}");
    private double excessThreshold = 0.12;
    private int suspiciousTicks;

    public HighJumpA(GrimPlayer player) {
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
            suspiciousTicks = 0;
            reward();
            return;
        }

        double expectedY = player.predictedVelocity.vector.getY();
        double actualY = player.actualMovement.getY();
        double excess = actualY - expectedY;
        boolean abnormalRise = actualY > 0.18 && excess > excessThreshold
                && complete.getOffset() > excessThreshold * 0.5;

        if (abnormalRise) {
            if (++suspiciousTicks >= 2) {
                flagWithSetback(V.write(verbose()).f64(expectedY).f64(actualY).f64(excess).uint(suspiciousTicks));
                suspiciousTicks = 0;
            }
        } else {
            suspiciousTicks = Math.max(0, suspiciousTicks - 1);
            reward();
        }
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        excessThreshold = config.getDoubleElse(getConfigName() + ".vertical-excess-threshold", 0.12);
    }
}
