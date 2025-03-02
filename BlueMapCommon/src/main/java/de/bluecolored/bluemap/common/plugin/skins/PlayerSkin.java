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
package de.bluecolored.bluemap.common.plugin.skins;

import de.bluecolored.bluemap.core.logger.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.*;

public class PlayerSkin {

    private final UUID uuid;
    private long lastUpdate;

    public PlayerSkin(UUID uuid) {
        this.uuid = uuid;
        this.lastUpdate = -1;
    }

    public void update(File storageFolder, File fallback) {
        long now = System.currentTimeMillis();
        if (lastUpdate > 0 && lastUpdate + 600000 > now) return; // only update if skin is older than 10 minutes

        lastUpdate = now;

        new Thread(() -> {
            BufferedImage head = null;

            try {
                Future<BufferedImage> futureSkin = loadSkin();
                BufferedImage skin = futureSkin.get(10, TimeUnit.SECONDS);
                head = createHead(skin);
            } catch (ExecutionException | TimeoutException e) {
                Logger.global.logDebug("Failed to load player-skin from mojang-servers: " + e);
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                if (head == null) head = ImageIO.read(fallback);
                ImageIO.write(head, "png", new File(storageFolder, uuid.toString() + ".png"));
            } catch (IOException e) {
                Logger.global.logError("Failed to write player-head image!", e);
            }
        }).start();
    }

    public BufferedImage createHead(BufferedImage skinTexture) {
        BufferedImage head = new BufferedImage(8, 8, skinTexture.getType());

        BufferedImage layer1 = skinTexture.getSubimage(8, 8, 8, 8);
        BufferedImage layer2 = skinTexture.getSubimage(40, 8, 8, 8);

        try {
            Graphics2D g = head.createGraphics();
            g.drawImage(layer1, 0, 0, null);
            g.drawImage(layer2, 0, 0, null);
        } catch (Throwable t) { // There might be problems with headless servers when loading the graphics class, so we catch every exception and error on purpose here
            Logger.global.noFloodWarning("headless-graphics-fail",
                    "Could not access Graphics2D to render player-skin texture. Try adding '-Djava.awt.headless=true' to your startup flags or ignore this warning.");

            layer1.copyData(head.getRaster());
        }

        return head;
    }

    public Future<BufferedImage> loadSkin() {
        CompletableFuture<BufferedImage> image = new CompletableFuture<>();

        new Thread(() -> {
            try {
                URL imageUrl = new URL("https://api.deltacraft.eu/embed/player-head/" + this.uuid.toString());
                image.complete(ImageIO.read(imageUrl));
            } catch (IOException e) {
                image.completeExceptionally(e);
            }
        }).start();

        return image;
    }
}
