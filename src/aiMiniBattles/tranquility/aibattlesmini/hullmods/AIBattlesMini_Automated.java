package aiMiniBattles.tranquility.aibattlesmini.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;

import static com.fs.starfarer.api.impl.hullmods.Automated.MAX_CR_PENALTY;
import static com.fs.starfarer.api.impl.hullmods.Automated.isAutomatedNoPenalty;

public class AIBattlesMini_Automated extends BaseHullMod {
    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (!isAutomatedNoPenalty(stats))
            stats.getMaxCombatReadiness().modifyFlat(id, -MAX_CR_PENALTY, "Automated ship penalty");
    }
}