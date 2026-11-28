package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.impl.aim.AimHitCheck;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import java.util.ArrayDeque;
import java.util.Deque;

@CheckData(name = "TriggerBotA", stableKey = "cwanticheat.combat.triggerbot_a", experimental = false
        description = "Repeated attacks immediately after a precise target-directed rotation")
public class TriggerBotA extends AimHitCheck implements PacketReceiveListener {
    private final Deque<Long> fastAimAttacks = new ArrayDeque<>();
    private long lastRotationAt, lastAttackAt, reactionWindowNanos, sampleWindowNanos, minimumAttackGapNanos;
    private double maximumAimError;
    private int requiredAttacks;
    public TriggerBotA(GrimPlayer player) { super(player); }
    @Override public void process(RotationUpdate update) {
        super.process(update);
        if (Math.abs(update.deltaYaw()) > 0.0005 || Math.abs(update.deltaPitch()) > 0.0005) {
            lastRotationAt = System.nanoTime();
        }
    }
    @Override public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled()
                || (event.getPacketType() != PacketType.Play.Client.ATTACK
                && event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY)) return;
        double aimError = getAimError(event);
        if (!Double.isFinite(aimError) || aimError > maximumAimError || lastRotationAt == 0L) return;
        long now = System.nanoTime();
        long reaction = now - lastRotationAt;
        if (reaction < 0L || reaction > reactionWindowNanos) return;
        if (lastAttackAt != 0L && now - lastAttackAt < minimumAttackGapNanos) return;
        while (!fastAimAttacks.isEmpty() && now - fastAimAttacks.peekFirst() > sampleWindowNanos) {
            fastAimAttacks.removeFirst();
        }
        fastAimAttacks.addLast(now);
        lastAttackAt = now;
        lastRotationAt = 0L;
        if (fastAimAttacks.size() >= requiredAttacks) {
            flag("rapid-aim-to-attack-sequences=" + fastAimAttacks.size()
                    + " reaction-ms=" + (reaction / 1_000_000L)
                    + " aim-error=" + formatOffset(aimError));
            fastAimAttacks.clear();
        }
    }
    @Override public void onReload(ConfigManager config) {
        maximumAimError = Math.max(0.2, config.getDoubleElse("TriggerBotA.maximum-aim-error", 0.55));
        reactionWindowNanos = Math.max(15L,
                (long) config.getDoubleElse("TriggerBotA.reaction-window-ms", 45)) * 1_000_000L;
        sampleWindowNanos = Math.max(1000L,
                (long) config.getDoubleElse("TriggerBotA.sample-window-ms", 3000)) * 1_000_000L;
        minimumAttackGapNanos = Math.max(40L,
                (long) config.getDoubleElse("TriggerBotA.minimum-attack-gap-ms", 65)) * 1_000_000L;
        requiredAttacks = Math.max(4, (int) config.getDoubleElse("TriggerBotA.required-attacks", 5));
        if (fastAimAttacks != null) fastAimAttacks.clear();
        lastRotationAt = 0L;
        lastAttackAt = 0L;
    }
}
