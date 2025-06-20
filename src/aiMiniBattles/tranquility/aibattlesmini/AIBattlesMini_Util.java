package aiMiniBattles.tranquility.aibattlesmini;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.AICoreOfficerPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.hullmods.Automated;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.CharacterStats;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class AIBattlesMini_Util {
    public static final String CONFIG_PATH = "data/config/aibattlesminiConfig/";
    private static final float DEPLOYMENT_OFFSET = 1000f;
    private static final float MAX_SPEED_UP_MULT = 100f;

    // Recommended to avoid potential AI bugs like this:
    // https://fractalsoftworks.com/forum/index.php?topic=32491.msg474977#msg474977
    private static final float CAP_TO_FPS = 60f;

    private static class HullSizeComparator implements Comparator<FleetMemberAPI> {
        @Override
        public int compare(FleetMemberAPI o1, FleetMemberAPI o2) {
            int c = o1.getVariant().getHullSize().compareTo(o2.getVariant().getHullSize());
            if (c == 0)
                c = Float.compare(o1.getUnmodifiedDeploymentPointsCost(), o2.getUnmodifiedDeploymentPointsCost());
            if (c == 0) c = Integer.compare(o1.getFleetPointCost(), o2.getFleetPointCost());
            return c;
        }
    }

    public static BaseEveryFrameCombatPlugin getSpeedUpPlugin() {
        return new BaseEveryFrameCombatPlugin() {
            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                if (Global.getCombatEngine().isPaused()) return;

                // Provided by Dark.Revenant
                int roundedFrameTimeMsec = (int) Math.ceil(1000f * Global.getCombatEngine().getElapsedInLastFrame() + 1);
                float scaledFPS = 1000f / roundedFrameTimeMsec;
                float unscaledFPS = Global.getCombatEngine().getTimeMult().getModifiedValue() * scaledFPS;
                float cappedTimeMult = Math.max(1f, unscaledFPS / CAP_TO_FPS);
                float newTimeMult = Math.min(cappedTimeMult, MAX_SPEED_UP_MULT);
                Global.getCombatEngine().getTimeMult().modifyMult("aibattlesmini_speedUp", newTimeMult);
            }
        };
    }

    // Force-deploy on battle start
    public static BaseEveryFrameCombatPlugin getDeploymentPlugin(JSONObject playerSide, JSONObject enemySide) {
        final boolean playerSortShips = playerSide.optBoolean("autoSortShips", true);
        final boolean enemySortShips = enemySide.optBoolean("autoSortShips", true);
        final float playerHeightMult = (float) playerSide.optDouble("mapHeightMult", 0.3333);
        final float enemyHeightMult = (float) enemySide.optDouble("mapHeightMult", 0.3333);
        final int playerShipsPerRow = Math.max(1, playerSide.optInt("shipsPerRow", 5));
        final int enemyShipsPerRow = Math.max(1, enemySide.optInt("shipsPerRow", 5));

        return new BaseEveryFrameCombatPlugin() {
            @Override
            public void init(CombatEngineAPI engine) {
                // Add loads of deployment points to prevent the AI from holding ships
                engine.setMaxFleetPoints(FleetSide.ENEMY, 9999);
                engine.setMaxFleetPoints(FleetSide.PLAYER, 9999);

                forceSpawnFleet(Global.getCombatEngine().getFleetManager(FleetSide.PLAYER), playerShipsPerRow, playerSortShips, 90f, -engine.getMapHeight() * playerHeightMult / 2f);
                forceSpawnFleet(Global.getCombatEngine().getFleetManager(FleetSide.ENEMY), enemyShipsPerRow, enemySortShips, 270f, engine.getMapHeight() * enemyHeightMult / 2f);
            }
        };
    }

    // Spawns fleet in a formation similar to (but not exactly!) the one used by vanilla deployment
    private static void forceSpawnFleet(CombatFleetManagerAPI cfm, int shipsPerRow, boolean sortShips, float facing, float y) {
        List<FleetMemberAPI> reserves = cfm.getReservesCopy();
        if (sortShips) Collections.reverse(reserves); // Reverse list so the last ships are spawned in the front

        Comparator<FleetMemberAPI> reverseCmp = Collections.reverseOrder(new HullSizeComparator());
        for (int i = 0; i < reserves.size(); ) {
            int shipsToSpawn = Math.min(shipsPerRow, reserves.size() - i);
            List<FleetMemberAPI> deployShips = reserves.subList(i, i + shipsToSpawn);

            // Sorting the row to prioritize bigger ships in middle
            if (sortShips) deployShips.sort(reverseCmp);

            float diff = DEPLOYMENT_OFFSET;
            float initialX = shipsToSpawn % 2 == 1 ? 0f : -diff * 0.5f; // Offset the 'center' if non-odd ship row
            float x = initialX;
            for (FleetMemberAPI member : deployShips) {
                cfm.spawnFleetMember(member, new Vector2f(x, y), facing, 1.5f);

                // Center, right, left, right, left,... deployment order
                if (x <= initialX) {
                    x = diff + initialX;
                } else {
                    x = -diff + initialX;
                    diff += DEPLOYMENT_OFFSET;
                }
            }

            i += shipsToSpawn;
            y = y < 0 ? y - DEPLOYMENT_OFFSET * 0.6666f : y + DEPLOYMENT_OFFSET * 0.6666f;
        }
    }

    @SuppressWarnings("unchecked")
    public static CampaignFleetAPI generateAPIFleet(JSONObject fleetConfig, MissionDefinitionAPI api, FleetSide side) throws JSONException, IOException {
        JSONArray shipList = fleetConfig.getJSONArray("shipList");
        String fleetName = fleetConfig.getString("fleetName");

        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, FleetTypes.TASK_FORCE, true);
        fleet.setName(fleetName);

        AICoreOfficerPlugin aiPlugin = new AICoreOfficerPluginImpl();
        boolean automatedPenalty = fleetConfig.optBoolean("automatedPenalty", false);

        for (int i = 0; i < shipList.length(); i++) {
            JSONObject shipConfig = shipList.getJSONObject(i);
            FleetMemberAPI member = fleet.getFleetData().addFleetMember(shipConfig.getString("variant"));
            String shipName = shipConfig.optString("shipName", null);
            if (shipName != null) member.setShipName(shipName);

            // Only inflict CR penalty if it would be affected by the Automated Ships skill
            if (automatedPenalty && Misc.isAutomated(member) && !Automated.isAutomatedNoPenalty(member))
                member.getVariant().addPermaMod("AIBattlesMini_Automated");

            JSONObject skills = shipConfig.optJSONObject("officerSkills");
            if (skills == null) continue; // Skip officer creation if no officer skills

            PersonAPI officer;
            String aiCoreId = !shipConfig.isNull("officerAICoreId") ? shipConfig.optString("officerAICoreId", null) : null;
            if (aiCoreId != null) {
                officer = aiPlugin.createPerson(aiCoreId, Factions.PLAYER, null);
                officer.setStats(new CharacterStats()); // Removes all AI core skills
            } else {
                officer = Global.getSettings().createPerson();
                officer.getName().setFirst("Human");
                officer.getName().setLast("Officer");
                officer.setFaction(Factions.PLAYER);
                officer.setPortraitSprite("graphics/portraits/" + shipConfig.optString("officerPortrait", "portrait_generic.png"));
                officer.setPersonality(shipConfig.optString("officerPersonality", Personalities.STEADY));
            }

            int level = 0;
            officer.getStats().setSkipRefresh(true);
            for (Iterator<String> iter = skills.keys(); iter.hasNext(); ) {
                String skillId = iter.next();
                int skillLevel = skills.getInt(skillId);
                if (skillLevel > 0) {
                    officer.getStats().setSkillLevel(skillId, skillLevel);
                    level++;
                }
            }
            officer.getStats().setLevel(Math.max(1, level));
            officer.getStats().setSkipRefresh(false);

            fleet.getFleetData().addOfficer(officer);
            member.setCaptain(officer);
        }

        // Creating a commander to give fleetwide skills for this fleet; will not be piloting any of the ships
        PersonAPI commander = Global.getFactory().createPerson();
        JSONObject skills = fleetConfig.getJSONObject("commanderSkills");
        for (Iterator<String> iter = skills.keys(); iter.hasNext(); ) {
            String skillId = iter.next();
            commander.getStats().setSkillLevel(skillId, skills.getInt(skillId));
        }
        fleet.setCommander(commander);

        boolean useAdmiralAI = fleetConfig.optBoolean("useAdmiralAI", true);
        // From BattleCreationPluginImpl.java
        int baseCommandPoints = (int) Global.getSettings().getFloat("startingCommandPoints");
        int modifiedCommandPoints = (int) fleet.getCommanderStats().getCommandPoints().getModifiedValue();
        api.initFleet(side, null, FleetGoal.ATTACK, useAdmiralAI, modifiedCommandPoints - baseCommandPoints);

        // If enabled, sort by hull size to partially replicate vanilla deployment.
        // Also ensures ship order in "shipList" does not largely affect the final deployment.
        // Comparator is reversed so larger ships appear first in mission.
        List<FleetMemberAPI> memberList = fleet.getFleetData().getMembersListCopy();
        if (fleetConfig.optBoolean("autoSortShips", true))
            memberList.sort(Collections.reverseOrder(new HullSizeComparator()));

        float totalDP = 0;
        for (FleetMemberAPI member : memberList) {
            member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
            api.addFleetMember(side, member);

            totalDP += member.getDeploymentPointsCost();
        }
        api.setFleetTagline(side, String.format("%s (%d ships; %d DP)", fleet.getName(), fleet.getNumShips(), (int) totalDP));

        return fleet;
    }

    public static void generateMapSettings(MissionDefinitionAPI api) throws JSONException, IOException {
        JSONObject mapConfig = Global.getSettings().loadJSON(CONFIG_PATH + "mapSettings.json");

        String layout = mapConfig.getString("mapLayout");
        JSONObject layoutConfig = Global.getSettings().loadJSON(CONFIG_PATH + "mapLayouts.json").getJSONObject(layout);
        api.initMap(layoutConfig.getInt("minX"), layoutConfig.getInt("maxX"), layoutConfig.getInt("minY"), layoutConfig.getInt("maxY"));
        api.getContext().setStandoffRange(layoutConfig.getInt("standoffRange"));

        JSONArray objectives = layoutConfig.optJSONArray("objectives");
        if (objectives != null) for (int i = 0; i < objectives.length(); i++) {
            JSONArray objective = objectives.getJSONArray(i);
            api.addObjective(objective.getInt(1), objective.getInt(2), objective.getString(0));
        }

        api.getContext().aiRetreatAllowed = mapConfig.getBoolean("aiRetreatAllowed");
        api.getContext().enemyDeployAll = mapConfig.getBoolean("enemyDeployAll");
        api.getContext().fightToTheLast = mapConfig.getBoolean("fightToTheLast");
    }
}