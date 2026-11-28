package ac.grim.grimac.checks.impl.movement;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import java.util.ArrayDeque;import java.util.Deque;
public abstract class NoSlowHandCheck extends Check{
 private final InteractionHand hand;private final Deque<Boolean> suspiciousSamples=new ArrayDeque<>();
 private long lastEvidenceAt,evidenceTimeoutNanos;private int sampleWindow,requiredSamples;private String lastItemKey;
 private double threshold,bestOffset=Double.POSITIVE_INFINITY;private boolean hasPrediction;
 protected NoSlowHandCheck(GrimPlayer p,InteractionHand h){super(p);hand=h;}
 public final void handlePredictionAnalysis(double o){if(Double.isFinite(o)&&o>=0){bestOffset=Math.min(bestOffset,o);hasPrediction=true;}}
 protected final void checkItemUseOffset(boolean checked){
  long now=System.nanoTime();double offset=bestOffset;boolean found=hasPrediction;bestOffset=Double.POSITIVE_INFINITY;hasPrediction=false;
  ItemStack item=player.inventory.getItemInHand(hand);boolean held=item!=null&&!item.isEmpty();boolean exempt=NoSlowCheckUtil.isExempt(player);
  boolean active=player.packetStateData.isSlowedByUsingItem()&&player.packetStateData.itemInUseHand==hand&&held&&!exempt;
  if(!active){
   boolean gap=!exempt&&held&&lastItemKey!=null&&lastItemKey.equals(item.getType().getName().getKey())
    &&lastEvidenceAt!=0&&now-lastEvidenceAt<=Math.min(evidenceTimeoutNanos,250_000_000L);
   if(gap)return;suspiciousSamples.clear();lastEvidenceAt=0;lastItemKey=null;return;
  }
  if(lastEvidenceAt!=0&&now-lastEvidenceAt>evidenceTimeoutNanos){suspiciousSamples.clear();lastItemKey=null;}
  if(!checked||!found||Math.hypot(player.actualMovement.getX(),player.actualMovement.getZ())<0.02)return;
  lastEvidenceAt=now;lastItemKey=item.getType().getName().getKey();suspiciousSamples.addLast(offset>threshold);
  while(suspiciousSamples.size()>sampleWindow)suspiciousSamples.removeFirst();
  int suspicious=0;for(boolean v:suspiciousSamples)if(v)suspicious++;
  if(suspiciousSamples.size()==sampleWindow&&suspicious>=requiredSamples){
   flagWithSetback("hand="+hand.name().toLowerCase()+" raw-offset="+formatOffset(offset)+" suspicious-samples="+suspicious+"/"+sampleWindow);
   suspiciousSamples.clear();lastEvidenceAt=0;lastItemKey=null;
  }else if(offset<=threshold)reward();
 }
 @Override public void onReload(ConfigManager c){
  String n=getConfigName();threshold=Math.max(0.0001,Math.min(0.0030,c.getDoubleElse(n+".threshold",0.0025)));
  sampleWindow=Math.max(8,(int)c.getDoubleElse(n+".sample-window",8));
  requiredSamples=Math.min(sampleWindow,Math.max(5,(int)c.getDoubleElse(n+".required-samples",6)));
  evidenceTimeoutNanos=Math.max(250L,(long)c.getDoubleElse(n+".evidence-timeout-ms",900))*1_000_000L;
  if(suspiciousSamples!=null)suspiciousSamples.clear();lastEvidenceAt=0;lastItemKey=null;
 }
}
