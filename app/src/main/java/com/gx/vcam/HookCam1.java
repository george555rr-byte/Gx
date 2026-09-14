package com.gx.vcam;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.SurfaceHolder;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HookCam1 {

    private static Thread canvasThread;
    private static volatile boolean canvasRun = false;
    private static SurfaceHolder holder;

    public static void install(ClassLoader cl) {
        try {
            Class<?> cam = XposedHelpers.findClass("android.hardware.Camera", cl);

            // ١) SurfaceTexture → GxEngine (OpenGL)
            XposedBridge.hookAllMethods(cam, "setPreviewTexture", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (!GxConfig.read().optBoolean("enabled", true)) return;
                        Object a0 = p.args[0];
                        if (!(a0 instanceof SurfaceTexture)) return;

                        SurfaceTexture appTex = (SurfaceTexture) a0;
                        Surface s = new Surface(appTex);
                        GxEngine.get().start(s);

                        p.args[0] = new SurfaceTexture(10); // dummy للكاميرا الحقيقية
                        GxLog.w("CAM1", "setPreviewTexture → GL engine");
                    } catch (Throwable t) { GxLog.w("CAM1", "tex err " + t); }
                }
            });

            // ٢) SurfaceHolder → Canvas
            XposedBridge.hookAllMethods(cam, "setPreviewDisplay", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (!GxConfig.read().optBoolean("enabled", true)) return;
                        Object a0 = p.args[0];
                        if (!(a0 instanceof SurfaceHolder)) return;

                        holder = (SurfaceHolder) a0;
                        startCanvas();
                        p.setResult(null);
                        GxLog.w("CAM1", "setPreviewDisplay → canvas");
                    } catch (Throwable t) { GxLog.w("CAM1", "disp err " + t); }
                }
            });

            GxLog.w("CAM1", "installed");

        } catch (Throwable t) { GxLog.w("CAM1", "install " + t); }
    }

    private static void startCanvas() {
        if (canvasRun) return;
        canvasRun = true;
        canvasThread = new Thread(() -> {
            while (canvasRun) {
                Canvas c = null;
                try {
                    c = holder.lockCanvas();
                    if (c != null) {
                        GxEngine.get().ensureFrame();
                        Bitmap f = GxEngine.get().frameBitmap();
                        c.drawColor(0xFF000000);
                        if (f != null) {
                            Rect dst = fit(f.getWidth(), f.getHeight(), c.getWidth(), c.getHeight());
                            c.drawBitmap(f, null, dst, null);
                        }
                    }
                } catch (Throwable t) { }
                finally {
                    try { if (c != null) holder.unlockCanvasAndPost(c); } catch (Throwable t) { }
                }
                try { Thread.sleep(33); } catch (Throwable t) { }
            }
        }, "gx-canvas");
        canvasThread.start();
    }

    private static Rect fit(int sw, int sh, int dw, int dh) {
        float r = Math.max((float) dw / sw, (float) dh / sh);
        int w = Math.round(sw * r), h = Math.round(sh * r);
        int l = (dw - w) / 2, t = (dh - h) / 2;
        return new Rect(l, t, l + w, t + h);
    }
                              }
