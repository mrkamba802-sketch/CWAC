package ac.grim.grimac.checks.impl.movement;
import ac.grim.grimac.api.config.ConfigManager;import ac.grim.grimac.checks.Check;import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PostPredictionListener;import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.PredictionComplete;import com.github.retrooper.packetevents.protocol.item.ItemStack;
import java.util.ArrayDeque;import java.util.Deque;
@CheckData(name="NoSlowC",stableKey="ronexgrim.movement.noslow_strict",description="Detects sustained partial or complete item-use slowdown bypasses",setback=5)
public class NoSlowC extends Check implements PostPredictionListener{
 private Deque<Boolean> samples;private double threshold,bestOffset=Double.POSITIVE_INFINITY;private int sampleWindow,requiredSamples;
 private boolean hasPrediction;private long lastEvidenceNanos;private String lastItemKey="";
 public NoSlowC(GrimPlayer p){super(p);samples=new ArrayDeque<>();}
 public void handlePredictionAnalysis(double o){if(Double.isFinite(o)&&o>=0){bestOffset=Math.min(bestOffset,o);hasPrediction=true;}}
 private void clearEvidence(){if(samples!=null)samples.clear();lastEvidenceNanos=0;lastItemKey="";}
 @Override public void onPredictionComplete(PredictionComplete p){
  double offset=bestOffset;boolean found=hasPrediction;bestOffset=Double.POSITIVE_INFINITY;hasPrediction=false;
  if(!p.isChecked()||!player.packetStateData.isSlowedByUsingItem()||NoSlowCheckUtil.isExempt(player)||player.packetStateData.itemInUseHand==null){clearEvidence();return;}
  ItemStack item=player.inventory.getItemInHand(player.packetStateData.itemInUseHand);
  if(item==null||item.isEmpty()){clearEvidence();return;}
  String key=item.getType().getName().getKey();long now=System.nanoTime();
  if(!lastItemKey.equals(key)||(lastEvidenceNanos!=0&&now-lastEvidenceNanos>1_000_000_000L))samples.clear();
  lastItemKey=key;if(!found||Math.hypot(player.actualMovement.getX(),player.actualMovement.getZ())<0.02)return;
  lastEvidenceNanos=now;samples.addLast(offset>threshold);while(samples.size()>sampleWindow)samples.removeFirst();
  int count=0;for(boolean v:samples)if(v)count++;
  if(samples.size()>=sampleWindow&&count>=requiredSamples){
   flagWithSetback("item="+key+" raw-offset="+formatOffset(offset)+" suspicious-samples="+count+"/"+samples.size());clearEvidence();
  }else if(offset<=threshold)reward();
 }
 @Override public void onReload(ConfigManager c){
  threshold=Math.max(0.00005,Math.min(0.0015,c.getDoubleElse(getConfigName()+".threshold",0.00012)));
  sampleWindow=Math.max(6,Math.min(16,(int)c.getDoubleElse(getConfigName()+".sample-window",8)));
  requiredSamples=Math.max(3,Math.min(sampleWindow,(int)c.getDoubleElse(getConfigName()+".required-samples",6)));clearEvidence();
 }
}
