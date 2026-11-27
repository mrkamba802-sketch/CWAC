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
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "AirStuckA", stableKey = "grim.movement.air_stuck_a", description = "Detects suspended air movement and suppresses combat and block actions while suspected", setback = 1, decay = 0.01)
public class AirStuckA extends Check implements PacketReceiveListener, PostPredictionListener {
    private static final Verbose V = Verbose.of("predictedY={f64}, actualY={f64}, offset={f64}, evidence={uint}");

    private volatile int lastMovementPacketTick;
    private volatile boolean actionSuppression;
    private volatile int lastSuspiciousTick;
    private int stagnantPredictionTicks;
    private int cleanPredictionTicks;
    private int lastSilentFlagTick = Integer.MIN_VALUE;
    private int packetSilenceTicks = 12;
    private int actionSuppressionTicks = 18;
    private double verticalOffsetThreshold = 0.005;

    public AirStuckA(GrimPlayer player) {
        super(player);
        lastMovementPacketTick = GrimAPI.INSTANCE.getTickManager().currentTick;
        lastSuspiciousTick = lastMovementPacketTick;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        int now = GrimAPI.INSTANCE.getTickManager().currentTick;
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            lastMovementPacketTick = now;
        }

        if (!isActionSuppressionActive() || !shouldModifyPackets() || !isRestrictedAction(event)) {
            return;
        }

        event.setCancelled(true);
        player.onPacketCancel();
    }

    private boolean isRestrictedAction(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ATTACK
                || event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING
                || event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            return true;
        }

        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            return new WrapperPlayClientInteractEntity(event).getAction()
                    == WrapperPlayClientInteractEntity.InteractAction.ATTACK;
        }
        return false;
    }

    @Override
    public void onPredictionComplete(PredictionComplete complete) {
        if (!complete.isChecked() || complete.getData() == null || complete.getData().isTeleport()
                || isExemptAirState() || player.getSetbackTeleportUtil().blockOffsets
                || player.packetStateData.lastPacketWasTeleport
                || player.uncertaintyHandler.wasAffectedByStuckSpeed()
                || player.predictedVelocity.isKnockback() || player.predictedVelocity.isExplosion()) {
            stagnantPredictionTicks = 0;
            if (player.onGround) clearActionSuppression();
            reward();
            return;
        }

        double predictedY = player.predictedVelocity.vector.getY();
        double actualY = player.actualMovement.getY();

        // One jump-apex tick can have almost no vertical delta. Require repeated divergence
        // while the server continues to predict a meaningful downward velocity.
        boolean ignoredGravity = predictedY < -0.12
                && actualY > -0.01
                && complete.getOffset() > verticalOffsetThreshold;

        if (ignoredGravity) {
            cleanPredictionTicks = 0;
            stagnantPredictionTicks++;
            if (stagnantPredictionTicks >= 3) {
                activateActionSuppression();
                flagWithSetback(V.write(verbose()).f64(predictedY).f64(actualY)
                        .f64(complete.getOffset()).uint(stagnantPredictionTicks));
                stagnantPredictionTicks = 0;
            }
        } else {
            stagnantPredictionTicks = Math.max(0, stagnantPredictionTicks - 1);
            boolean fallingNormally = predictedY < -0.08 && actualY < -0.025
                    && Math.abs(actualY - predictedY) < 0.12;
            if (player.onGround || fallingNormally) {
                cleanPredictionTicks++;
                if (cleanPredictionTicks >= 3) clearActionSuppression();
            } else {
                cleanPredictionTicks = 0;
            }
            reward();
        }
    }

    /** Called by the server-tick watchdog, including when the client sends no movement packets. */
    public void onServerTick() {
        if (isExemptAirState() || player.getSetbackTeleportUtil().blockOffsets
                || player.packetStateData.lastPacketWasTeleport
                || player.uncertaintyHandler.wasAffectedByStuckSpeed()
                || player.predictedVelocity.isKnockback() || player.predictedVelocity.isExplosion()) {
            if (player.onGround) clearActionSuppression();
            return;
        }

        int now = GrimAPI.INSTANCE.getTickManager().currentTick;
        int silentTicks = now - lastMovementPacketTick;
        double predictedY = player.predictedVelocity.vector.getY();
        if (silentTicks < packetSilenceTicks || predictedY >= -0.12) return;

        activateActionSuppression();
        if (lastSilentFlagTick == Integer.MIN_VALUE || now - lastSilentFlagTick >= 4) {
            flagWithSetback(V.write(verbose()).f64(predictedY).f64(player.actualMovement.getY())
                    .f64(0.0).uint(silentTicks));
            lastSilentFlagTick = now;
        }
    }

    private void activateActionSuppression() {
        actionSuppression = true;
        lastSuspiciousTick = GrimAPI.INSTANCE.getTickManager().currentTick;
        cleanPredictionTicks = 0;
    }

    private boolean isActionSuppressionActive() {
        if (!actionSuppression) return false;
        int now = GrimAPI.INSTANCE.getTickManager().currentTick;
        if (now - lastSuspiciousTick > actionSuppressionTicks) {
            actionSuppression = false;
            return false;
        }
        return true;
    }

    private void clearActionSuppression() {
        actionSuppression = false;
        cleanPredictionTicks = 0;
    }

    private boolean isExemptAirState() {
        return !player.cameraEntity.isSelf() || player.onGround || player.inVehicle()
                || player.isFlying || player.isGliding || player.isSwimming
                || player.wasTouchingWater || player.wasTouchingLava
                || player.isClimbing || player.isSlowMovement || player.isInBed;
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        packetSilenceTicks = Math.max(8, config.getIntElse(getConfigName() + ".packet-silence-ticks", 12));
        actionSuppressionTicks = Math.max(8, config.getIntElse(getConfigName() + ".action-suppression-ticks", 18));
        verticalOffsetThreshold = Math.max(0.001, config.getDoubleElse(
                getConfigName() + ".vertical-offset-threshold", 0.005));
    }
}
