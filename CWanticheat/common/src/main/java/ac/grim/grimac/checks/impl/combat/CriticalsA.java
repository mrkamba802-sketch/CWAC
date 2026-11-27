package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.ArrayDeque;
import java.util.Deque;

@CheckData(name = "CriticalsA", stableKey = "cwanticheat.combat.criticals_a",
        description = "Repeated attacks while the client claims airborne but server movement remains grounded",
        experimental = true)
public class CriticalsA extends Check implements PacketReceiveListener {
    private final Deque<Long> suspiciousAttacks = new ArrayDeque<>();
    private double groundVerticalEpsilon;
    private long windowNanos;
    private int requiredAttacks;
    public CriticalsA(GrimPlayer player) { super(player); }
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
        int targetId = attackedEntityId(event);
        if (targetId < 0 || player.inVehicle() || player.isFlying || player.isGliding
                || player.isSwimming || player.isClimbing) return;
        PacketEntity target = player.compensatedEntities.entityMap.get(targetId);
        if (target == null || !target.isLivingEntity || target.isDead) return;

        boolean serverGrounded = player.onGround && player.lastOnGround;
        boolean clientClaimsAir = !player.clientClaimsLastOnGround;
        boolean noJumpTrajectory = Math.abs(player.actualMovement.getY()) <= groundVerticalEpsilon
                && player.fallDistance < 0.1F;
        long now = System.nanoTime();
        while (!suspiciousAttacks.isEmpty() && now - suspiciousAttacks.peekFirst() > windowNanos) {
            suspiciousAttacks.removeFirst();
        }
        if (!serverGrounded || !clientClaimsAir || !noJumpTrajectory) {
            suspiciousAttacks.clear();
            return;
        }
        suspiciousAttacks.addLast(now);
        if (suspiciousAttacks.size() >= requiredAttacks) {
            flag("grounded-air-claim-attacks=" + suspiciousAttacks.size()
                    + " vertical-motion=" + formatOffset(player.actualMovement.getY()));
            suspiciousAttacks.clear();
        }
    }
    @Override public void onReload(ConfigManager config) {
        groundVerticalEpsilon = Math.max(0.005,
                config.getDoubleElse("CriticalsA.grounded-vertical-epsilon", 0.02));
        windowNanos = Math.max(500L, (long) config.getDoubleElse("CriticalsA.window-ms", 2000)) * 1_000_000L;
        requiredAttacks = Math.max(3, (int) config.getDoubleElse("CriticalsA.required-attacks", 4));
        if (suspiciousAttacks != null) suspiciousAttacks.clear();
    }
}
