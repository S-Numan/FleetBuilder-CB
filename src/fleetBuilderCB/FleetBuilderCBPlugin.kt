package fleetBuilderCB

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import fleetBuilder.config.ModSettings.PRIMARYDIR
import org.json.JSONObject

const val customDir = (PRIMARYDIR + "CustomBattle/")
const val defaultFleetFile = "defaultFleets"
const val missionID = "fb_custom_battle"

class FleetBuilderCBPlugin : BaseModPlugin() {

    override fun onApplicationLoad() {
        super.onApplicationLoad()

        if(!Global.getSettings().fileExistsInCommon(customDir + defaultFleetFile)) {
            val defaultFleets = JSONObject()
            defaultFleets.put("firstFleet", "data/fleets/Core Fleets/Tritachyon Patrol.json")
            defaultFleets.put("secondFleet", "data/fleets/Core Fleets/Hegemony Fast Picket.json")

            Global.getSettings().writeJSONToCommon(customDir + defaultFleetFile, defaultFleets, false)
        }
    }
}