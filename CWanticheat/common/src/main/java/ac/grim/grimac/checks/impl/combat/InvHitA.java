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

@CheckData(name = "InvHitA", stableKey = "ronexgrim.inventory.attack_with_menu_open", experimental = false
        description = "Detects repeated attacks on living entities while an inventory menu is open")
public class InvHitA extends Check implements PacketReceiveListener {
    private final Deque<Long> attacks = new ArrayDeque<>();
    private long windowNanos, graceNanos;
    private int requiredAttacks;
    public InvHitA(GrimPlayer player) { super(player); }
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
        boolean menuOpen = player.hasInventoryOpen || player.inventory.openWindowID != 0;
        if (!menuOpen) {
            attacks.clear();
            return;
        }
        long nowMillis = System.currentTimeMillis();
        if (nowMillis - player.lastInventoryOpen < graceNanos / 1_000_000L) return;
        int targetId = attackedEntityId(event);
        if (targetId < 0) return;
        PacketEntity target = player.compensatedEntities.entityMap.get(targetId);
        if (target == null || !target.isLivingEntity || target.isDead) return;
        long now = System.nanoTime();
        while (!attacks.isEmpty() && now - attacks.peekFirst() > windowNanos) attacks.removeFirst();
        attacks.addLast(now);
        if (attacks.size() >= requiredAttacks) {
            flag("attacks-with-inventory-open=" + attacks.size()
                    + " window-ms=" + (windowNanos / 1_000_000L));
            attacks.clear();
        }
    }
    @Override public void onReload(ConfigManager config) {
        windowNanos = Math.max(300L, (long) config.getDoubleElse("InvHitA.window-ms", 1200)) * 1_000_000L;
        graceNanos = Math.max(0L, (long) config.getDoubleElse("InvHitA.open-grace-ms", 180)) * 1_000_000L;
        requiredAttacks = Math.max(2, (int) config.getDoubleElse("InvHitA.required-attacks", 2));
        if (attacks != null) attacks.clear();
    }
}
