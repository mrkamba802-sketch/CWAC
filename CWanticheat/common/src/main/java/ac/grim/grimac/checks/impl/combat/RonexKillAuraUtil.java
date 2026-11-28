package ac.grim.grimac.checks.impl.combat;
import ac.grim.grimac.player.GrimPlayer;import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
final class RonexKillAuraUtil{
 private RonexKillAuraUtil(){}
 static int attackedEntityId(PacketReceiveEvent e){
  if(e.getPacketType()==PacketType.Play.Client.ATTACK)return new WrapperPlayClientAttack(e).getEntityId();
  if(e.getPacketType()==PacketType.Play.Client.INTERACT_ENTITY){WrapperPlayClientInteractEntity p=new WrapperPlayClientInteractEntity(e);return p.getAction()==WrapperPlayClientInteractEntity.InteractAction.ATTACK?p.getEntityId():-1;}return -1;
 }
 static PacketEntity livingTarget(GrimPlayer p,int id){if(id<0||p.inVehicle())return null;PacketEntity t=p.compensatedEntities.entityMap.get(id);return t!=null&&t.isLivingEntity&&!t.isDead?t:null;}
}
