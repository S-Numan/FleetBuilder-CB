package data.missions.moreloadouts_fleet_tester

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.mission.MissionDefinitionAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.UIPanelAPI
import fleetBuilder.util.ReflectionMisc
import starficz.ReflectionUtils.getFieldsMatching
import starficz.ReflectionUtils.getMethodsMatching
import starficz.ReflectionUtils.invoke


internal class FBCBMissionListener : EveryFrameCombatPlugin {
    companion object {
        var missionUI: CustomPanelAPI? = null
    }

    override fun advance(
        amount: Float,
        events: List<InputEventAPI?>?
    ) {
        if (missionUI == null) return

        val coreUI = ReflectionMisc.getCoreUI() ?: return

        val missionThing = (coreUI.invoke("getChildrenCopy") as List<*>).find { it?.getMethodsMatching(name = "getMissionList")?.isNotEmpty() == true }
        val missionDetail = missionThing?.invoke("getMissionDetail") as? UIPanelAPI ?: return

        val api = missionDetail.invoke("getPreview") as? MissionDefinitionAPI ?: return
        val spec = api.invoke("getSpec") ?: return
        val missionOpen = spec.getFieldsMatching(fieldAccepts = String::class.java).find { (it.get(spec) as String).contains("moreloadouts_fleet_tester") } != null
        val addedMissionUI = (missionDetail.invoke("getChildrenCopy") as List<*>).find { it === missionUI }

        if (!missionOpen) {//When you leave the screen
            missionDetail.removeComponent(missionUI)
            missionUI = null
        } else if (addedMissionUI == null) {//Typically on mission finish when brought back to the mission. You don't technically leave the mission in title-screen state
            missionUI = null

            //Regenerate the mission, mostly for the UI to appear again
            val missionList = missionThing.invoke("getMissionList")
            missionList?.invoke("selectMission", "moreloadouts_fleet_tester")
        }
    }




    override fun processInputPreCoreControls(
        amount: Float,
        events: List<InputEventAPI?>?
    ) {

    }

    override fun renderInWorldCoords(viewport: ViewportAPI?) {

    }

    override fun renderInUICoords(viewport: ViewportAPI?) {

    }

    override fun init(engine: CombatEngineAPI?) {

    }


}