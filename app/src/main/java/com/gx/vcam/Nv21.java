package com.gx.vcam;

import android.graphics.Bitmap;

public class Nv21 {

    /** يحوّل Bitmap إلى إطارات NV21 (الصيغة اللي تفهمها كاميرا أندرويد). */
    public static byte[] fromBitmap(Bitmap bm) {
        int w = bm.getWidth();
        int h = bm.getHeight();
        int[] argb = new int[w * h];
        bm.getPixels(argb, 0, w, 0, 0, w, h);

        byte[] yuv = new byte[w * h * 3 / 2];
        int yIdx = 0;
        int uvIdx = w * h;

        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int c = argb[j * w + i];
                int r = (c >> 16) & 0xff;
                int g = (c >> 8) & 0xff;
                int b = c & 0xff;

                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                yuv[yIdx++] = (byte) (y < 0 ? 0 : (y > 255 ? 255 : y));

                if ((j & 1) == 0 && (i & 1) == 0) {
                    int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                    yuv[uvIdx++] = (byte) (v < 0 ? 0 : (v > 255 ? 255 : v)); // V
                    yuv[uvIdx++] = (byte) (u < 0 ? 0 : (u > 255 ? 255 : u)); // U
                }
            }
        }
        return yuv;
    }
}
