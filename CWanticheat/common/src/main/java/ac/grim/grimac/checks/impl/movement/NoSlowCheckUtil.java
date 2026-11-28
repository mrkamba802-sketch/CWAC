package ac.grim.grimac.checks.impl.movement;
import ac.grim.grimac.player.GrimPlayer;
final class NoSlowCheckUtil {
 private NoSlowCheckUtil(){}
 static boolean isExempt(GrimPlayer p){
  if(p.inVehicle()||p.isFlying||p.isGliding||p.isSwimming||p.isClimbing||p.wasTouchingWater||p.isRiptidePose||p.packetStateData.lastPacketWasTeleport)return true;
  if(p.predictedVelocity!=null&&(p.predictedVelocity.isKnockback()||p.predictedVelocity.isExplosion()||p.predictedVelocity.isZeroPointZeroThree()))return true;
  return p.uncertaintyHandler.isOrWasNearGlitchyBlock||p.uncertaintyHandler.influencedByBouncyBlock()||p.uncertaintyHandler.lastHardCollidingLerpingEntity.hasOccurredSince(3);
 }
}
