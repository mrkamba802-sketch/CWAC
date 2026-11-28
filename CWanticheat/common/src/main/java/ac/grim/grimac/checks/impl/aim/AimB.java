package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.impl.aim.processor.AimProcessor;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AimB", stableKey = "grim.aim.gcd_snap", experimental = false
        description = "Repeated large rotations contradict a stable mouse movement grid", decay = 0.02)
public class AimB extends Check implements RotationListener {
    private double snapThreshold = 50.0;
    private double gridTolerance = 0.08;
    private int requiredEvidence = 5;
    private int evidence;
    private int stableYawSamples;
    private int stablePitchSamples;
    private double previousYawMode;
    private double previousPitchMode;

    public AimB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void process(RotationUpdate update) {
        if (player.packetStateData.lastPacketWasTeleport
                || player.packetStateData.horseInteractCausedForcedRotation
                || player.vehicleData.wasVehicleSwitch || player.inVehicle()
                || !player.cameraEntity.isSelf()) {
            resetEvidence();
            return;
        }

        AimProcessor aim = player.checkManager.get(AimProcessor.class);
        if (aim == null || !validGrid(aim.modeYaw) || !validGrid(aim.modePitch)
                || !Double.isFinite(aim.deltaDotsYaw) || !Double.isFinite(aim.deltaDotsPitch)) {
            stableYawSamples = 0;
            stablePitchSamples = 0;
            previousYawMode = 0.0;
            previousPitchMode = 0.0;
            evidence = Math.max(0, evidence - 1);
            return;
        }

        stableYawSamples = updateStability(aim.modeYaw, previousYawMode, stableYawSamples);
        stablePitchSamples = updateStability(aim.modePitch, previousPitchMode, stablePitchSamples);
        previousYawMode = aim.modeYaw;
        previousPitchMode = aim.modePitch;

        if (stableYawSamples < 8 || stablePitchSamples < 8) return;

        double deltaYaw = update.deltaYawABS();
        double deltaPitch = update.deltaPitchABS();
        if (Math.max(deltaYaw, deltaPitch) < snapThreshold) {
            evidence = Math.max(0, evidence - 1);
            return;
        }

        boolean yawOffGrid = deltaYaw >= snapThreshold
                && distanceToInteger(aim.deltaDotsYaw) > gridTolerance;
        boolean pitchOffGrid = deltaPitch >= snapThreshold
                && distanceToInteger(aim.deltaDotsPitch) > gridTolerance;

        if (yawOffGrid && pitchOffGrid) {
            evidence++;
            if (evidence >= requiredEvidence) {
                flag("large-two-axis-snap-off-grid yawDots=" + formatOffset(aim.deltaDotsYaw)
                        + " pitchDots=" + formatOffset(aim.deltaDotsPitch)
                        + " evidence=" + evidence);
                resetEvidence();
            }
        } else {
            evidence = Math.max(0, evidence - 1);
            reward();
        }
    }

    private static boolean validGrid(double value) {
        return Double.isFinite(value) && value >= 0.0001 && value <= 5.0;
    }

    private static int updateStability(double current, double previous, int samples) {
        if (previous <= 0.0) return 1;
        double tolerance = Math.max(0.006, previous * 0.15);
        return Math.abs(current - previous) <= tolerance ? Math.min(20, samples + 1) : 1;
    }

    private static double distanceToInteger(double value) {
        return Math.abs(value - Math.rint(value));
    }

    private void resetEvidence() {
        evidence = 0;
        stableYawSamples = 0;
        stablePitchSamples = 0;
        previousYawMode = 0.0;
        previousPitchMode = 0.0;
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        snapThreshold = Math.max(45.0, config.getDoubleElse(getConfigName() + ".snap-threshold", 50.0));
        gridTolerance = Math.max(0.05, Math.min(0.20,
                config.getDoubleElse(getConfigName() + ".gcd-tolerance", 0.08)));
        requiredEvidence = Math.max(4,
                (int) config.getDoubleElse(getConfigName() + ".required-evidence", 5));
        resetEvidence();
    }
}
