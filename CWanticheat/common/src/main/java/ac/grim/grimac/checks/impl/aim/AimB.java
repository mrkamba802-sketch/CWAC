package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.impl.aim.processor.AimProcessor;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AimB", stableKey = "grim.aim.gcd_snap", description = "Large rotation snaps do not follow the client's observed mouse GCD", decay = 0.02)
public class AimB extends Check implements RotationListener {
    private static final Verbose V = Verbose.of("deltaYaw={f32}, deltaPitch={f32}, yawDots={f64}, pitchDots={f64}, evidence={uint}");
    private double snapThreshold = 35.0;
    private double gridTolerance = 0.02;
    private int evidence;

    public AimB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport || player.packetStateData.horseInteractCausedForcedRotation
                || player.vehicleData.wasVehicleSwitch || player.inVehicle() || !player.cameraEntity.isSelf()) {
            evidence = 0;
            return;
        }

        double deltaYaw = rotationUpdate.deltaYawABS();
        double deltaPitch = rotationUpdate.deltaPitchABS();
        if (Math.max(deltaYaw, deltaPitch) < snapThreshold) {
            evidence = Math.max(0, evidence - 1);
            return;
        }

        AimProcessor aim = player.checkManager.get(AimProcessor.class);
        if (aim.modeYaw <= 0.0001 || aim.modePitch <= 0.0001
                || !Double.isFinite(aim.deltaDotsYaw) || !Double.isFinite(aim.deltaDotsPitch)) {
            return;
        }

        boolean yawOffGrid = deltaYaw > 0.01 && distanceToInteger(aim.deltaDotsYaw) > gridTolerance;
        boolean pitchOffGrid = deltaPitch > 0.01 && distanceToInteger(aim.deltaDotsPitch) > gridTolerance;
        if (yawOffGrid || pitchOffGrid) {
            evidence++;
            if (evidence >= 3) {
                flag(V.write(verbose()).f32(rotationUpdate.deltaYaw()).f32(rotationUpdate.deltaPitch())
                        .f64(aim.deltaDotsYaw).f64(aim.deltaDotsPitch).uint(evidence));
                evidence = 0;
            }
        } else {
            evidence = Math.max(0, evidence - 1);
            reward();
        }
    }

    private static double distanceToInteger(double value) {
        return Math.abs(value - Math.rint(value));
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        snapThreshold = config.getDoubleElse(getConfigName() + ".snap-threshold", 35.0);
        gridTolerance = config.getDoubleElse(getConfigName() + ".gcd-tolerance", 0.02);
    }
}
