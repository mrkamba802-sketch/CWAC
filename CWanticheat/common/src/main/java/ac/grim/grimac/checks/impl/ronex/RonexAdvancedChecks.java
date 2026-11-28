package ac.grim.grimac.checks.impl.ronex;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public final class RonexAdvancedChecks {
    private RonexAdvancedChecks() {}
    private static boolean isFishingRod(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String key = stack.getType().getName().getKey().toLowerCase(Locale.ROOT);
        return key.equals("fishing_rod") || key.endsWith(":fishing_rod");
    }
    private static double coefficientOfVariation(Deque<Long> values) {
        if (values.isEmpty()) return Double.POSITIVE_INFINITY;
        double mean = 0.0;
        for (long value : values) mean += value;
        mean /= values.size();
        if (mean <= 0.0) return Double.POSITIVE_INFINITY;
        double variance = 0.0;
        for (long value : values) {
            double difference = value - mean;
            variance += difference * difference;
        }
        return Math.sqrt(variance / values.size()) / mean;
    }

    @CheckData(name = "AutoFishA", stableKey = "ronexgrim.misc.autofish_rapid", experimental = false
            description = "Repeated unusually rapid fishing-rod use cycles")
    public static class AutoFishA extends Check implements PacketReceiveListener {
        private final Deque<Long> intervals = new ArrayDeque<>();
        private long lastUse, minimumInterval, maximumInterval;
        private int required;
        private double maximumVariation;
        public AutoFishA(GrimPlayer player) { super(player); }
        @Override public void onPacketReceive(PacketReceiveEvent event) {
            if (event.isCancelled() || event.getPacketType() != PacketType.Play.Client.USE_ITEM) return;
            WrapperPlayClientUseItem use = new WrapperPlayClientUseItem(event);
            if (!isFishingRod(player.inventory.getItemInHand(use.getHand()))) {
                lastUse = 0L;
                intervals.clear();
                return;
            }
            long now = System.currentTimeMillis();
            if (lastUse != 0L) {
                long interval = now - lastUse;
                if (interval >= minimumInterval && interval <= maximumInterval) {
                    intervals.addLast(interval);
                    while (intervals.size() > required + 2) intervals.removeFirst();
                } else intervals.clear();
            }
            lastUse = now;
            if (intervals.size() >= required) {
                double variation = coefficientOfVariation(intervals);
                if (variation <= maximumVariation) {
                    flag("regular-fishing-rod-uses=" + intervals.size()
                            + " interval-cv=" + formatOffset(variation));
                    intervals.clear();
                    lastUse = 0L;
                }
            }
        }
        @Override public void onReload(ConfigManager config) {
            minimumInterval = Math.max(25L, (long) config.getDoubleElse("AutoFishA.minimum-interval-ms", 35));
            maximumInterval = Math.max(minimumInterval + 20L,
                    (long) config.getDoubleElse("AutoFishA.maximum-interval-ms", 280));
            required = Math.max(4, (int) config.getDoubleElse("AutoFishA.required-intervals", 5));
            maximumVariation = Math.max(0.2,
                    Math.min(1.0, config.getDoubleElse("AutoFishA.maximum-variation", 0.75)));
            if (intervals != null) intervals.clear();
            lastUse = 0L;
        }
    }
}
