package ac.grim.grimac.checks.impl.scaffolding;

import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.BlockPlaceCheck;
import ac.grim.grimac.checks.type.BlockPlaceListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockPlace;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

@CheckData(name = "ScaffoldA", stableKey = "grim.scaffolding.scaffold_a", description = "Repeatedly placed blocks at foot level while moving with a forward-facing view", setback = 6, decay = 0.02)
public class ScaffoldA extends BlockPlaceCheck implements BlockPlaceListener {
    private static final Verbose V = Verbose.of("horizontalSpeed={f64}, pitch={f32}, placeY={uint}, evidence={uint}");
    private int evidence;
    private long lastSuspiciousPlace;

    public ScaffoldA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(BlockPlace place) {
        if (place.material == StateTypes.SCAFFOLDING || !player.cameraEntity.isSelf()
                || player.inVehicle() || player.packetStateData.lastPacketWasTeleport
                || !player.isTickingReliablyFor(3)) {
            evidence = 0;
            return;
        }

        double horizontalSpeed = Math.hypot(player.x - player.lastX, player.z - player.lastZ);
        boolean atFootLevel = place.position.getY() <= (int) Math.floor(player.y + 0.05);
        boolean shallowLook = player.pitch < 48.0f;
        boolean fastMovement = horizontalSpeed > 0.12;

        if (atFootLevel && shallowLook && fastMovement) {
            long now = System.nanoTime();
            evidence = now - lastSuspiciousPlace <= 500_000_000L ? evidence + 1 : 1;
            lastSuspiciousPlace = now;
            if (evidence >= 5 && flag(V.write(verbose()).f64(horizontalSpeed).f32(player.pitch)
                    .uint(place.position.getY()).uint(evidence)) && shouldModifyPackets() && shouldCancel()) {
                place.resync();
                evidence = 0;
            }
        } else {
            evidence = Math.max(0, evidence - 2);
        }
    }
}
