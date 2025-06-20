package fleetBuilderCB

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import fleetBuilder.variants.LoadoutManager.fleetDir
import fleetBuilder.variants.LoadoutManager.primaryDir
import org.json.JSONObject

const val customDir = (primaryDir + "CustomBattle/")
const val defaultFleetFile = "defaultFleets"

class FleetBuilderCBPlugin : BaseModPlugin() {

    override fun onApplicationLoad() {
        super.onApplicationLoad()

        if(!Global.getSettings().fileExistsInCommon(customDir + defaultFleetFile)) {
            val defaultFleets = JSONObject()
            defaultFleets.put("firstFleet", "data/fleets/Vanilla/TritachyonPatrol.json")
            defaultFleets.put("secondFleet", "data/fleets/Vanilla/HegemonyFastPicket.json")

            Global.getSettings().writeJSONToCommon(customDir + defaultFleetFile, defaultFleets, false)
        }

    }
    override fun onGameLoad(newGame: Boolean) {
        super.onGameLoad(newGame)

        val sector = Global.getSector() ?: return

        val listeners = sector.listenerManager

    }

}