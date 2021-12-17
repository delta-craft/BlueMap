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
package de.bluecolored.bluemap.core.map.lowres;

import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3f;
import de.bluecolored.bluemap.core.logger.Logger;
import de.bluecolored.bluemap.core.map.hires.HiresTileMeta;
import de.bluecolored.bluemap.core.storage.Storage;
import de.bluecolored.bluemap.core.threejs.BufferGeometry;
import de.bluecolored.bluemap.core.util.math.Color;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class LowresModelManager {

    private final Storage.TileStorage storage;
    private final Vector2i pointsPerLowresTile;
    private final Vector2i pointsPerHiresTile;

    private final Map<Vector2i, CachedModel> models;

    public LowresModelManager(Storage.TileStorage storage, Vector2i pointsPerLowresTile, Vector2i pointsPerHiresTile) {
        this.storage = storage;

        this.pointsPerLowresTile = pointsPerLowresTile;
        this.pointsPerHiresTile = pointsPerHiresTile;

        models = new ConcurrentHashMap<>();
    }

    /**
     * Renders all points from the given hires-model onto the lowres-grid
     */
    public void render(HiresTileMeta tileMeta) {
        Vector2i blocksPerPoint = new Vector2i(
                tileMeta.getSizeX() / pointsPerHiresTile.getX(),
                tileMeta.getSizeZ() / pointsPerHiresTile.getY()
        );

        Vector2i pointMin = new Vector2i(
                Math.floorDiv(tileMeta.getMinX(), blocksPerPoint.getX()),
                Math.floorDiv(tileMeta.getMinZ(), blocksPerPoint.getY())
        );

        Color
                pointColor = new Color(),
                columnColor = new Color();

        for (int tx = 0; tx < pointsPerHiresTile.getX(); tx++){
            for (int tz = 0; tz < pointsPerHiresTile.getY(); tz++){

                double height = 0;
                pointColor.set(0, 0, 0, 0, true);

                for (int x = 0; x < blocksPerPoint.getX(); x++){
                    for (int z = 0; z < blocksPerPoint.getY(); z++){

                        int rx = tx * blocksPerPoint.getX() + x + tileMeta.getMinX();
                        int rz = tz * blocksPerPoint.getY() + z + tileMeta.getMinZ();
                        height += tileMeta.getHeight(rx, rz);

                        tileMeta.getColor(rx, rz, columnColor).premultiplied();
                        pointColor.add(columnColor);
                    }
                }

                pointColor.flatten().straight();

                int count = blocksPerPoint.getX() * blocksPerPoint.getY();
                height /= count;

                update(pointMin.getX() + tx, pointMin.getY() + tz, (float) height, pointColor);
            }
        }
    }

    /**
     * Saves all unsaved changes to the models to disk
     */
    public synchronized void save(){
        for (Entry<Vector2i, CachedModel> entry : models.entrySet()){
            saveModel(entry.getKey(), entry.getValue());
        }

        tidyUpModelCache();
    }

    /**
     * Updates a point on the lowres-model-grid
     */
    public void update(int px, int pz, float height, Color color) {
        if (color.premultiplied) throw new IllegalArgumentException("Color can not be premultiplied!");

        Vector2i point = new Vector2i(px, pz);
        Vector3f colorV = new Vector3f(color.r, color.g, color.b);

        Vector2i tile = pointToTile(point);
        Vector2i relPoint = getPointRelativeToTile(tile, point);
        LowresModel model = getModel(tile);
        model.update(relPoint, height, colorV);

        if (relPoint.getX() == 0){
            Vector2i tile2 = tile.add(-1, 0);
            Vector2i relPoint2 = getPointRelativeToTile(tile2, point);
            LowresModel model2 = getModel(tile2);
            model2.update(relPoint2, height, colorV);
        }

        if (relPoint.getY() == 0){
            Vector2i tile2 = tile.add(0, -1);
            Vector2i relPoint2 = getPointRelativeToTile(tile2, point);
            LowresModel model2 = getModel(tile2);
            model2.update(relPoint2, height, colorV);
        }

        if (relPoint.getX() == 0 && relPoint.getY() == 0){
            Vector2i tile2 = tile.add(-1, -1);
            Vector2i relPoint2 = getPointRelativeToTile(tile2, point);
            LowresModel model2 = getModel(tile2);
            model2.update(relPoint2, height, colorV);
        }
    }

    private LowresModel getModel(Vector2i tile) {

        CachedModel model = models.get(tile);

        if (model == null){
            synchronized (this) {
                model = models.get(tile);
                if (model == null){

                    try {
                        Optional<InputStream> optIs = storage.read(tile);
                        if (optIs.isPresent()){
                            try (InputStream is = optIs.get()) {
                                String json = IOUtils.toString(is, StandardCharsets.UTF_8);

                                model = new CachedModel(BufferGeometry.fromJson(json));
                            }
                        }
                    } catch (IllegalArgumentException | IOException ex){
                        Logger.global.logWarning("Failed to load lowres model '" + tile + "': " + ex);

                        try {
                            storage.delete(tile);
                        } catch (IOException ex2) {
                            Logger.global.logError("Failed to delete lowres-file: " + tile, ex2);
                        }
                    }

                    if (model == null){
                        model = new CachedModel(pointsPerLowresTile);
                    }

                    models.put(tile, model);

                    tidyUpModelCache();
                }
            }
        }

        return model;
    }

    /**
     * This Method tidies up the model cache:<br>
     * it saves all modified models that have not been saved for 2 minutes and<br>
     * saves and removes the oldest models from the cache until the cache size is 10 or less.<br>
     * <br>
     * This method gets automatically called if the cache grows, but if you want to ensure model will be saved after 2 minutes, you could e.g call this method every second.<br>
     */
    public synchronized void tidyUpModelCache() {
        List<Entry<Vector2i, CachedModel>> entries = new ArrayList<>(models.size());
        entries.addAll(models.entrySet());
        entries.sort((e1, e2) -> (int) Math.signum(e1.getValue().cacheTime - e2.getValue().cacheTime));

        int size = entries.size();
        for (Entry<Vector2i, CachedModel> e : entries) {
            if (size > 10) {
                saveAndRemoveModel(e.getKey(), e.getValue());
                continue;
            }

            if (e.getValue().getCacheTime() > 120000) {
                saveModel(e.getKey(), e.getValue());
            }
        }
    }

    private synchronized void saveAndRemoveModel(Vector2i tile, CachedModel model) {
        models.remove(tile);
        try {
            model.save(storage, tile,false);
            //logger.logDebug("Saved and unloaded lowres tile: " + model.getTile());
        } catch (IOException ex) {
            Logger.global.logError("Failed to save and unload lowres-model: " + tile, ex);
        }
    }

    private void saveModel(Vector2i tile, CachedModel model) {
        try {
            model.save(storage, tile, false);
            //logger.logDebug("Saved lowres tile: " + model.getTile());
        } catch (IOException ex) {
            Logger.global.logError("Failed to save lowres-model: " + tile, ex);
        }

        model.resetCacheTime();
    }

    private Vector2i pointToTile(Vector2i point){
        return new Vector2i(
                Math.floorDiv(point.getX(), pointsPerLowresTile.getX()),
                Math.floorDiv(point.getY(), pointsPerLowresTile.getY())
        );
    }

    private Vector2i getPointRelativeToTile(Vector2i tile, Vector2i point){
        return new Vector2i(
                point.getX() - tile.getX() * pointsPerLowresTile.getX(),
                point.getY() - tile.getY() * pointsPerLowresTile.getY()
        );
    }

    public Vector2i getTileSize() {
        return pointsPerLowresTile;
    }

    public Vector2i getPointsPerHiresTile() {
        return pointsPerHiresTile;
    }

    private static class CachedModel extends LowresModel {

        private long cacheTime;

        public CachedModel(BufferGeometry model) {
            super(model);

            cacheTime = System.currentTimeMillis();
        }

        public CachedModel(Vector2i gridSize) {
            super(gridSize);

            cacheTime = System.currentTimeMillis();
        }

        public long getCacheTime() {
            return System.currentTimeMillis() - cacheTime;
        }

        public void resetCacheTime() {
            cacheTime = System.currentTimeMillis();
        }

    }

}
