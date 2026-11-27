package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "KillAuraA", stableKey = "grim.combat.killaura_smooth_snap", description = "Repeatedly snapped or smoothly tracked onto targets at attack time", setback = 6, decay = 0.02)
public class KillAuraA extends Check implements PacketReceiveListener, RotationListener {
    private static final Verbose V = Verbose.of("angle={f64}, snap={bool}, smoothSamples={uint}, evidence={uint}");
    private double alignThreshold = 4.0;
    private double snapThreshold = 35.0;
    private double lastDeltaMagnitude;
    private int smoothSamples;
    private int evidence;
    private boolean recentSnap;
    private int recentSnapTicks;
    private int lastYawSign;
    private int lastPitchSign;

    public KillAuraA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void process(RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport || player.packetStateData.horseInteractCausedForcedRotation
                || player.vehicleData.wasVehicleSwitch || player.inVehicle() || !player.cameraEntity.isSelf()) {
            resetRotationState();
            return;
        }

        double yaw = rotationUpdate.deltaYaw();
        double pitch = rotationUpdate.deltaPitch();
        double magnitude = Math.hypot(yaw, pitch);
        if (magnitude >= snapThreshold) {
            recentSnap = true;
            recentSnapTicks = 2;
            smoothSamples = 0;
        } else if (recentSnapTicks > 0 && --recentSnapTicks == 0) {
            recentSnap = false;
        }

        int yawSign = sign(yaw);
        int pitchSign = sign(pitch);
        if (magnitude >= 0.08 && magnitude <= 18.0
                && (lastYawSign == 0 || yawSign == 0 || yawSign == lastYawSign)
                && (lastPitchSign == 0 || pitchSign == 0 || pitchSign == lastPitchSign)
                && Math.abs(magnitude - lastDeltaMagnitude) <= Math.max(0.75, lastDeltaMagnitude * 0.18)) {
            smoothSamples++;
        } else if (magnitude >= 0.08) {
            smoothSamples = Math.max(0, smoothSamples - 1);
        }

        if (yawSign != 0) lastYawSign = yawSign;
        if (pitchSign != 0) lastPitchSign = pitchSign;
        lastDeltaMagnitude = magnitude;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        int entityId;
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            entityId = new WrapperPlayClientAttack(event).getEntityId();
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
            entityId = packet.getEntityId();
        } else {
            return;
        }

        PacketEntity target = player.compensatedEntities.getEntity(entityId);
        if (!KillAuraUtil.canCheck(player, target)) return;

        double angle = KillAuraUtil.angleToTarget(player, target);
        boolean snappedOnTarget = recentSnap && angle <= alignThreshold;
        boolean smoothOnTarget = smoothSamples >= 5 && angle <= alignThreshold;
        if (snappedOnTarget || smoothOnTarget) {
            evidence++;
            if (evidence >= 3) {
                flag(V.write(verbose()).f64(angle).bool(snappedOnTarget).uint(smoothSamples).uint(evidence));
                evidence = 0;
            }
        } else {
            evidence = Math.max(0, evidence - 1);
            reward();
        }

        recentSnap = false;
        recentSnapTicks = 0;
        smoothSamples = Math.max(0, smoothSamples - 2);
    }

    private void resetRotationState() {
        lastDeltaMagnitude = 0.0;
        smoothSamples = 0;
        recentSnap = false;
        recentSnapTicks = 0;
        lastYawSign = 0;
        lastPitchSign = 0;
    }

    private static int sign(double value) {
        return value > 0 ? 1 : value < 0 ? -1 : 0;
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        alignThreshold = config.getDoubleElse(getConfigName() + ".align-threshold", 4.0);
        snapThreshold = config.getDoubleElse(getConfigName() + ".snap-threshold", 35.0);
    }
}
