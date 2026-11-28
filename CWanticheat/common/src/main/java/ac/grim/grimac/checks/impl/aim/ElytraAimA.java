package ac.grim.grimac.checks.impl.aim;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import java.util.ArrayDeque;
import java.util.Deque;

@CheckData(name = "ElytraAimA", stableKey = "ronexgrim.aim.elytra_snap", experimental = false
        description = "Repeated attack-timed snap rotations while gliding")
public class ElytraAimA extends AimHitCheck implements PacketReceiveListener {
    private final Deque<Boolean> hits = new ArrayDeque<>();
    private double minimumSnapDegrees, maximumAimError;
    private int requiredHits, window;
    private long lastRotationAt;
    private double lastRotationDelta;
    public ElytraAimA(GrimPlayer player) { super(player); }
    @Override public void process(RotationUpdate update) {
        super.process(update);
        lastRotationDelta = Math.hypot(update.deltaYaw(), update.deltaPitch());
        lastRotationAt = System.nanoTime();
    }
    @Override public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled()
                || (event.getPacketType() != PacketType.Play.Client.ATTACK
                && event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY)) return;
        double error = getAimError(event);
        if (!Double.isFinite(error)) return;
        if (!player.isGliding) {
            hits.clear();
            return;
        }
        long now = System.nanoTime();
        boolean suspicious = lastRotationAt != 0L && now - lastRotationAt <= 180_000_000L
                && lastRotationDelta >= minimumSnapDegrees && error <= maximumAimError;
        hits.addLast(suspicious);
        while (hits.size() > window) hits.removeFirst();
        int count = 0;
        for (boolean sample : hits) if (sample) count++;
        if (hits.size() == window && count >= requiredHits) {
            flag("elytra-snap-hits=" + count + "/" + window + " delta=" + formatOffset(lastRotationDelta));
            hits.clear();
        } else if (!suspicious) reward();
    }
    @Override public void onReload(ConfigManager config) {
        minimumSnapDegrees = Math.max(8.0, config.getDoubleElse("ElytraAimA.minimum-snap-degrees", 13.0));
        maximumAimError = Math.max(0.25, config.getDoubleElse("ElytraAimA.maximum-aim-error", 1.2));
        requiredHits = Math.max(2, (int) config.getDoubleElse("ElytraAimA.required-hits", 3));
        window = Math.max(requiredHits, (int) config.getDoubleElse("ElytraAimA.hit-window", 6));
        if (hits != null) hits.clear();
        lastRotationAt = 0L;
        lastRotationDelta = 0.0;
    }
}
