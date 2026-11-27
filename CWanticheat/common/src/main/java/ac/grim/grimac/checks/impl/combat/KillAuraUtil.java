package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.boundingbox.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import ac.grim.grimac.utils.math.Vector3dm;
import ac.grim.grimac.utils.nmsutil.ReachUtils;

final class KillAuraUtil {
    private KillAuraUtil() {}

    static double angleToTarget(GrimPlayer player, PacketEntity entity) {
        SimpleCollisionBox box = entity.getPossibleCollisionBoxes();
        double targetX = (box.minX + box.maxX) * 0.5;
        double targetY = box.minY + (box.maxY - box.minY) * 0.68;
        double targetZ = (box.minZ + box.maxZ) * 0.5;

        double dx = targetX - player.x;
        double dy = targetY - (player.y + player.getEyeHeight());
        double dz = targetZ - player.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6) return 0.0;

        Vector3dm look = ReachUtils.getLook(player, player.yaw, player.pitch);
        double dot = (look.getX() * dx + look.getY() * dy + look.getZ() * dz) / length;
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.toDegrees(Math.acos(dot));
    }

    static boolean canCheck(GrimPlayer player, PacketEntity target) {
        return target != null && target.isLivingEntity && !target.isDead
                && player.cameraEntity.isSelf() && !player.inVehicle()
                && player.gamemode != com.github.retrooper.packetevents.protocol.player.GameMode.SPECTATOR
                && !player.packetStateData.lastPacketWasTeleport
                && !player.vehicleData.wasVehicleSwitch
                && player.isTickingReliablyFor(3);
    }
}
