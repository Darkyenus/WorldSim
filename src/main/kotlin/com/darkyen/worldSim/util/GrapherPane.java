package com.darkyen.worldSim.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.utils.Array;
import com.darkyen.worldSim.WorldSim;

/**
 * @author Darkyen
 */
public final class GrapherPane extends Widget {

    public final Array<GraphData> graphs = new Array<>(false, 8, GraphData.class);
    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final BitmapFont font = WorldSim.INSTANCE.getSkin().getFont("font-ui-small");
    private final StringBuilder tmp = new StringBuilder();

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        batch.end();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setTransformMatrix(batch.getTransformMatrix());
        shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.6f);
        shapeRenderer.rect(getX(), getY(), getWidth(), getHeight());
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        for (GraphData graph : graphs) {
            shapeRenderer.setColor(graph.color);

            float step = getWidth() / graph.data.length;

            for (int i = 0; i < graph.data.length - 1; i++) {
                float x = getX() + step * i;
                float y = getY() + graph.getPointPercentage(i) * getHeight();

                float x2 = getX() + step * i + step;
                float y2 = getY() + graph.getPointPercentage(i+1) * getHeight();

                shapeRenderer.line(x,y,x2,y2);
            }
        }

        shapeRenderer.end();

        batch.begin();

        for (GraphData graph : graphs) {
            float lastPoint = graph.getPoint(-1);
            tmp.setLength(0);
            tmp.append(lastPoint);
            font.setColor(graph.color);
            font.draw(batch, tmp, getX() + getWidth() + 5f, getY() + getHeight() * MathUtils.clamp(graph.getPointPercentage(-1), 0f,1f));
        }
    }

    public static final class GraphData {
        private final float[] data;
        private int end = 0;
        private final Color color;
        private float min;
        private float max;
        private final boolean automax;

        public GraphData(int memory, Color color, float min, float max, boolean automax) {
            this.min = min;
            this.max = max;
            this.data = new float[memory];
            this.color = color;
            this.automax = automax;
        }

        public void addDataPoint(float point){
            data[end] = point;
            end = (end + 1) % data.length;
            if(automax && point > max)max = point;
        }

        private float getPointPercentage(int i){
            float point = data[(end + i + data.length) % data.length];
            return (point - min) / (max - min);
        }

        private float getPoint(int i){
            return data[(end + i + data.length) % data.length];
        }
    }
}
