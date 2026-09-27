package org.telegram.messenger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/** Java port of Nekogram's image.cpp calcCDT routine. */
public final class Utilities {
    private static final int BINS = 256;
    private static final int SEGMENTS = 4;

    private Utilities() {}

    public static void calcCDT(ByteBuffer hsvBuffer, int width, int height,
                               ByteBuffer output, ByteBuffer scratch) {
        int totalSegments = SEGMENTS * SEGMENTS;
        int tileArea = (width / SEGMENTS) * (height / SEGMENTS);
        int clipLimit = Math.max(1, (int) (1.25f * tileArea / BINS));
        float scale = 255.0f / tileArea;

        ByteBuffer hsv = hsvBuffer.duplicate();
        IntBuffer calc = scratch.duplicate().order(ByteOrder.nativeOrder()).asIntBuffer();
        int cdfsMinOffset = 0;
        int cdfsMaxOffset = totalSegments;
        int cdfsOffset = totalSegments * 2;
        int histOffset = cdfsOffset + totalSegments * BINS;
        for (int i = 0; i < totalSegments * BINS; i++) calc.put(histOffset + i, 0);

        float xMul = SEGMENTS / (float) width;
        float yMul = SEGMENTS / (float) height;
        for (int y = 0; y < height; y++) {
            int row = y * width * 4;
            for (int x = 0; x < width; x++) {
                int tile = (int) (y * yMul) * SEGMENTS + (int) (x * xMul);
                int value = hsv.get(row + x * 4 + 2) & 0xff;
                int index = histOffset + tile * BINS + value;
                calc.put(index, calc.get(index) + 1);
            }
        }

        for (int tile = 0; tile < totalSegments; tile++) {
            int clipped = 0;
            int histBase = histOffset + tile * BINS;
            int cdfBase = cdfsOffset + tile * BINS;
            for (int i = 0; i < BINS; i++) {
                int value = calc.get(histBase + i);
                if (value > clipLimit) {
                    clipped += value - clipLimit;
                    calc.put(histBase + i, clipLimit);
                }
            }
            int batch = clipped / BINS;
            int residual = clipped - batch * BINS;
            for (int i = 0; i < BINS; i++) {
                int value = calc.get(histBase + i) + batch + (i < residual ? 1 : 0);
                calc.put(histBase + i, value);
                calc.put(cdfBase + i, value);
            }
            int hMin = BINS - 1;
            for (int i = 0; i < hMin; i++) {
                if (calc.get(cdfBase + i) != 0) hMin = i;
            }
            int cdf = 0;
            for (int i = hMin; i < BINS; i++) {
                cdf += calc.get(cdfBase + i);
                calc.put(cdfBase + i, Math.min(255, (int) (cdf * scale)));
            }
            calc.put(cdfsMinOffset + tile, calc.get(cdfBase + hMin));
            calc.put(cdfsMaxOffset + tile, calc.get(cdfBase + BINS - 1));
        }

        ByteBuffer result = output.duplicate();
        for (int tile = 0; tile < totalSegments; tile++) {
            int cdfBase = cdfsOffset + tile * BINS;
            int outputBase = tile * BINS * 4;
            for (int i = 0; i < BINS; i++) {
                int index = outputBase + i * 4;
                result.put(index, (byte) calc.get(cdfBase + i));
                result.put(index + 1, (byte) calc.get(cdfsMinOffset + tile));
                result.put(index + 2, (byte) calc.get(cdfsMaxOffset + tile));
                result.put(index + 3, (byte) 255);
            }
        }
    }
}
