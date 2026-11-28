package ac.grim.grimac.checks.impl.inventory;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AutoSwapA", stableKey = "grim.inventory.auto_swap_a", experimental = false description = "Repeated rapid hotbar swap, attack, and return cycles", setback = 0, decay = 0.02)
public class AutoSwapA extends Check implements PacketReceiveListener {
    private static final Verbose V = Verbose.of("fromSlot={uint}, attackDelayMs={ulong}, cycleMs={ulong}, cycles={uint}");
    private int originalSlot = -1;
    private int lastSlot = -1;
    private long swapStartedNanos;
    private long attackNanos;
    private long lastCycleNanos;
    private boolean attackedDuringSwap;
    private int suspiciousCycles;
    private long cycleWindowNanos = 350_000_000L;

    public AutoSwapA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        long now = System.nanoTime();
        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            int selected = new WrapperPlayClientHeldItemChange(event).getSlot();
            if (lastSlot < 0) lastSlot = player.packetStateData.lastSlotSelected;

            if (originalSlot < 0) {
                originalSlot = lastSlot;
                swapStartedNanos = now;
                attackedDuringSwap = false;
            } else if (selected == originalSlot) {
                long cycleNanos = now - swapStartedNanos;
                long attackDelay = attackedDuringSwap ? now - attackNanos : Long.MAX_VALUE;
                if (attackedDuringSwap && cycleNanos <= cycleWindowNanos && attackDelay <= 220_000_000L) {
                    if (now - lastCycleNanos > 2_000_000_000L) suspiciousCycles = 0;
                    suspiciousCycles++;
                    lastCycleNanos = now;
                    if (suspiciousCycles >= 3) {
                        flag(V.write(verbose()).uint(originalSlot).ulong(attackDelay / 1_000_000L)
                                .ulong(cycleNanos / 1_000_000L).uint(suspiciousCycles));
                        suspiciousCycles = 0;
                    }
                } else {
                    suspiciousCycles = Math.max(0, suspiciousCycles - 1);
                }
                originalSlot = -1;
                attackedDuringSwap = false;
            } else if (now - swapStartedNanos > cycleWindowNanos) {
                originalSlot = lastSlot;
                swapStartedNanos = now;
                attackedDuringSwap = false;
            }
            lastSlot = selected;
        } else if (isAttack(event)) {
            if (originalSlot >= 0 && now - swapStartedNanos <= cycleWindowNanos) {
                attackedDuringSwap = true;
                attackNanos = now;
            }
        }
    }

    private boolean isAttack(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) return true;
        return event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY
                && new WrapperPlayClientInteractEntity(event).getAction()
                == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        cycleWindowNanos = Math.max(150, config.getIntElse(getConfigName() + ".cycle-window-ms", 350)) * 1_000_000L;
    }
}
