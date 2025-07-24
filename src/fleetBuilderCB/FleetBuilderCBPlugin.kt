package fleetBuilderCB

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import fleetBuilder.config.ModSettings.PRIMARYDIR
import org.json.JSONObject

const val customDir = (PRIMARYDIR + "CustomBattle/")
const val defaultFleetFile = "defaultFleets"

class FleetBuilderCBPlugin : BaseModPlugin() {

    override fun onApplicationLoad() {
        super.onApplicationLoad()

        if(!Global.getSettings().fileExistsInCommon(customDir + defaultFleetFile)) {
            val defaultFleets = JSONObject()
            defaultFleets.put("firstFleet", "data/fleets/Fleets_Core/TritachyonPatrol.json")
            defaultFleets.put("secondFleet", "data/fleets/Fleets_Core/HegemonyFastPicket.json")

            Global.getSettings().writeJSONToCommon(customDir + defaultFleetFile, defaultFleets, false)
        }

    }
}