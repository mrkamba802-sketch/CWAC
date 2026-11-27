package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.impl.aim.processor.AimProcessor;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;

@CheckData(name = "AimA", stableKey = "grim.aim.yaw_pitch_delta", description = "Yaw/pitch sensitivity diverged from the vanilla mouse delta model", decay = 0.02)
public class AimA extends Check implements RotationListener {
    private static final Verbose V = Verbose.of("yawSensitivity={f64}, pitchSensitivity={f64}, difference={f64}, samples={uint}");
    private int divergentSamples;

    public AimA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport || player.packetStateData.horseInteractCausedForcedRotation
                || player.vehicleData.wasVehicleSwitch || player.inVehicle() || !player.cameraEntity.isSelf()) {
            divergentSamples = 0;
            return;
        }

        AimProcessor aim = player.checkManager.get(AimProcessor.class);
        if (aim.modeYaw <= 0.0001 || aim.modePitch <= 0.0001
                || rotationUpdate.deltaYawABS() < 0.01f || rotationUpdate.deltaPitchABS() < 0.01f) {
            divergentSamples = Math.max(0, divergentSamples - 1);
            return;
        }

        double yawSensitivity = aim.sensitivityYaw;
        double pitchSensitivity = aim.sensitivityPitch;
        if (!Double.isFinite(yawSensitivity) || !Double.isFinite(pitchSensitivity)
                || yawSensitivity < -0.05 || yawSensitivity > 1.05
                || pitchSensitivity < -0.05 || pitchSensitivity > 1.05) {
            divergentSamples = 0;
            return;
        }

        double difference = Math.abs(yawSensitivity - pitchSensitivity);
        if (difference > 0.22) {
            divergentSamples++;
            if (divergentSamples >= 14) {
                flag(V.write(verbose()).f64(yawSensitivity).f64(pitchSensitivity)
                        .f64(difference).uint(divergentSamples));
                divergentSamples = 0;
            }
        } else {
            divergentSamples = Math.max(0, divergentSamples - 2);
            reward();
        }
    }
}
