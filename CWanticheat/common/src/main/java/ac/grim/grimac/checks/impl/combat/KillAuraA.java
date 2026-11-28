package ac.grim.grimac.checks.impl.combat;
import ac.grim.grimac.api.config.ConfigManager;import ac.grim.grimac.checks.CheckData;import ac.grim.grimac.checks.impl.aim.AimHitCheck;
import ac.grim.grimac.checks.type.PacketReceiveListener;import ac.grim.grimac.player.GrimPlayer;import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;import java.util.ArrayDeque;import java.util.Deque;
@CheckData(name="KillAuraA",stableKey="ronexgrim.combat.killaura_multi_target",description="Repeated attacks on different living targets without an intervening rotation update")
public class KillAuraA extends AimHitCheck implements PacketReceiveListener{
 private final Deque<Long> bursts=new ArrayDeque<>();private long lastAttackAt,maxGap,window;private int lastTarget=-1,rotationSequence,lastAttackSequence,requiredBursts;
 public KillAuraA(GrimPlayer p){super(p);}
 @Override public void process(RotationUpdate u){super.process(u);rotationSequence++;}
 @Override public void onPacketReceive(PacketReceiveEvent e){
  int id=RonexKillAuraUtil.attackedEntityId(e);if(id<0||RonexKillAuraUtil.livingTarget(player,id)==null)return;
  long now=System.currentTimeMillis();while(!bursts.isEmpty()&&now-bursts.peekFirst()>window)bursts.removeFirst();
  if(lastAttackAt!=0&&id!=lastTarget&&now-lastAttackAt<=maxGap&&rotationSequence==lastAttackSequence){
   bursts.addLast(now);if(bursts.size()>=requiredBursts){flag("multi-target-bursts="+bursts.size()+" without-rotation gap-ms="+(now-lastAttackAt));bursts.clear();}
  }
  lastAttackAt=now;lastTarget=id;lastAttackSequence=rotationSequence;
 }
 @Override public void onReload(ConfigManager c){maxGap=Math.max(25L,(long)c.getDoubleElse(getConfigName()+".maximum-attack-gap-ms",80));window=Math.max(1000L,(long)c.getDoubleElse(getConfigName()+".burst-window-ms",5000));requiredBursts=Math.max(2,(int)c.getDoubleElse(getConfigName()+".required-bursts",3));}
}
