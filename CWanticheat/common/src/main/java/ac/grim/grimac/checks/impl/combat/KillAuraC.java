package ac.grim.grimac.checks.impl.combat;
import ac.grim.grimac.api.config.ConfigManager;import ac.grim.grimac.checks.Check;import ac.grim.grimac.checks.CheckData;import ac.grim.grimac.checks.type.PacketReceiveListener;
import ac.grim.grimac.player.GrimPlayer;import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;import ac.grim.grimac.utils.data.packetentity.PacketEntity;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;import com.github.retrooper.packetevents.util.Vector3i;import java.util.ArrayDeque;import java.util.Deque;
@CheckData(name="KillAuraC",stableKey="ronexgrim.combat.killaura_occluded_attack",description="Repeated attacks on living targets with both sampled aim points blocked by solid blocks")
public class KillAuraC extends Check implements PacketReceiveListener{
 private final Deque<Boolean> hits=new ArrayDeque<>();private double maximumDistance;private int requiredHits,hitWindow;
 public KillAuraC(GrimPlayer p){super(p);}
 @Override public void onPacketReceive(PacketReceiveEvent e){
  int id=RonexKillAuraUtil.attackedEntityId(e);PacketEntity t=RonexKillAuraUtil.livingTarget(player,id);
  if(id<0||t==null||player.getSetbackTeleportUtil().insideUnloadedChunk())return;
  SimpleCollisionBox b=t.getPossibleCollisionBoxes();if(b==null)return;
  double eye=player.y+player.getEyeHeight(),x=(b.minX+b.maxX)*0.5,cy=(b.minY+b.maxY)*0.5,uy=b.minY+(b.maxY-b.minY)*0.72,z=(b.minZ+b.maxZ)*0.5;
  double dx=x-player.x,dy=cy-eye,dz=z-player.z,d=Math.sqrt(dx*dx+dy*dy+dz*dz);if(d<0.5||d>maximumDistance)return;
  boolean blocked=occluded(x,cy,z)&&occluded(x,uy,z);hits.addLast(blocked);while(hits.size()>hitWindow)hits.removeFirst();
  int count=0;for(boolean v:hits)if(v)count++;
  if(hits.size()==hitWindow&&count>=requiredHits){if(flag("occluded-target-attacks="+count+"/"+hitWindow)&&shouldModifyPackets()){e.setCancelled(true);player.onPacketCancel();}hits.clear();}else if(!blocked)reward();
 }
 private boolean occluded(double tx,double ty,double tz){
  double ex=player.x,ey=player.y+player.getEyeHeight(),ez=player.z,dx=tx-ex,dy=ty-ey,dz=tz-ez,d=Math.sqrt(dx*dx+dy*dy+dz*dz);
  if(d<0.1||d>maximumDistance)return false;int steps=Math.max(2,(int)Math.ceil(d*12.0));
  for(int i=1;i<steps-2;i++){double t=(double)i/steps;int x=(int)Math.floor(ex+dx*t),y=(int)Math.floor(ey+dy*t),z=(int)Math.floor(ez+dz*t);
   if(!player.compensatedWorld.isChunkLoaded(x>>4,z>>4))return false;
   if(player.compensatedWorld.getBlock(new Vector3i(x,y,z)).getType().isBlocking())return true;
  }return false;
 }
 @Override public void onReload(ConfigManager c){maximumDistance=Math.max(3.0,c.getDoubleElse(getConfigName()+".maximum-distance",5.0));requiredHits=Math.max(2,(int)c.getDoubleElse(getConfigName()+".required-hits",3));hitWindow=Math.max(requiredHits,(int)c.getDoubleElse(getConfigName()+".hit-window",6));}
}
