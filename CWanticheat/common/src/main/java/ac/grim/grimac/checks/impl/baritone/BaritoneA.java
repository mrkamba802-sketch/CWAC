package ac.grim.grimac.checks.impl.baritone;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.impl.aim.processor.AimProcessor;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.utils.math.GrimMath;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "BaritoneA", stableKey = "grim.baritone.baritone_a", description = "Detects repeated path-like micro-pitch corrections while automated movement continues", setback = 3, decay = 0.02)
public class BaritoneA extends Check implements RotationListener {
    private static final Verbose V = Verbose.of("pitchStep={f32}, pitchDivisor={f64}, evidence={uint}");
    private int evidence;
    private int requiredEvidence = 16;
    private double maxPitchStep = 1.0;

    public BaritoneA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void process(RotationUpdate update) {
        if (!player.cameraEntity.isSelf() || player.inVehicle() || player.isFlying || player.isGliding
                || Math.hypot(player.actualMovement.getX(), player.actualMovement.getZ()) < 0.02) {
            evidence = Math.max(0, evidence - 2);
            return;
        }

        double pitchStep = Math.abs(update.newPitch() - update.oldPitch());
        AimProcessor aim = player.checkManager.get(AimProcessor.class);
        boolean pathLikeMicroCorrection = Math.abs(update.deltaYaw()) < 0.001
                && pitchStep > 0.02 && pitchStep <= maxPitchStep
                && aim.divisorPitch < GrimMath.MINIMUM_DIVISOR;

        if (pathLikeMicroCorrection) {
            evidence++;
            if (evidence >= requiredEvidence) {
                flagWithSetback(V.write(verbose()).f32((float) pitchStep)
                        .f64(AimProcessor.convertToSensitivity(aim.divisorPitch)).uint(evidence));
                evidence = 0;
            }
        } else {
            evidence = Math.max(0, evidence - 2);
            reward();
        }
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        requiredEvidence = Math.max(8, config.getIntElse(getConfigName() + ".required-evidence", 16));
        maxPitchStep = Math.max(0.1, Math.min(1.5,
                config.getDoubleElse(getConfigName() + ".max-pitch-step", 1.0)));
    }
}
