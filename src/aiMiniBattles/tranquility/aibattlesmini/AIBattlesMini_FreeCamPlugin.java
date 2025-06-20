package aiMiniBattles.tranquility.aibattlesmini;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 * Uses code from <a href="https://github.com/IgGusev/AIBattles/blob/main/src/data/scripts/plugins/AI_freeCamPlugin.java">AI Battle's free camera plugin</a>
 */
public class AIBattlesMini_FreeCamPlugin extends BaseEveryFrameCombatPlugin {
    private CombatEngineAPI engine;
    private int freeCam;
    private boolean zoomIn, zoomOut;
    private boolean camToggle, hideShipUI;
    private float mapX, mapY, screenX, screenY, scale, zoomX, zoomY;
    private Vector2f target = new Vector2f();

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            engine = Global.getCombatEngine();
            camToggle = true; // Enable free cam by default
            freeCam = 1; // Will be set to absolute cam

            screenX = Global.getSettings().getScreenWidth();
            screenY = Global.getSettings().getScreenHeight();
            zoomX = screenX;
            zoomY = screenY;
            mapX = engine.getMapWidth();
            mapY = engine.getMapHeight();

            hideShipUI = true;
            engine.getCombatUI().hideShipInfo();
        }

        // Hides/shows the ship info UI
        if (Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            hideShipUI = !hideShipUI;
            if (hideShipUI) engine.getCombatUI().hideShipInfo();
            else engine.getCombatUI().reFanOutShipInfo();
            //Global.getCombatEngine().getFogOfWar(0).revealAroundPoint(null, 0, 4000f, 4000f);
        }

        if (Keyboard.isKeyDown(Keyboard.KEY_C)) {
            camToggle = true;
        } else if (camToggle) {
            camToggle = false;

            freeCam++;
            if (freeCam == 3) freeCam = 1;

            engine.getViewport().setExternalControl(freeCam > 0);
            target = new Vector2f();
            scale = mapY / screenY;
            zoomX = engine.getViewport().getVisibleWidth();
            zoomY = engine.getViewport().getVisibleHeight();
        }

        switch (freeCam) {
            case 2: { // Absolute
                for (InputEventAPI i : events) {
                    if (i.isRMBDownEvent()) zoomOut = true;
                    if (i.isRMBUpEvent()) zoomOut = false;
                    if (i.isLMBDownEvent()) zoomIn = true;
                    if (i.isLMBUpEvent()) zoomIn = false;
                    if (i.isAltDown()) {
                        freeCam = 0;
                        camToggle = true;
                    }
                    if (i.isMouseMoveEvent()) {
                        target = new Vector2f(i.getX() - (screenX / 2), i.getY() - (screenY / 2));
                        target.scale(scale);
                    }
                }

                Vector2f move = new Vector2f(engine.getViewport().getCenter());
                Vector2f.sub(target, move, move);
                move.scale(amount);
                Vector2f.add(move, engine.getViewport().getCenter(), move);

                if (zoomIn) {
                    zoomX -= screenX * amount * 3;
                    zoomX = Math.max(screenX / 2, zoomX);
                    zoomY -= screenY * amount * 3;
                    zoomY = Math.max(screenY / 2, zoomY);
                    engine.getViewport().set(engine.getViewport().getLLX(), engine.getViewport().getLLY(), zoomX, zoomY);
                } else if (zoomOut) {
                    zoomX += screenX * amount * 3;
                    zoomX = Math.min(screenX * 10, zoomX);
                    zoomY += screenY * amount * 3;
                    zoomY = Math.min(screenY * 10, zoomY);
                    engine.getViewport().set(engine.getViewport().getLLX(), engine.getViewport().getLLY(), zoomX, zoomY);
                }
                engine.getViewport().setCenter(move);
            }
            case 1: { // Smooth
                for (InputEventAPI i : events) {
                    if (i.isRMBDownEvent()) zoomOut = true;
                    if (i.isRMBUpEvent()) zoomOut = false;
                    if (i.isLMBDownEvent()) zoomIn = true;
                    if (i.isLMBUpEvent()) zoomIn = false;
                    if (i.isAltDown()) {
                        freeCam = -1;
                        camToggle = true;
                    }
                    if (i.isMouseMoveEvent()) {
                        target = new Vector2f(i.getX() - (screenX / 2), i.getY() - (screenY / 2));
                        if (target.lengthSquared() > Math.pow(screenY / 2, 2)) {
                            //clamp max offset
                            target = MathUtils.getPointOnCircumference(null, screenY / 2, VectorUtils.getFacing(target));
                        }
                    }
                }

                float smooth = target.lengthSquared() / (float) Math.pow(screenY / 4, 2f);
                Vector2f move = new Vector2f(target);
                move.scale(amount * smooth);
                Vector2f.add(move, engine.getViewport().getCenter(), move);
                move = new Vector2f(Math.max(-mapX / 2, Math.min(mapX / 2, move.x)), Math.max(-mapY / 2, Math.min(mapY / 2, move.y)));

                if (zoomIn) {
                    zoomX -= screenX * amount * 3;
                    zoomX = Math.max(screenX / 2, zoomX);
                    zoomY -= screenY * amount * 3;
                    zoomY = Math.max(screenY / 2, zoomY);
                    engine.getViewport().set(engine.getViewport().getLLX(), engine.getViewport().getLLY(), zoomX, zoomY);
                } else if (zoomOut) {
                    zoomX += screenX * amount * 3;
                    zoomX = Math.min(screenX * 10, zoomX);
                    zoomY += screenY * amount * 3;
                    zoomY = Math.min(screenY * 10, zoomY);
                    engine.getViewport().set(engine.getViewport().getLLX(), engine.getViewport().getLLY(), zoomX, zoomY);
                }
                engine.getViewport().setCenter(move);
            }
        }
    }
}