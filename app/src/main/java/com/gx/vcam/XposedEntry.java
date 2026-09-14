package com.gx.vcam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;

import org.json.JSONObject;

import java.io.File;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedEntry implements IXposedHookLoadPackage {

    // حالة مسار الـCallback (Camera1)
    private static Object sCallback = null;
    private static byte[] sFrame    = null;
    private static int    sW = 0, sH = 0;
    private static String sLoadedPath = null;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        try {
            if ("com.gx.vcam".equals(lp.packageName)) return;

            GxLog.w("GX", "=== hooked: " + lp.packageName + " ===");

            // ── تركيب هوكات Camera1 + Camera2 ──
            HookCam1.install(lp.classLoader);
            HookCam2.install(lp.classLoader);

            // ── مسار الـCallback (للتطبيقات القديمة) ──
            Class<?> cam = XposedHelpers.findClass("android.hardware.Camera", lp.classLoader);

            XposedBridge.hookAllMethods(cam, "setPreviewCallback", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length > 0) { sCallback = p.args[0]; GxLog.w("CAM1", "cb set"); }
                }
            });
            XposedBridge.hookAllMethods(cam, "setPreviewCallbackWithBuffer", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length > 0) { sCallback = p.args[0]; GxLog.w("CAM1", "cb+buf set"); }
                }
            });
            XposedBridge.hookAllMethods(cam, "setOneShotPreviewCallback", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length > 0) { sCallback = p.args[0]; }
                }
            });

            XposedBridge.hookAllMethods(cam, "addCallbackBuffer", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (!GxConfig.read().optBoolean("enabled", true)) return;
                        Object a0 = p.args[0];
                        if (!(a0 instanceof byte[])) return;
                        byte[] buf = (byte[]) a0;
                        if (sFrame == null) buildFrame();
                        if (sFrame == null) return;
                        int n = Math.min(buf.length, sFrame.length);
                        System.arraycopy(sFrame, 0, buf, 0, n);
                        deliver(buf);
                        p.setResult(null);
                    } catch (Throwable t) { GxLog.w("CAM1", "addBuf " + t); }
                }
            });

            Class<?> params = XposedHelpers.findClass("android.hardware.Camera$Parameters", lp.classLoader);
            XposedBridge.hookAllMethods(params, "setPreviewSize", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        int w = (Integer) p.args[0];
                        int h = (Integer) p.args[1];
                        if (w != sW || h != sH) {
                            sW = w; sH = h; sFrame = null;
                            GxLog.w("CAM1", "previewSize " + w + "x" + h);
                        }
                    } catch (Throwable t) { }
                }
            });

        } catch (Throwable t) {
            GxLog.w("GX", "FATAL: " + t);
        }
    }

    // ── بناء إطار NV21 (مسار الـCallback) ──
    private static void buildFrame() {
        try {
            JSONObject c = GxConfig.read();
            int slot = c.optInt("slot", 0);
            JSONObject s = GxStore.getSlot(slot);
            if (s == null) return;
            String path = s.optString("path", "");
            if (path.equals(sLoadedPath) && sFrame != null) return;
            File f = new File(path);
            if (!f.exists()) return;

            Bitmap src = BitmapFactory.decodeFile(path);
            if (src == null) return;

            int w = (sW > 0) ? sW : src.getWidth();
            int h = (sH > 0) ? sH : src.getHeight();

            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            cv.drawColor(0xFF000000);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            Rect dst = new Rect(0, 0, w, h);
            cv.drawBitmap(src, null, dst, paint);

            if (c.optBoolean("mirror", false)) {
                Matrix m = new Matrix();
                m.postScale(-1, 1, w / 2f, h / 2f);
                Bitmap flip = Bitmap.createBitmap(out, 0, 0, w, h, m, true);
                out.recycle();
                out = flip;
            }
            src.recycle();

            sFrame = Nv21.fromBitmap(out);
            sLoadedPath = path;
            GxLog.w("CAM1", "nv21 built " + w + "x" + h);
        } catch (Throwable t) { GxLog.w("CAM1", "buildFrame " + t); }
    }

    private static void deliver(byte[] buf) {
        try {
            Object cb = sCallback;
            if (cb == null) return;
            cb.getClass()
              .getMethod("onPreviewFrame", byte[].class, Camera.class)
              .invoke(cb, buf, null);
        } catch (Throwable t) { GxLog.w("CAM1", "deliver " + t); }
    }
}
