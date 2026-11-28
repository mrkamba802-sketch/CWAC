package ac.grim.grimac.checks.impl.movement;
import ac.grim.grimac.checks.CheckData;import ac.grim.grimac.checks.type.PostPredictionListener;
import ac.grim.grimac.player.GrimPlayer;import ac.grim.grimac.utils.anticheat.update.PredictionComplete;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
@CheckData(name="NoSlowB",stableKey = "ronexgrim.movement.noslow_off_hand", experimental = falsedescription="Moved faster than predicted while using an off-hand item",setback=5)
public class NoSlowB extends NoSlowHandCheck implements PostPredictionListener{
 public NoSlowB(GrimPlayer p){super(p,InteractionHand.OFF_HAND);}
 @Override public void onPredictionComplete(PredictionComplete p){checkItemUseOffset(p.isChecked());}
}
