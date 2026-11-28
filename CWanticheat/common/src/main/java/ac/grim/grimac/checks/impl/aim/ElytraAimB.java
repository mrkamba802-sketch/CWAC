package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

@CheckData(name = "ElytraAimB", stableKey = "ronexgrim.aim.elytra_target_tracking", experimental = false
        description = "Repeated near-center attacks against moving targets while gliding")
public class ElytraAimB extends AimHitCheck implements PacketReceiveListener {
    private int targetId = -1;
    private int requiredHits, window;
    private double[] lastCenter;
    private double minimumTargetMovement, maximumSpatialError;
    private int samples, suspiciousHits;
    public ElytraAimB(GrimPlayer player) { super(player); }
    private int attackedEntityId(PacketReceiveEvent event) {
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
    @Override public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled()) return;
        int id = attackedEntityId(event);
        if (id < 0) return;
        if (!player.isGliding) {
            reset();
            return;
        }
        PacketEntity target = player.compensatedEntities.entityMap.get(id);
        if (target == null || !target.isLivingEntity || target.isDead || player.inVehicle()) return;
        SimpleCollisionBox box = target.getPossibleCollisionBoxes();
        if (box == null) return;
        double[] center = new double[] {
                (box.minX + box.maxX) * 0.5,
                (box.minY + box.maxY) * 0.5,
                (box.minZ + box.maxZ) * 0.5
        };
        if (targetId != id || lastCenter == null) {
            targetId = id;
            lastCenter = center;
            samples = 0;
            suspiciousHits = 0;
            return;
        }
        double dx = center[0] - lastCenter[0];
        double dy = center[1] - lastCenter[1];
        double dz = center[2] - lastCenter[2];
        double movement = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double error = getAimError(event);
        double distanceX = center[0] - player.x;
        double distanceY = center[1] - (player.y + player.getEyeHeight());
        double distanceZ = center[2] - player.z;
        double distance = Math.sqrt(distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ);
        double spatialError = Double.isFinite(error) && Double.isFinite(distance)
                ? Math.tan(Math.toRadians(error)) * distance : Double.POSITIVE_INFINITY;
        boolean suspicious = movement >= minimumTargetMovement && spatialError <= maximumSpatialError;
        samples++;
        if (suspicious) suspiciousHits++;
        if (samples >= window) {
            if (suspiciousHits >= requiredHits) {
                flag("elytra-target-lock=" + suspiciousHits + "/" + samples
                        + " spatial=" + formatOffset(spatialError));
            } else reward();
            samples = 0;
            suspiciousHits = 0;
        }
        lastCenter = center;
    }
    private void reset() {
        targetId = -1;
        lastCenter = null;
        samples = 0;
        suspiciousHits = 0;
    }
    @Override public void onReload(ConfigManager config) {
        minimumTargetMovement = Math.max(0.03,
                config.getDoubleElse("ElytraAimB.minimum-target-movement", 0.10));
        maximumSpatialError = Math.max(0.04,
                config.getDoubleElse("ElytraAimB.maximum-spatial-error", 0.16));
        requiredHits = Math.max(3, (int) config.getDoubleElse("ElytraAimB.required-hits", 4));
        window = Math.max(requiredHits, (int) config.getDoubleElse("ElytraAimB.hit-window", 7));
        reset();
    }
}
