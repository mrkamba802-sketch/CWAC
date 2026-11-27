package ac.grim.grimac.checks.impl.inventory;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.InventoryCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.BlockHitData;
import ac.grim.grimac.utils.nmsutil.WorldRayTrace;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.defaulttags.BlockTags;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "GhostHandA", stableKey = "grim.inventory.ghost_hand_a", description = "Tried to interact with a container through an obstructing block", setback = 0, decay = 0.02)
public class GhostHandA extends InventoryCheck {
    private static final Verbose V = Verbose.of("clicked={mcpos}, visible={bool}, evidence={uint}");
    private int evidence;
    private int evidenceToCancel = 2;

    public GhostHandA(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        super.onPacketReceive(event);
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT
                || player.packetStateData.lastPacketWasTeleport || !player.cameraEntity.isSelf()
                || player.inVehicle() || !player.isTickingReliablyFor(3)) return;

        WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement(event);
        if (packet.getFace() == BlockFace.OTHER) return;

        var position = packet.getBlockPosition();
        StateType clickedType = player.compensatedWorld.getBlock(position).getType();
        if (!isContainer(clickedType)) {
            evidence = Math.max(0, evidence - 1);
            return;
        }

        BlockHitData visibleBlock = WorldRayTrace.getNearestBlockHitResult(player, null, true, false, false);
        // A missing ray result is inconclusive (e.g. a transient world/latency mismatch).
        if (visibleBlock == null) return;

        boolean visible = position.equals(visibleBlock.position());
        if (!visible) {
            evidence++;
            if (evidence >= evidenceToCancel && flag(V.write(verbose()).mcPos(position.getX(), position.getY(), position.getZ())
                    .bool(false).uint(evidence))) {
                if (shouldModifyPackets()) {
                    event.setCancelled(true);
                    player.onPacketCancel();
                }
                if (player.hasInventoryOpen && !isNoSetbackPermission()) closeInventory();
                evidence = 0;
            }
        } else {
            evidence = Math.max(0, evidence - 1);
            reward();
        }
    }

    private static boolean isContainer(StateType type) {
        return BlockTags.SHULKER_BOXES.contains(type)
                || type == StateTypes.CHEST || type == StateTypes.TRAPPED_CHEST
                || type == StateTypes.BARREL || type == StateTypes.ENDER_CHEST
                || type == StateTypes.FURNACE || type == StateTypes.BLAST_FURNACE
                || type == StateTypes.SMOKER || type == StateTypes.HOPPER
                || type == StateTypes.DISPENSER || type == StateTypes.DROPPER;
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        super.onReload(config);
        evidenceToCancel = Math.max(1, config.getIntElse(getConfigName() + ".evidence-to-cancel", 2));
    }
}
