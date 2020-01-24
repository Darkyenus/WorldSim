package com.darkyen.worldSim.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.antag99.retinazer.EngineService;

/**
 * @author Darkyen
 */
public abstract class UISystem implements InputProcessor, EngineService {

    private final Viewport viewport;
    public final Stage root;

    public UISystem(Batch batch) {
        this(new ScreenViewport(), batch);
    }

    public UISystem(Viewport viewport, Batch batch) {
        this.viewport = viewport;
        this.root = new Stage(viewport, batch);
    }

    @Override
    public void initialize() {
        createUI();
    }

    protected abstract void createUI();

    private int lastW, lastH;

    @Override
    public void update() {
        final int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        if(w != lastW || h != lastH){
            layout();
            viewport.update(w, h, true);
            lastW = w;
            lastH = h;
        }

        root.act(Gdx.graphics.getDeltaTime());
        root.draw();
    }

    protected void layout(){}

    //region InputProcessor
    @Override
    public boolean keyDown(int keycode) {
        return root.keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        return root.keyUp(keycode);
    }

    @Override
    public boolean keyTyped(char character) {
        return root.keyTyped(character);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return root.touchDown(screenX, screenY, pointer, button);
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return root.touchUp(screenX, screenY, pointer, button);
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return root.touchDragged(screenX, screenY, pointer);
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return root.mouseMoved(screenX, screenY);
    }

    @Override
    public boolean scrolled(int amount) {
        return root.scrolled(amount);
    }
    //endregion
}
