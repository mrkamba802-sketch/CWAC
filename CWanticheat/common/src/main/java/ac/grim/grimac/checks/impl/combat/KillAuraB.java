package ac.grim.grimac.checks.impl.combat;
import ac.grim.grimac.api.config.ConfigManager;import ac.grim.grimac.checks.CheckData;import ac.grim.grimac.checks.impl.aim.AimHitCheck;
import ac.grim.grimac.checks.type.PacketReceiveListener;import ac.grim.grimac.player.GrimPlayer;import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.packetentity.PacketEntity;import com.github.retrooper.packetevents.event.PacketReceiveEvent;import java.util.ArrayDeque;import java.util.Deque;
@CheckData(name="KillAuraB",stableKey="ronexgrim.combat.killaura_attack_direction",description="Repeated attacks issued while the server-visible look direction is well outside the target hitbox")
public class KillAuraB extends AimHitCheck implements PacketReceiveListener{
 private final Deque<Boolean> suspiciousHits=new ArrayDeque<>();private double minimumExcess,maximumDistance;private int requiredHits,hitWindow;
 public KillAuraB(GrimPlayer p){super(p);}
 @Override public void onPacketReceive(PacketReceiveEvent e){
  int id=RonexKillAuraUtil.attackedEntityId(e);if(id<0||RonexKillAuraUtil.livingTarget(player,id)==null)return;
  PacketEntity t=player.compensatedEntities.entityMap.get(id);SimpleCollisionBox b=t==null?null:t.getPossibleCollisionBoxes();double error=getAimError(e);if(b==null||!Double.isFinite(error))return;
  double cx=(b.minX+b.maxX)*0.5,cy=(b.minY+b.maxY)*0.5,cz=(b.minZ+b.maxZ)*0.5,dx=cx-player.x,dy=cy-(player.y+player.getEyeHeight()),dz=cz-player.z;
  double h=Math.hypot(dx,dz),d=Math.sqrt(h*h+dy*dy);if(d<0.5||d>maximumDistance)return;
  double hw=Math.max(b.maxX-b.minX,b.maxZ-b.minZ)*0.5,hh=(b.maxY-b.minY)*0.25;
  double ha=Math.toDegrees(Math.atan2(hw,Math.max(0.1,h))),va=Math.toDegrees(Math.atan2(hh,Math.max(0.1,d)));
  double excess=error-(Math.hypot(ha,va)+4.0);boolean suspicious=excess>=minimumExcess;suspiciousHits.addLast(suspicious);while(suspiciousHits.size()>hitWindow)suspiciousHits.removeFirst();
  int count=0;for(boolean hit:suspiciousHits)if(hit)count++;
  if(suspiciousHits.size()==hitWindow&&count>=requiredHits){flag("off-hitbox-angle-hits="+count+"/"+hitWindow+" excess-degrees="+formatOffset(excess));suspiciousHits.clear();}else if(!suspicious)reward();
 }
 @Override public void onReload(ConfigManager c){minimumExcess=Math.max(4.0,c.getDoubleElse(getConfigName()+".minimum-excess-degrees",10.0));maximumDistance=Math.max(3.0,c.getDoubleElse(getConfigName()+".maximum-distance",5.0));requiredHits=Math.max(2,(int)c.getDoubleElse(getConfigName()+".required-hits",4));hitWindow=Math.max(requiredHits,(int)c.getDoubleElse(getConfigName()+".hit-window",8));}
}
