package ac.grim.grimac.checks.impl.aim;
import ac.grim.grimac.checks.Check;import ac.grim.grimac.checks.type.RotationListener;import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAttack;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
public abstract class AimHitCheck extends Check implements RotationListener{
 private double yaw,pitch;protected AimHitCheck(GrimPlayer p){super(p);yaw=p.yaw;pitch=p.pitch;}
 @Override public void process(RotationUpdate u){yaw=u.newYaw();pitch=u.newPitch();}
 protected final double getAimError(PacketReceiveEvent e){
  final int id;if(e.getPacketType()==PacketType.Play.Client.ATTACK)id=new WrapperPlayClientAttack(e).getEntityId();
  else if(e.getPacketType()==PacketType.Play.Client.INTERACT_ENTITY){WrapperPlayClientInteractEntity p=new WrapperPlayClientInteractEntity(e);if(p.getAction()!=WrapperPlayClientInteractEntity.InteractAction.ATTACK)return Double.NaN;id=p.getEntityId();}
  else return Double.NaN;if(player.inVehicle())return Double.NaN;
  PacketEntity t=player.compensatedEntities.entityMap.get(id);if(t==null||!t.isLivingEntity||t.isDead)return Double.NaN;
  SimpleCollisionBox b=t.getPossibleCollisionBoxes();if(b==null)return Double.NaN;double h=b.maxY-b.minY;if(!Double.isFinite(h)||h<=0.05)return Double.NaN;
  double eye=player.y+player.getEyeHeight();double c=angle((b.minX+b.maxX)*0.5,b.minY+h*0.5,(b.minZ+b.maxZ)*0.5,eye,yaw,pitch);
  double u=angle((b.minX+b.maxX)*0.5,b.minY+h*0.72,(b.minZ+b.maxZ)*0.5,eye,yaw,pitch);return Math.min(c,u);
 }
 private double angle(double x,double y,double z,double eye,double a,double b){
  double dx=x-player.x,dy=y-eye,dz=z-player.z,hor=Math.hypot(dx,dz);if(hor<1E-4)return Double.NaN;
  double ty=Math.toDegrees(Math.atan2(-dx,dz)),tp=-Math.toDegrees(Math.atan2(dy,hor));return Math.hypot(wrap(ty-a),tp-b);
 }
 private static double wrap(double a){a%=360;if(a>=180)a-=360;if(a< -180)a+=360;return a;}
}
