package com.darkyen.worldSim.util;

import com.badlogic.gdx.math.FloatCounter;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class PerlinNoise {
    private final long seed;
    private final RandomXS128 r = new RandomXS128();

    public PerlinNoise(long seed) {
        this.seed = seed;
    }

    private static float i(float f, float t, float a){
        return MathUtils.lerp(f,t,3*a*a-2*a*a*a);
    }

    private static long hash (long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x;
    }

    private void seed(int x, int y, int subSeed){
        long base = (((long) x << 32) | (y & 0xFFFFFFFFL)) ^ seed;
        r.setSeed(hash(base + subSeed));
    }

    public float sample(float x, float y, int octavePower, float magnitude){
        int firstX = (int)(x / octavePower);
        int firstY = (int)(y / octavePower);

        float xA = (x/(float)octavePower)%1;
        if(x < 0f) {
            xA = 1f + xA;
            firstX--;
        }
        float yA = (y/(float)octavePower)%1;
        if(y < 0f) {
            yA = 1f + yA;
            firstY--;
        }

        int secondX = firstX + 1;
        int secondY = firstY + 1;

        seed(firstX, firstY, octavePower);
        float c00 = r.nextFloat();

        seed(secondX, firstY, octavePower);
        float c10 = r.nextFloat();

        seed(secondX, secondY, octavePower);
        float c11 = r.nextFloat();

        seed(firstX, secondY, octavePower);
        float c01 = r.nextFloat();

        return i(i(c00, c10, xA), i(c01, c11, xA), yA) * magnitude;
    }

    public float sample(float x, float y){
        return (sample(x,y,256,1f/2f) + sample(x,y,128,1f/4f) + sample(x,y,64,1f/8f) + sample(x,y,32,1f/16f))  * (16f / 15f);
    }

    public float sampleDense(float x, float y){
        return (sample(x,y,64,1f/2f) + sample(x,y,32,1f/4f) + sample(x,y,16,1f/8f) + sample(x,y,8,1f/16f)) * (16f / 15f);
    }

    public float sampleVeryDense(float x, float y){
        return (sample(x,y,32,1f/2f) + sample(x,y,16,1f/4f) + sample(x,y,8,1f/8f) + sample(x,y,4,1f/16f)) * (16f / 15f);
    }

    public static void main(String[] args) throws IOException {
        PerlinNoise n = new PerlinNoise(System.currentTimeMillis());
        int size = 1024;
        int factor = 1;
        FloatCounter counter = new FloatCounter(0);
        BufferedImage image = new BufferedImage(size,size, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g = image.createGraphics();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float value = n.sample((x-size/2)/factor,(y-size/2)/factor-100);
                value = MathUtils.clamp(value, 0f, 1f);
                counter.put(value);
                //System.out.println(value);
                g.setColor(new java.awt.Color(value, value, value));
                g.fillRect(x, y, 1, 1);
            }
        }
        ImageIO.write(image, "png", new File("PerlinNoise.png"));
    }
}
