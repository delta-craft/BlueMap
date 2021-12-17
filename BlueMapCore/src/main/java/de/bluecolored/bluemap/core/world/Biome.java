/*
 * This file is part of BlueMap, licensed under the MIT License (MIT).
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bluecolored.bluemap.core.world;

import de.bluecolored.bluemap.core.debug.DebugDump;
import de.bluecolored.bluemap.core.util.ConfigUtils;
import de.bluecolored.bluemap.core.util.math.Color;
import org.spongepowered.configurate.ConfigurationNode;

@DebugDump
public class Biome {

    public static final Biome DEFAULT = new Biome("minecraft:ocean");

    private final String namespace;
    private final String id;
    private final String fullId;
    private float humidity = 0.5f;
    private float temp = 0.5f;
    private final Color waterColor = new Color().set(4159204).premultiplied();

    private final Color overlayFoliageColor = new Color().premultiplied();
    private final Color overlayGrassColor = new Color().premultiplied();

    public Biome(String id) {
        //resolve namespace
        String namespace = "minecraft";
        int namespaceSeperator = id.indexOf(':');
        if (namespaceSeperator > 0) {
            namespace = id.substring(0, namespaceSeperator);
            id = id.substring(namespaceSeperator + 1);
        }

        this.id = id;
        this.namespace = namespace;
        this.fullId = namespace + ":" + id;
    }

    public Biome(String id, float humidity, float temp, Color waterColor) {
        this(id);
        this.humidity = humidity;
        this.temp = temp;
        this.waterColor.set(waterColor);
    }

    public Biome(String id, float humidity, float temp, Color waterColor, Color overlayFoliageColor, Color overlayGrassColor) {
        this (id, humidity, temp, waterColor);
        this.overlayFoliageColor.set(overlayFoliageColor);
        this.overlayGrassColor.set(overlayGrassColor);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getId() {
        return id;
    }

    public String getFullId() {
        return fullId;
    }

    public float getHumidity() {
        return humidity;
    }

    public float getTemp() {
        return temp;
    }

    public Color getWaterColor() {
        return waterColor;
    }

    public Color getOverlayFoliageColor() {
        return overlayFoliageColor;
    }

    public Color getOverlayGrassColor() {
        return overlayGrassColor;
    }

    public static Biome create(String id, ConfigurationNode node) {
        Biome biome = new Biome(id);
        biome.humidity = (float) node.node("humidity").getDouble(biome.humidity);
        biome.temp = (float) node.node("temp").getDouble(biome.temp);
        try { biome.waterColor.set(ConfigUtils.readColorInt(node.node("watercolor"))).premultiplied(); 				} catch (NumberFormatException ignored) {}
        try { biome.overlayFoliageColor.set(ConfigUtils.readColorInt(node.node("foliagecolor"))).premultiplied(); 	} catch (NumberFormatException ignored) {}
        try { biome.overlayGrassColor.set(ConfigUtils.readColorInt(node.node("grasscolor"))).premultiplied(); 		} catch (NumberFormatException ignored) {}

        return biome;
    }

    @Override
    public String toString() {
        return "Biome{" +
               "id='" + id + '\'' +
               ", namespace=" + namespace +
               ", fullId=" + fullId +
               ", humidity=" + humidity +
               ", temp=" + temp +
               ", waterColor=" + waterColor +
               ", overlayFoliageColor=" + overlayFoliageColor +
               ", overlayGrassColor=" + overlayGrassColor +
               '}';
    }

}
