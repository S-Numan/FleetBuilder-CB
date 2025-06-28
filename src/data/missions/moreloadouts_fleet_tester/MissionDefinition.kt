package data.missions.moreloadouts_fleet_tester

import aiMiniBattles.tranquility.aibattlesmini.AIBattlesMini_FreeCamPlugin
import aiMiniBattles.tranquility.aibattlesmini.AIBattlesMini_Util
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.fleet.FleetGoal
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.hullmods.Automated
import com.fs.starfarer.api.mission.FleetSide
import com.fs.starfarer.api.mission.MissionDefinitionAPI
import com.fs.starfarer.api.mission.MissionDefinitionPlugin
import com.fs.starfarer.api.util.Misc
import fleetBuilder.persistence.PersonSerialization
import fleetBuilder.util.ClipboardUtil.getClipboardJson
import fleetBuilder.util.MISC
import fleetBuilder.util.MISC.createErrorVariant
import fleetBuilder.util.MISC.showError
import fleetBuilderCB.customDir
import fleetBuilderCB.defaultFleetFile
import org.json.JSONObject
import org.lazywizard.lazylib.ext.json.iterator
import org.lwjgl.input.Keyboard
import java.util.*


class MissionDefinition : MissionDefinitionPlugin {
    companion object {
        private var speedUp = false
        private var flipSide = false
        private var aiRetreatAllowed = false
        private var enemyDeployAll = true
        private var fightToTheLast = true
        private var pickedLayout = JSONObject()
        private var layoutConfigChoices = mutableListOf<JSONObject>()
        private var defaultCR = true
        private var playerFleetJson: JSONObject = JSONObject()
        private var enemyFleetJson: JSONObject = JSONObject()
        private var init = false
        private var forceDeployAll = true
    }

    private fun init() {
        speedUp = false
        flipSide = false
        aiRetreatAllowed = false
        enemyDeployAll = true
        fightToTheLast = true
        defaultCR = true
        forceDeployAll = true
        //playerFleetJson = JSONObject()
        //enemyFleetJson = JSONObject()


        //var json: JSONObject
        try {
            val defaultFleetsJson = Global.getSettings().readJSONFromCommon(customDir + defaultFleetFile, false)

            playerFleetJson = Global.getSettings().loadJSON(defaultFleetsJson.getString("firstFleet"))
            enemyFleetJson = Global.getSettings().loadJSON(defaultFleetsJson.getString("secondFleet"))
        } catch(_: Exception) {}

        layoutConfigChoices.clear()
        if(layoutConfigChoices.isEmpty()) {
            val json = Global.getSettings().loadJSON("data/missions/mapLayouts.json")
            val jsonArray = json.getJSONArray("layouts")
            for (layout in jsonArray) {
                layoutConfigChoices.add(layout)
            }
        }

        pickedLayout = JSONObject()
        val index = layoutConfigChoices.indexOfFirst { it.optString("name") == "default_no_objective" }
        pickedLayout = layoutConfigChoices[index]


        init = true
    }

    private fun loadFleetFromJson(json: JSONObject, factionId: String): CampaignFleetAPI {
            val fleet = MISC.createFleetFromJson(
                json,
                faction = factionId,
                includeOfficers = true,
                includeCommander = true,
                includeNoOfficerPersonality = true,
                setFlagship = false
            )
            for (member in fleet.fleetData.membersListCopy) {
                //val clone = member.variant.clone()
                //clone.hullVariantId += "_clone"
                //member.setVariant(clone, false, false)
                member.variant.addPermaMod("SCVE_officerdetails_X")
            }

            return fleet
    }

    override fun defineMission(api: MissionDefinitionAPI) {
        if(!init)
            init()
        if(!init) {
            showError("Mission init failed")
            return
        }

        val shiftEnabled = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
        val ctrlEnabled = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
        if (shiftEnabled && ctrlEnabled) init()

        val qDown = Keyboard.isKeyDown(Keyboard.KEY_Q)
        val wDown = Keyboard.isKeyDown(Keyboard.KEY_W)

        if (Keyboard.isKeyDown(Keyboard.KEY_T)) speedUp = !speedUp
        if (Keyboard.isKeyDown(Keyboard.KEY_F)) flipSide = !flipSide
        if (Keyboard.isKeyDown(Keyboard.KEY_A)) aiRetreatAllowed = !aiRetreatAllowed
        if (Keyboard.isKeyDown(Keyboard.KEY_D)) forceDeployAll = !forceDeployAll

        if (Keyboard.isKeyDown(Keyboard.KEY_Z)) {
            val index = layoutConfigChoices.indexOfFirst { it.optString("name") == pickedLayout.optString("name") }

            val nextLayoutObject = if (index != -1) {
                val nextIndex = (index + 1) % layoutConfigChoices.size
                layoutConfigChoices[nextIndex]
            } else {
                layoutConfigChoices.first()
            }

            pickedLayout = nextLayoutObject
        }

        // Try to fetch clipboard fleet data if relevant keys are pressed
        val clipboardJson = if (qDown || wDown) getClipboardJson().also {
            if (it == null) {
                showError("No valid fleet data found in clipboard")
            }
        } else null

        // Assign clipboard data to respective fleet JSONs, only if not null
        clipboardJson?.let {
            if (qDown) playerFleetJson = it
            if (wDown) enemyFleetJson = it
        }

        // Load fleets
        var playerFleet = loadFleetFromJson(playerFleetJson, Factions.PLAYER)
        var enemyFleet = loadFleetFromJson(enemyFleetJson, Factions.PLAYER)

        // Validate fleets
        if (playerFleet.fleetSizeCount == 0) {
            val fleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, FleetTypes.TASK_FORCE, true)
            fleet.fleetData.addFleetMember(Global.getSettings().createFleetMember(FleetMemberType.SHIP, createErrorVariant()))
            playerFleet = fleet
            showError("Failed to create player fleet")
        }
        if (enemyFleet.fleetSizeCount == 0) {
            val fleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, FleetTypes.TASK_FORCE, true)
            fleet.fleetData.addFleetMember(Global.getSettings().createFleetMember(FleetMemberType.SHIP, createErrorVariant()))
            enemyFleet = fleet
            showError("Failed to create enemy fleet ")
        }

        // Choose fleet sides based on flipSide flag
        val (playerSide, enemySide) = if (!flipSide) FleetSide.PLAYER to FleetSide.ENEMY else FleetSide.ENEMY to FleetSide.PLAYER

        // Generate fleets
        generateAPIFleet(api, playerFleet, playerSide, playerFleetJson.optInt("aggression_doctrine", 2), playerFleetJson.optBoolean("useAdmiralAI", true), playerFleetJson.optBoolean("autoSortShips", true), playerFleetJson.optBoolean("automatedPenalty", false))
        generateAPIFleet(api, enemyFleet, enemySide, enemyFleetJson.optInt("aggression_doctrine", 2), enemyFleetJson.optBoolean("useAdmiralAI", true), enemyFleetJson.optBoolean("autoSortShips", true), enemyFleetJson.optBoolean("automatedPenalty", false))

        playerFleetJson.put("mapHeightMult", pickedLayout.getDouble("mapHeightMult"))
        enemyFleetJson.put("mapHeightMult", pickedLayout.getDouble("mapHeightMult"))

        if(forceDeployAll) {
            api.addPlugin(AIBattlesMini_Util.getDeploymentPlugin(playerFleetJson, enemyFleetJson))
        } else {
            //
        }

        api.addBriefingItem(String.format("%s vs. %s", playerFleet.name, enemyFleet.name))

        generateMapSettings(api)

        api.addPlugin(AIBattlesMini_FreeCamPlugin())

        var optionBrief = ""
        if (speedUp) {
            api.addPlugin(AIBattlesMini_Util.getSpeedUpPlugin())
            optionBrief += "1-100x Speed-Up, "
        }
        if (flipSide) {
            optionBrief += "Flipped Player and Enemy Sides, "
        }
        if(aiRetreatAllowed) {
            optionBrief += "AI retreat allowed, "
        }
        if(!forceDeployAll) {
            optionBrief += "Not force deploying entire fleet, "
        }

            /*
        val layoutsDescription = buildString {
            append("Available layouts:\n")
            for ((index, obj) in layoutConfigChoices.withIndex()) {
                val layoutName = obj.optString("layout", "Unnamed")
                append("  ${index + 1}. $layoutName\n")
            }
        }

        api.addBriefingItem(layoutsDescription.trim())*/


        api.addBriefingItem("Picked Layout: ${pickedLayout.optString("name", "Missing")}")

        if (optionBrief.isNotEmpty()) api.addBriefingItem("Changed: " + optionBrief.substring(0, optionBrief.length - 2))
    }


    private fun generateMapSettings(api: MissionDefinitionAPI) {

        api.initMap(
            pickedLayout.getInt("minX").toFloat(),
            pickedLayout.getInt("maxX").toFloat(),
            pickedLayout.getInt("minY").toFloat(),
            pickedLayout.getInt("maxY").toFloat()
        )
        api.context.standoffRange = pickedLayout.getInt("standoffRange").toFloat()

        val objectives = pickedLayout.optJSONArray("objectives")
        if (objectives != null) for (i in 0..<objectives.length()) {
            val objective = objectives.getJSONArray(i)
            api.addObjective(objective.getInt(1).toFloat(), objective.getInt(2).toFloat(), objective.getString(0))
        }

        api.context.aiRetreatAllowed = aiRetreatAllowed
        api.context.enemyDeployAll = enemyDeployAll
        api.context.fightToTheLast = fightToTheLast
    }

    private fun generateAPIFleet(api: MissionDefinitionAPI, fleet: CampaignFleetAPI, side: FleetSide, aggression: Int = 2, useAdmiralAI: Boolean, autoSortShips: Boolean, automatedPenalty: Boolean) {
        // From BattleCreationPluginImpl.java

        val baseCommandPoints = Global.getSettings().getFloat("startingCommandPoints").toInt()
        api.initFleet(
            side,
            null,
            FleetGoal.ATTACK,
            useAdmiralAI,
            (fleet.commanderStats.commandPoints.getModifiedValue() - baseCommandPoints).toInt()
        )

        // If enabled, sort by hull size to partially replicate vanilla deployment.
        // Also ensures ship order in "shipList" does not largely affect the final deployment.
        val memberList: MutableList<FleetMemberAPI> = fleet.fleetData.membersListCopy
        if (autoSortShips) {
            memberList.sortWith(Collections.reverseOrder(HullSizeComparator()))
        }

        //The commander must not be piloting a ship, and the flagship must not exist. If either of these are false, the game crashes on entering the mission.

        //Make copy of commander, and assign them to be the commander
        val tempCommander = PersonSerialization.savePersonToJson(fleet.commander)
        fleet.commander = PersonSerialization.getPersonFromJson(tempCommander)

        var hasDefaultOfficer = false

        var totalDP = 0f
        for (member in memberList) {
            if (automatedPenalty && Misc.isAutomated(member) && !Automated.isAutomatedNoPenalty(member))
                member.variant.addPermaMod("AIBattlesMini_Automated")

            member.repairTracker.cr = member.repairTracker.maxCR

            member.isFlagship = false//Ensure the player wont try to control it, as that causes a crash due to how the ships are deployed.
            api.addFleetMember(side, member)

            totalDP += member.deploymentPointsCost

            if(!hasDefaultOfficer && member.captain.isDefault && !member.captain.isAICore)
                hasDefaultOfficer = true
        }

        if(hasDefaultOfficer) {
            val doctrine: String

            if(aggression == 1) {
                doctrine = "CAUT"
            } else if (aggression == 2) {
                doctrine = "STDY"
            } else if (aggression == 3) {
                doctrine = "AGGR"
            } else if (aggression == 4) {
                doctrine = "A/R"
            } else if (aggression == 5) {
                doctrine = "RECK"
            } else {
                doctrine = "UNKO"
            }

            api.setFleetTagline(
                side,
                java.lang.String.format("%s (%d ships; %d DP; %s doctrine)", fleet.name, fleet.numShips, totalDP.toInt(), doctrine)
            )
        } else {
            api.setFleetTagline(
                side,
                java.lang.String.format("%s (%d ships; %d DP)", fleet.name, fleet.numShips, totalDP.toInt())
            )
        }
    }

    class HullSizeComparator : Comparator<FleetMemberAPI> {
        override fun compare(o1: FleetMemberAPI, o2: FleetMemberAPI): Int {
            var c = o1.variant.hullSize.compareTo(o2.variant.hullSize)
            if (c == 0) c =
                o1.unmodifiedDeploymentPointsCost.compareTo(o2.unmodifiedDeploymentPointsCost)
            if (c == 0) c = o1.fleetPointCost.compareTo(o2.fleetPointCost)
            return c
        }
    }
}