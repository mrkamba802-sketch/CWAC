package ac.grim.grimac.checks.impl.movement;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AirStuckA", stableKey = "grim.movement.air_stuck_a", description = "Suspended in the air by suppressing movement packets or ignoring gravity", setback = 5, decay = 0.01)
public class AirStuckA extends Check implements PacketReceiveListener, PostPredictionListener {
    private static final Verbose V = Verbose.of("predictedY={f64}, actualY={f64}, offset={f64}, silentTicks={uint}");

    private volatile int lastMovementPacketTick;
    private volatile boolean alreadyFlaggedSilent;
    private int stagnantPredictionTicks;
    private int packetSilenceTicks = 7;
    private double verticalOffsetThreshold = 0.015;

    public AirStuckA(GrimPlayer player) {
        super(player);
        lastMovementPacketTick = GrimAPI.INSTANCE.getTickManager().currentTick;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            lastMovementPacketTick = GrimAPI.INSTANCE.getTickManager().currentTick;
            alreadyFlaggedSilent = false;
        }
    }

    @Override
    public void onPredictionComplete(PredictionComplete complete) {
        if (!complete.isChecked() || complete.getData() == null || complete.getData().isTeleport()
                || isExemptAirState() || player.getSetbackTeleportUtil().blockOffsets
                || player.packetStateData.lastPacketWasTeleport
                || player.uncertaintyHandler.wasAffectedByStuckSpeed()
                || player.predictedVelocity.isKnockback() || player.predictedVelocity.isExplosion()) {
            stagnantPredictionTicks = 0;
            reward();
            return;
        }

        double predictedY = player.predictedVelocity.vector.getY();
        double actualY = player.actualMovement.getY();
        boolean ignoredGravity = predictedY < -0.08 && Math.abs(actualY) < 0.001
                && complete.getOffset() > verticalOffsetThreshold;

        if (ignoredGravity) {
            if (++stagnantPredictionTicks >= 2) {
                flagWithSetback(V.write(verbose()).f64(predictedY).f64(actualY)
                        .f64(complete.getOffset()).uint(stagnantPredictionTicks));
                stagnantPredictionTicks = 0;
            }
        } else {
            stagnantPredictionTicks = Math.max(0, stagnantPredictionTicks - 1);
            reward();
        }
    }

    /** Called once per server tick, including when the client sends no movement packets. */
    public void onServerTick() {
        if (alreadyFlaggedSilent || isExemptAirState() || player.getSetbackTeleportUtil().blockOffsets
                || player.packetStateData.lastPacketWasTeleport || player.uncertaintyHandler.wasAffectedByStuckSpeed()
                || !player.isTickingReliablyFor(3)) {
            return;
        }

        int now = GrimAPI.INSTANCE.getTickManager().currentTick;
        int silentTicks = now - lastMovementPacketTick;
        if (silentTicks < packetSilenceTicks || player.predictedVelocity.vector.getY() >= -0.08
                || player.predictedVelocity.isKnockback() || player.predictedVelocity.isExplosion()) return;

        if (flagWithSetback(V.write(verbose()).f64(player.predictedVelocity.vector.getY())
                .f64(player.actualMovement.getY()).f64(0.0).uint(silentTicks))) {
            alreadyFlaggedSilent = true;
        }
    }

    private boolean isExemptAirState() {
        return player.onGround || player.inVehicle() || player.isFlying || player.isGliding
                || player.isSwimming || player.wasTouchingWater || player.wasTouchingLava
                || player.isClimbing || player.isSlowMovement || player.isInBed;
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        packetSilenceTicks = Math.max(4, config.getIntElse(getConfigName() + ".packet-silence-ticks", 7));
        verticalOffsetThreshold = config.getDoubleElse(getConfigName() + ".vertical-offset-threshold", 0.015);
    }
}
