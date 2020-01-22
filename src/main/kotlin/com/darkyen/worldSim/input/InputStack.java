package com.darkyen.worldSim.input;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BooleanArray;

/** Stack of input processors through which events can bubble through. */
public final class InputStack implements InputProcessor {

    private final Array<InputProcessor> stack = new Array<>(true, 4, InputProcessor.class);
    private final BooleanArray stackBubble = new BooleanArray(true, 4);

    public void push(InputProcessor inputProcessor, boolean bubble){
        stack.add(inputProcessor);
        stackBubble.add(bubble);
    }

    public void pushToBottom(InputProcessor inputProcessor, boolean bubble){
        stack.insert(0, inputProcessor);
        stackBubble.insert(0, bubble);
    }

    public boolean pop(InputProcessor inputProcessor){
        //noinspection SimplifiableIfStatement
        if(stack.size == 0)return false;
        final int i = stack.indexOf(inputProcessor, true);
        if(i == -1)return false;
        stack.removeIndex(i);
        stackBubble.removeIndex(i);
        return true;
    }

    public void clear(){
        stack.clear();
        stackBubble.clear();
    }

    public boolean keyDown (int keycode) {
        for (int i = stack.size-1; i >= 0; i--) {
            if (stack.get(i).keyDown(keycode)) return true;
            if (!stackBubble.get(i))return true;
        }
        return false;
    }

    public boolean keyUp (int keycode) {
        for (int i = stack.size-1; i >= 0; i--) {
            if (stack.get(i).keyUp(keycode)) return true;
            if (!stackBubble.get(i))return true;
        }
        return false;
    }

    public boolean keyTyped (char character) {
        for (int i = stack.size-1; i >= 0; i--) {
            if (stack.get(i).keyTyped(character)) return true;
            if (!stackBubble.get(i))return true;
        }
        return false;
    }

    public boolean touchDown (int screenX, int screenY, int pointer, int button) {
        for (int i = stack.size-1; i >= 0; i--) {
            if (stack.get(i).touchDown(screenX, screenY, pointer, button)) return true;
            if (!stackBubble.get(i))return true;
        }
        return false;
    }

    public boolean touchUp (int screenX, int screenY, int pointer, int button) {
        for (int i = stack.size-1; i >= 0; i--) {
            if (stack.get(i).touchUp(screenX, screenY, pointer, button)) return true;
            if (!stackBubble.get(i))return true;
        }
        return false;
    }

    public boolean touchDragged (int screenX, int screenY, int pointer) {
        for (int i = stack.size-1; i >= 0; i--) {
            if (stack.get(i).touchDragged(screenX, screenY, pointer)) return true;
            if (!stackBubble.get(i))return true;
        }
        return false;
    }

    @Override
    public boolean mouseMoved (int screenX, int screenY) {
        for (int i = stack.size-1; i >= 0; i--) {
            if (stack.get(i).mouseMoved(screenX, screenY)) return true;
            if (!stackBubble.get(i))return true;
        }
        return false;
    }

    @Override
    public boolean scrolled (int amount) {
        for (int i = stack.size-1; i >= 0; i--) {
            if (stack.get(i).scrolled(amount)) return true;
            if (!stackBubble.get(i))return true;
        }
        return false;
    }
}
