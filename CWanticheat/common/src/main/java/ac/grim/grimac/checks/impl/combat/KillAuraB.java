package ac.grim.grimac.checks.impl.combat;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.jetbrains.annotations.NotNull;

@CheckData(name = "KillAuraB", stableKey = "grim.combat.killaura_back_side", description = "Repeatedly attacked living targets while looking away from them", setback = 8, decay = 0.02)
public class KillAuraB extends Check implements PacketReceiveListener {
    private static final Verbose V = Verbose.of("angle={f64}, threshold={f64}, badHits={uint}, samples={uint}");
    private double angleThreshold = 100.0;
    private int badHits;
    private int samples;

    public KillAuraB(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        int entityId;
        if (event.getPacketType() == PacketType.Play.Client.ATTACK) {
            entityId = new WrapperPlayClientAttack(event).getEntityId();
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
            if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
            entityId = packet.getEntityId();
        } else {
            return;
        }

        PacketEntity target = player.compensatedEntities.getEntity(entityId);
        if (!KillAuraUtil.canCheck(player, target)) return;

        double angle = KillAuraUtil.angleToTarget(player, target);
        samples++;
        if (angle >= angleThreshold) badHits++;

        // Multiple rear/side attacks are required; one can be caused by latency
        // or knockback, so isolated off-angle hits are not treated as proof.
        if (samples >= 4) {
            if (badHits >= 3) {
                flag(V.write(verbose()).f64(angle).f64(angleThreshold).uint(badHits).uint(samples));
            } else {
                reward();
            }
            badHits = 0;
            samples = 0;
        }
    }

    @Override
    public void onReload(@NotNull ConfigManager config) {
        angleThreshold = config.getDoubleElse(getConfigName() + ".angle-threshold", 100.0);
    }
}
