// Taken from SCVE by rubi

package SCVE.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;

//SCVE_officerdetails_X

public class SCVE_OfficerDetails extends BaseHullMod {

    public static final String MOD_PREFIX = "SCVE";

    public static String getString(String id) {
        return Global.getSettings().getString(MOD_PREFIX, id);
    }

    public static Logger log = Global.getLogger(SCVE_OfficerDetails.class);
    public static boolean firstFrame = true;

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            ship.getVariant().removeMod(spec.getId());
            ship.getVariant().removePermaMod(spec.getId());
            return;
        }
        // ship.getCaptain() returns null for the first frame
        if (firstFrame) {
            firstFrame = false;
            return;
        }
        if (ship.getCaptain() != null && ship.getCaptain().getNameString().isEmpty()) {
            ship.getVariant().removePermaMod(spec.getId());
            firstFrame = true;
        }

    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        // this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return getString("hullModCampaignError");
        }
        if (ship.getCaptain() == null) {
            return getString("hullModNoPermaMods");
        }
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        //this needs to do nothing if done in campaign
        return (Global.getSettings().getCurrentState() == GameState.TITLE
                && ship.getCaptain() != null
                && !ship.getCaptain().getNameString().isEmpty());
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        if(ship == null)
            return;

        PersonAPI person = ship.getCaptain();
        final float PAD = 10f;

        if (person != null && !person.isDefault()) {
            String title, imageText;
            float portraitHeight = 100;

            String shipName = ship.getName();
            String fullName = person.getNameString();
            String portrait = person.getPortraitSprite();
            String level = Integer.toString(person.getStats().getLevel());
            String personality = person.getPersonalityAPI().getDisplayName();
            List<SkillLevelAPI> skills = person.getStats().getSkillsCopy();
            //String desc = person.getMemoryWithoutUpdate().getString("$quote");

            boolean isAdmiral = false;
            /* todo maybe I'll re-add admiral skills later
            if (ship.getFleetMember().isFlagship()) {
                isAdmiral = true;
            }
             */

            if (isAdmiral) {
                title = getString("hullModOfficerDetailAdmiralTitle");
                imageText = String.format(getString("hullModOfficerDetailAdmiralText"), shipName, fullName, level, personality);
            } else {
                title = getString("hullModOfficerDetailOfficerTitle");
                imageText = String.format(getString("hullModOfficerDetailOfficerText"), shipName, fullName, level, personality);
            }
            tooltip.addSectionHeading(title, Alignment.MID, PAD);

            TooltipMakerAPI officerImageWithText = tooltip.beginImageWithText(portrait, portraitHeight);
            officerImageWithText.addPara(imageText,
                                         -portraitHeight / 2, Color.YELLOW,
                                         shipName, fullName, level, personality);
            tooltip.addImageWithText(PAD);

            if (isAdmiral) {
                tooltip.addSectionHeading(getString("v"), Alignment.MID, PAD);
                for (SkillLevelAPI skill : skills) {
                    float skillLevel = skill.getLevel();
                    if (!skill.getSkill().isAdmiralSkill() || skillLevel == 0) continue;
                    String skillSprite = skill.getSkill().getSpriteName();
                    String skillName = skill.getSkill().getName();
                    TooltipMakerAPI skillImageWithText = tooltip.beginImageWithText(skillSprite, 40);
                    skillImageWithText.addPara(skillName, 0);
                    tooltip.addImageWithText(PAD);
                }
            }

            tooltip.addSectionHeading(getString("hullModOfficerDetailOfficerSkills"), Alignment.MID, PAD);
            for (SkillLevelAPI skill : skills) {
                float skillLevel = skill.getLevel();
                if (!skill.getSkill().isCombatOfficerSkill() || skillLevel == 0) continue;
                String skillSprite = skill.getSkill().getSpriteName();
                String skillName = skill.getSkill().getName();
                String eliteText = (skillLevel == 2) ? String.format(" (%s)",getString("hullModOfficerDetailElite")) : "";
                TooltipMakerAPI skillImageWithText = tooltip.beginImageWithText(skillSprite, 40);
                skillImageWithText.addPara(skillName + eliteText, 0, Color.GREEN, eliteText);
                tooltip.addImageWithText(PAD);
            }
        }
    }

    @Override
    public int getDisplaySortOrder() {
        return -1;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return -10;
    }

    @Override
    public Color getNameColor() {
        return Color.MAGENTA;
    }
}
