package ac.grim.grimac.manager.tick.impl;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.impl.movement.AirStuckA;
import ac.grim.grimac.manager.tick.Tickable;
import ac.grim.grimac.player.GrimPlayer;

/** Runs the AirStuckA watchdog even when a client stops sending movement packets. */
public class AirStuckWatchdog implements Tickable {
    @Override
    public void tick() {
        for (GrimPlayer player : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            player.checkManager.get(AirStuckA.class).onServerTick();
        }
    }
}
