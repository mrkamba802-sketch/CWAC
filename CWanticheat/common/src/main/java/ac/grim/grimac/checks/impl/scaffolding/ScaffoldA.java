package ac.grim.grimac.checks.impl.scaffolding;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.BlockPlaceCheck;
import ac.grim.grimac.checks.type.BlockPlaceListener;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockPlace;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "ScaffoldA", stableKey = "grim.scaffolding.scaffold_a", description = "Repeatedly placed blocks at foot level while moving with a forward-facing view", setback = 6, decay = 0.02)
public class ScaffoldA extends BlockPlaceCheck implements BlockPlaceListener, PacketReceiveListener {
    private static final Verbose V = Verbose.of("horizontalSpeed={f64}, pitch={f32}, placeY={uint}, evidence={uint}");
    private static final long EVIDENCE_WINDOW_NANOS = 400_000_000L;
    private static final long ENTITY_INTERACTION_GRACE_NANOS = 750_000_000L;
    private int evidence;
    private long lastSuspiciousPlace;
    private long lastEntityInteraction;
    private int evidenceToFlag = 6;

    public ScaffoldA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            lastEntityInteraction = System.nanoTime();
            evidence = 0;
            lastSuspiciousPlace = 0L;
        }
    }

    @Override
    public void onBlockPlace(BlockPlace place) {
        long now = System.nanoTime();
        if (place.material == StateTypes.SCAFFOLDING || !player.cameraEntity.isSelf()
                || player.inVehicle() || player.isFlying || player.isGliding || player.isSwimming
                || player.hasInventoryOpen || player.packetStateData.lastPacketWasTeleport
                || !player.isTickingReliablyFor(3)
                || (lastEntityInteraction != 0L && now - lastEntityInteraction <= ENTITY_INTERACTION_GRACE_NANOS)) {
            evidence = 0;
            return;
        }

        double horizontalSpeed = Math.hypot(player.x - player.lastX, player.z - player.lastZ);
        boolean atFootLevel = place.position.getY() == (int) Math.floor(player.y);
        boolean shallowLook = player.pitch >= -10.0f && player.pitch < 48.0f;
        boolean fastMovement = horizontalSpeed > 0.12;

        if (atFootLevel && shallowLook && fastMovement) {
            evidence = lastSuspiciousPlace != 0L && now - lastSuspiciousPlace <= EVIDENCE_WINDOW_NANOS
                    ? evidence + 1 : 1;
            lastSuspiciousPlace = now;
            if (evidence >= evidenceToFlag && flag(V.write(verbose()).f64(horizontalSpeed).f32(player.pitch)
                    .uint(place.position.getY()).uint(evidence)) && shouldModifyPackets() && shouldCancel()) {
                place.resync();
                evidence = 0;
            }
        } else {
            evidence = Math.max(0, evidence - 2);
        }
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        evidenceToFlag = Math.max(2, config.getIntElse(getConfigName() + ".evidence-to-flag", 6));
    }
}
