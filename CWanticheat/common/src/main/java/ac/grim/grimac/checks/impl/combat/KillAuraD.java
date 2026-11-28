package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

@CheckData(name = "KillAuraD", stableKey = "cwanticheat.combat.kill_aura_d", experimental = false
        description = "Repeatedly switches between separated living targets without enough intervening rotation")
public class KillAuraD extends Check implements PacketReceiveListener, RotationListener {
    private final Deque<Boolean> switchSamples = new ArrayDeque<>();

    private int lastTargetId = -1;
    private double lastTargetX;
    private double lastTargetY;
    private double lastTargetZ;
    private long lastAttackAt;
    private double rotationSinceAttack;

    private double minimumTargetAngle;
    private double maximumObservedTurn;
    private double maximumDistance;
    private long maximumAttackGapNanos;
    private int window;
    private int requiredSwitches;

    public KillAuraD(GrimPlayer player) {
        super(player);
    }

    @Override
    public void process(RotationUpdate update) {
        double yaw = wrapDegrees(update.deltaYaw());
        rotationSinceAttack += Math.hypot(yaw, update.deltaPitch());
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled() || player.inVehicle() || !player.cameraEntity.isSelf()
                || player.getSetbackTeleportUtil().insideUnloadedChunk()) return;

        int targetId = attackedEntityId(event);
        if (targetId < 0) return;
        PacketEntity target = player.compensatedEntities.entityMap.get(targetId);
        SimpleCollisionBox box = target == null || !target.isLivingEntity || target.isDead
                ? null : target.getPossibleCollisionBoxes();
        if (box == null) {
            resetTarget();
            return;
        }

        double x = (box.minX + box.maxX) * 0.5;
        double y = (box.minY + box.maxY) * 0.5;
        double z = (box.minZ + box.maxZ) * 0.5;
        double eyeY = player.y + player.getEyeHeight();
        double distance = Math.sqrt((x - player.x) * (x - player.x)
                + (y - eyeY) * (y - eyeY) + (z - player.z) * (z - player.z));
        long now = System.nanoTime();

        if (lastTargetId >= 0 && targetId != lastTargetId
                && now - lastAttackAt <= maximumAttackGapNanos) {
            double previousDistance = Math.sqrt((lastTargetX - player.x) * (lastTargetX - player.x)
                    + (lastTargetY - eyeY) * (lastTargetY - eyeY)
                    + (lastTargetZ - player.z) * (lastTargetZ - player.z));
            double targetAngle = angleBetween(
                    lastTargetX - player.x, lastTargetY - eyeY, lastTargetZ - player.z,
                    x - player.x, y - eyeY, z - player.z);
            boolean suspicious = previousDistance <= maximumDistance && distance <= maximumDistance
                    && targetAngle >= minimumTargetAngle
                    && rotationSinceAttack <= maximumObservedTurn;

            switchSamples.addLast(suspicious);
            while (switchSamples.size() > window) switchSamples.removeFirst();
            int evidence = countEvidence();
            if (switchSamples.size() == window && evidence >= requiredSwitches) {
                flag("low-rotation-target-switches=" + evidence + "/" + window
                        + " target-angle=" + formatOffset(targetAngle)
                        + " observed-turn=" + formatOffset(rotationSinceAttack));
                switchSamples.clear();
            } else if (!suspicious) {
                reward();
            }
        }

        lastTargetId = targetId;
        lastTargetX = x;
        lastTargetY = y;
        lastTargetZ = z;
        lastAttackAt = now;
        rotationSinceAttack = 0.0;
    }

    private static int attackedEntityId(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            return new WrapperPlayClientAttack(event).getEntityId();
        }
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            return packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK
                    ? packet.getEntityId() : -1;
        }
        return -1;
    }

    private static double angleBetween(double ax, double ay, double az,
                                       double bx, double by, double bz) {
        double aLength = Math.sqrt(ax * ax + ay * ay + az * az);
        double bLength = Math.sqrt(bx * bx + by * by + bz * bz);
        if (aLength < 0.001 || bLength < 0.001) return 0.0;
        double dot = (ax * bx + ay * by + az * bz) / (aLength * bLength);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    private int countEvidence() {
        int count = 0;
        for (boolean sample : switchSamples) {
            if (sample) count++;
        }
        return count;
    }

    private static double wrapDegrees(double value) {
        value %= 360.0;
        if (value >= 180.0) value -= 360.0;
        if (value < -180.0) value += 360.0;
        return value;
    }

    private void resetTarget() {
        lastTargetId = -1;
        lastAttackAt = 0L;
        rotationSinceAttack = 0.0;
        if (switchSamples != null) switchSamples.clear();
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        minimumTargetAngle = Math.max(6.0,
                config.getDoubleElse(getConfigName() + ".minimum-target-angle-degrees", 14.0));
        maximumObservedTurn = Math.max(0.5,
                config.getDoubleElse(getConfigName() + ".maximum-observed-turn-degrees", 5.0));
        maximumDistance = Math.max(3.0,
                config.getDoubleElse(getConfigName() + ".maximum-distance", 6.0));
        maximumAttackGapNanos = Math.max(50L,
                config.getIntElse(getConfigName() + ".maximum-attack-gap-ms", 250)) * 1_000_000L;
        window = Math.max(5, config.getIntElse(getConfigName() + ".window", 8));
        requiredSwitches = Math.max(3,
                Math.min(window, config.getIntElse(getConfigName() + ".required-switches", 4)));
        resetTarget();
    }
}
