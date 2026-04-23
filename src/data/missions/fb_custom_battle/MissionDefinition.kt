package data.missions.fb_custom_battle

import aiMiniBattles.tranquility.aibattlesmini.AIBattlesMini_FreeCamPlugin
import aiMiniBattles.tranquility.aibattlesmini.AIBattlesMini_Util
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.fleet.FleetGoal
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.hullmods.Automated
import com.fs.starfarer.api.mission.FleetSide
import com.fs.starfarer.api.mission.MissionDefinitionAPI
import com.fs.starfarer.api.mission.MissionDefinitionPlugin
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import com.fs.starfarer.api.util.Misc
import fleetBuilder.core.displayMessage.DisplayMessage
import fleetBuilder.otherMods.starficz.addImage
import fleetBuilder.otherMods.starficz.addTooltip
import fleetBuilder.otherMods.starficz.onClick
import fleetBuilder.otherMods.starficz.width
import fleetBuilder.serialization.SerializationUtils
import fleetBuilder.serialization.fleet.CompressedFleet
import fleetBuilder.serialization.fleet.DataFleet
import fleetBuilder.serialization.fleet.JSONFleet
import fleetBuilder.serialization.person.DataPerson
import fleetBuilder.ui.addCheckboxD
import fleetBuilder.ui.customPanel.common.DialogPanel
import fleetBuilder.util.ReflectionMisc
import fleetBuilder.util.api.VariantUtils
import fleetBuilder.util.lib.ClipboardUtil
import fleetBuilderCB.customDir
import fleetBuilderCB.defaultFleetFile
import fleetBuilderCB.missionID
import fleetBuilderCB.otherMods.starficz.ReflectionUtils.getMethodsMatching
import fleetBuilderCB.otherMods.starficz.ReflectionUtils.invoke
import org.json.JSONObject
import org.lazywizard.lazylib.ext.json.iterator
import java.util.*


// Be advised, the content of this file is extreme jank and is not at all maintained properly. It should honestly be rewritten.

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
        private var playerFleetData: DataFleet.ParsedFleetData? = null
        private var enemyFleetData: DataFleet.ParsedFleetData? = null
        private var init = false
        private var forceDeployAll = true
        private var applyOfficerDetails = true
    }

    private fun init() {
        speedUp = false
        flipSide = false
        aiRetreatAllowed = false
        enemyDeployAll = true
        fightToTheLast = true
        defaultCR = true
        forceDeployAll = true
        applyOfficerDetails = true
        //playerFleetJson = JSONObject()
        //enemyFleetJson = JSONObject()


        //var json: JSONObject
        try {
            val defaultFleetsJson = Global.getSettings().readJSONFromCommon(customDir + defaultFleetFile, false)

            val firstFleetJSON = Global.getSettings().loadJSON(defaultFleetsJson.getString("firstFleet"))
            val secondFleetJSON = Global.getSettings().loadJSON(defaultFleetsJson.getString("secondFleet"))
            playerFleetData = SerializationUtils.extractDataFromAny(firstFleetJSON) as DataFleet.ParsedFleetData
            enemyFleetData = SerializationUtils.extractDataFromAny(secondFleetJSON) as DataFleet.ParsedFleetData
        } catch (_: Exception) {
        }

        layoutConfigChoices.clear()
        if (layoutConfigChoices.isEmpty()) {
            val json = Global.getSettings().loadJSON("data/missions/mapLayouts.json")
            val jsonArray = json.getJSONArray("layouts")
            for (layout in jsonArray) {
                layoutConfigChoices.add(layout)
            }
        }

        pickedLayout = layoutConfigChoices.first()


        init = true
    }

    private fun loadFleetFromData(inputData: Any?, factionId: String): CampaignFleetAPI {
        if(inputData !is DataFleet.ParsedFleetData)
            throw Exception("Fail")

        val fleet = DataFleet.createCampaignFleetFromData(
            inputData.copy(factionID = factionId), true)

        for (member in fleet.fleetData.membersListCopy) {
            //val clone = member.variant.clone()
            //clone.hullVariantId += "_clone"
            //member.setVariant(clone, false, false)
            if(applyOfficerDetails)
                member.variant.addPermaMod("SCVE_officerdetails_X")
        }

        return fleet
    }

    override fun defineMission(api: MissionDefinitionAPI) {
        if (!init)
            init()
        if (!init) {
            DisplayMessage.showError("Mission init failed")
            return
        }

        // Load fleets
        val playerFleet = loadFleetFromData(playerFleetData, Factions.PLAYER)
        val enemyFleet = loadFleetFromData(enemyFleetData, Factions.PLAYER)

        // Validate fleets
        validateFleet(playerFleet, FleetSide.PLAYER)
        validateFleet(enemyFleet, FleetSide.ENEMY)


        // This UI is a terrible hack
        if(FBCBMissionListener.missionUI == null) {
            val screenPanel = ReflectionMisc.getScreenPanel() ?: return

            val missionThing = (screenPanel.invoke("getChildrenCopy") as List<*>).find { it?.getMethodsMatching(name = "getMissionList")?.isNotEmpty() == true }
            val missionList = missionThing?.invoke("getMissionList")
            val missionDetail = missionThing?.invoke("getMissionDetail") as? UIPanelAPI ?: return

            val missionOrderOfBattle = (missionDetail.invoke("getChildrenCopy") as List<*>).find { it?.getMethodsMatching(name = "getRefit")?.isNotEmpty() == true } as? UIPanelAPI
                ?: return
            val yourFlagship = (missionDetail.invoke("getChildrenCopy") as List<*>).find { it?.getMethodsMatching(name = "setVariant")?.isNotEmpty() == true } as? UIPanelAPI
                ?: return
            //yourFlagship.invoke("setOpacity", 0f)

            /*val detailChildren = missionDetail.invoke("getChildrenCopy") as List<*>
            detailChildren.getOrNull(0)?.invoke("setOpacity", 0f)//Title
            detailChildren.getOrNull(1)?.invoke("setOpacity", 0f)//Tactical briefing
            detailChildren.getOrNull(2)?.invoke("setOpacity", 0f)//Description
            detailChildren.getOrNull(3)?.invoke("setOpacity", 0f)//Your flagship
            detailChildren.getOrNull(4)?.invoke("setOpacity", 0f)//Order of battle + each fleet & refit / reset buttons*/



            val buttonHeight = 24f

            val dialog = DialogPanel(headerTitle = "Custom Battle Settings")
            dialog.allowHotkeyQuit = false
            dialog.dialogStyle = false
            dialog.darkenBackground = false
            dialog.consumeInnerMouseEvents = true
            dialog.consumeAllEvents = false
            dialog.background.alphaMult = 1f

            dialog.show(missionDetail.position.width, missionDetail.position.height, parent = missionDetail, xOffset = 0f, yOffset = missionDetail.position.height) { ui ->
                fun resetMission() {
                    //Reload UI
                    missionDetail.removeComponent(dialog.panel)
                    FBCBMissionListener.missionUI = null

                    //Reload this mission
                    missionList?.invoke("selectMission", missionID)
                }

                ui.addButton("Reset Settings", null, ui.width, buttonHeight, 0f).onClick {
                    init = false

                    resetMission()
                }

                ui.addSpacer(buttonHeight)

                ui.addButton("Click to assign clipboard to player fleet", null, ui.width, buttonHeight, 0f).onClick {
                    val data = ClipboardUtil.getClipboardContentsAutoJSON()?.let { SerializationUtils.extractDataFromAny(it) as? DataFleet.ParsedFleetData }
                    if(data !is DataFleet.ParsedFleetData) {
                        DisplayMessage.showError("No valid fleet data found in clipboard")
                    } else {
                        playerFleetData = data
                    }

                    resetMission()
                    dialog.confirmButton?.opacity = 0f
                }
                ui.addButton("Click to assign clipboard to enemy fleet", null, ui.width, buttonHeight, 0f).onClick {
                    val data = ClipboardUtil.getClipboardContentsAutoJSON()?.let { SerializationUtils.extractDataFromAny(it) as? DataFleet.ParsedFleetData }
                    if(data !is DataFleet.ParsedFleetData) {
                        DisplayMessage.showError("No valid fleet data found in clipboard")
                    } else {
                        enemyFleetData = data
                    }

                    resetMission()
                    dialog.confirmButton?.opacity = 0f
                }

                ui.addSpacer(buttonHeight / 2)



                ui.addPara("During the battle, the following keys can be pressed.\n" +
                        "\n" +
                        "Alt: Disable Free-Cam and return to vanilla camera\n" +
                        "C: Enable/switch Free-Cam modes\n" +
                        "Left-Click: Zoom In (Free-Cam)\n" +
                        "Right-Click: Zoom Out (Free-Cam)\n" +
                        "Right-Ctrl: Hide/show ship UI\n" +
                        "\n" +
                        "In Free-Cam mode, you no longer need to jump from ship-to-ship to watch the battle; the camera is completely free.\n" +
                        "- Drag Free-Cam smoothly pans the camera in the direction of your mouse.\n" +
                        "- Absolute Free-Cam directly ties the camera to your mouse movements.", 0f)

                ui.addSpacer(buttonHeight * 2)

                ui.addPara("Layout Config:", 0f)
                var currentLayout = pickedLayout.optString("name")
                val layouts = layoutConfigChoices.map { it.optString("name") }
                val toggles = layouts.indices.map { index ->
                    ui.addCheckboxD(
                        name = layouts[index],
                        data = layouts[index],
                        isChecked = layouts[index] == currentLayout
                    )
                }

                toggles.forEach { toggle ->
                    toggle.onClick {
                        currentLayout = toggle.customData as String

                        pickedLayout = layoutConfigChoices.first { it.optString("name") == currentLayout }
                        toggles.forEach { it.isChecked = it.customData == currentLayout }
                        dialog.confirmButton?.opacity = 1f
                    }
                }

                ui.addSpacer(buttonHeight + 4f)

                ui.addCheckboxD("Apply Officer Details HullMod", applyOfficerDetails).onClick {
                    dialog.confirmButton?.opacity = 1f
                    applyOfficerDetails = !applyOfficerDetails
                }

                ui.addCheckboxD("Speed Up", speedUp).onClick {
                    dialog.confirmButton?.opacity = 1f
                    speedUp = !speedUp
                }

                ui.addCheckboxD("Flip Side", flipSide).onClick {
                    dialog.confirmButton?.opacity = 1f
                    flipSide = !flipSide
                }

                ui.addCheckboxD("Allow AI Retreat", aiRetreatAllowed).onClick {
                    dialog.confirmButton?.opacity = 1f
                    aiRetreatAllowed = !aiRetreatAllowed
                }

                ui.addCheckboxD("Force Deploy All", forceDeployAll).onClick {
                    dialog.confirmButton?.opacity = 1f
                    forceDeployAll = !forceDeployAll
                }

                dialog.onConfirm {
                    resetMission()

                    dialog.confirmButton?.opacity = 0f
                }

                val pad = 10f

                fun addCommanderWithTooltip(
                    fleet: CampaignFleetAPI,
                    offsetX: Float,
                    offsetY: Float
                ) {
                    val commanderImage = dialog.panel.addImage(fleet.commander.portraitSprite, 64f, 64f)
                    //val commanderImage = ui.starfizAddImage(fleet.commander.portraitSprite, 64f, 64f)

                    commanderImage.position.setXAlignOffset(
                        -commanderImage.position.x + missionOrderOfBattle.position.x - 32f - 12f + offsetX
                    )
                    commanderImage.position.setYAlignOffset(
                        -commanderImage.position.y + missionOrderOfBattle.position.y + 32f + 16f + offsetY
                    )

                    commanderImage.uiImage.addTooltip(TooltipMakerAPI.TooltipLocation.LEFT, 280f) { tooltip ->
                        val skills = fleet.commander.stats.skillsCopy
                            .filter { it.skill.isAdmiralSkill && it.level > 0f }

                        tooltip.addTitle(fleet.commander.nameString)

                        tooltip.addSectionHeading("Commander Skills:", Alignment.MID, pad)

                        for (skill in skills) {
                            val skillImageWithText = tooltip.beginImageWithText(skill.skill.spriteName, 40f)
                            skillImageWithText.addPara(skill.skill.name, 0f)
                            tooltip.addImageWithText(pad)
                        }
                    }
                }

                addCommanderWithTooltip(
                    playerFleet,
                    offsetX = 0f,
                    offsetY = if(!flipSide) 128f else -8f
                )
                addCommanderWithTooltip(
                    enemyFleet,
                    offsetX = 0f,
                    offsetY = if(flipSide) 128f else -8f
                )

                dialog.addActionButtons(confirmText = "Apply", alignment = Alignment.LMID, addCancelButton = false)
                dialog.doesConfirmDismiss = false
            }
            dialog.setMaxSize()

            dialog.confirmButton?.opacity = 0f

            missionDetail.bringComponentToTop(missionOrderOfBattle)

            FBCBMissionListener.missionUI = dialog.panel

        }

        // Choose fleet sides based on flipSide flag
        val (playerSide, enemySide) = if (!flipSide) FleetSide.PLAYER to FleetSide.ENEMY else FleetSide.ENEMY to FleetSide.PLAYER

        val playerFleetJson = JSONObject().apply {
            put("useAdmiralAI", true)
            put("autoSortShips", false)
            put("automatedPenalty", false)
        }
        val enemyFleetJson = JSONObject().apply {
            put("useAdmiralAI", true)
            put("autoSortShips", false)
            put("automatedPenalty", false)
        }

        // Generate fleets
        generateAPIFleet(api, playerFleet, playerSide, playerFleetData!!.aggression, playerFleetJson.optBoolean("useAdmiralAI", true), playerFleetJson.optBoolean("autoSortShips", false), playerFleetJson.optBoolean("automatedPenalty", false))
        generateAPIFleet(api, enemyFleet, enemySide, enemyFleetData!!.aggression, enemyFleetJson.optBoolean("useAdmiralAI", true), enemyFleetJson.optBoolean("autoSortShips", false), enemyFleetJson.optBoolean("automatedPenalty", false))

        playerFleetJson.put("mapHeightMult", pickedLayout.getDouble("mapHeightMult"))
        enemyFleetJson.put("mapHeightMult", pickedLayout.getDouble("mapHeightMult"))

        if (forceDeployAll) {
            api.addPlugin(AIBattlesMini_Util.getDeploymentPlugin(playerFleetJson, enemyFleetJson))
        } else {
            //
        }

        api.addBriefingItem(String.format("%s vs. %s", playerFleet.name, enemyFleet.name))

        generateMapSettings(api)

        api.addPlugin(AIBattlesMini_FreeCamPlugin())

        if (speedUp) {
            api.addPlugin(AIBattlesMini_Util.getSpeedUpPlugin())
        }
    }

    private fun validateFleet(fleet: CampaignFleetAPI, fleetSide: FleetSide) {
        if (fleet.fleetSizeCount == 0) {
            fleet.fleetData.addFleetMember(Global.getSettings().createFleetMember(FleetMemberType.SHIP, VariantUtils.createErrorVariant()))
            DisplayMessage.showError("Failed to create fleet on side ${fleetSide.name}")
        }
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

    private fun generateAPIFleet(
        api: MissionDefinitionAPI,
        fleet: CampaignFleetAPI,
        side: FleetSide,
        aggression: Int = -1,
        useAdmiralAI: Boolean,
        autoSortShips: Boolean,
        automatedPenalty: Boolean
    ) {
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

        //The commander must not be piloting a ship, otherwise the game crashes on entering the mission.

        //Make copy of commander, and assign them to be the commander
        fleet.commander = DataPerson.clonePerson(fleet.commander)

        var hasDefaultOfficer = false

        var totalDP = 0f
        for (member in memberList) {
            if (automatedPenalty && Misc.isAutomated(member) && !Automated.isAutomatedNoPenalty(member))
                member.variant.addPermaMod("AIBattlesMini_Automated")

            member.repairTracker.cr = member.repairTracker.maxCR

            member.isFlagship = false//Ensure the player wont try to control it, as that causes a crash due to how the ships are deployed.
            api.addFleetMember(side, member)

            totalDP += member.deploymentPointsCost

            if (!hasDefaultOfficer && member.captain.isDefault && !member.captain.isAICore)
                hasDefaultOfficer = true
        }

        if (hasDefaultOfficer && aggression >= 0) {
            val doctrine: String = when (aggression) {
                1 -> { "CAUT" }
                2 -> { "STDY" }
                3 -> { "AGGR" }
                4 -> { "A/R" }
                5 -> { "RECK" }
                else -> { "" }
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