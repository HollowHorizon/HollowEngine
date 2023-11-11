package ru.hollowhorizon.hollowengine.client.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

public class TextAnimation {
    public static Vec2[] RANDOM_DIR;

    public static void init() {
        int len = 30;
        List<Vec2> dirs = new ArrayList<>(len);
        float step = (float) (Math.PI * 2 / len);
        for (int i = 0; i < len; i++) {
            float rad = step * i;
            dirs.add(new Vec2(Mth.cos(rad), Mth.sin(rad)));
        }
        Collections.shuffle(dirs);
        RANDOM_DIR = dirs.toArray(Vec2[]::new);
    }
}
