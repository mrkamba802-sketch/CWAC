package ac.grim.grimac.checks.impl.groundspoof;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;

@CheckData(name = "NoFallA", stableKey = "grim.groundspoof.no_fall_a", description = "Claimed ground contact while still falling", setback = 8, decay = 0.02)
public class NoFallA extends Check implements PostPredictionListener {
    private static final Verbose V = Verbose.of("fallDistance={f64}, motionY={f64}, ground={bool}");
    private int suspiciousTicks;

    public NoFallA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPredictionComplete(PredictionComplete predictionComplete) {
        if (!predictionComplete.isChecked() || predictionComplete.getData() == null
                || predictionComplete.getData().isTeleport()
                || player.exemptOnGround()
                || player.getSetbackTeleportUtil().blockOffsets
                || player.packetStateData.lastPacketWasTeleport
                || player.inVehicle() || player.isFlying || player.isGliding) {
            suspiciousTicks = 0;
            return;
        }

        boolean suspicious = player.clientClaimsLastOnGround
                && !player.onGround
                && player.fallDistance > 2.0
                && player.actualMovement.getY() < -0.05;

        if (suspicious) {
            suspiciousTicks++;
            if (suspiciousTicks >= 2) {
                flagWithSetback(V.write(verbose()).f64(player.fallDistance)
                        .f64(player.actualMovement.getY()).bool(player.clientClaimsLastOnGround));
                suspiciousTicks = 0;
            }
        } else {
            suspiciousTicks = Math.max(0, suspiciousTicks - 1);
            reward();
        }
    }
}
