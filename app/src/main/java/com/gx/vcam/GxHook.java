package com.gx.vcam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class GxHook implements IXposedHookLoadPackage {

    private static MediaPlayer player;
    private static Surface texSurface, holderSurface, c2Surface;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        try {
            if ("com.gx.vcam".equals(lp.packageName)) return;
            GxLog.w("GX", "hook " + lp.packageName);
            hookCam1(lp.classLoader);
            hookCam2(lp.classLoader);
        } catch (Throwable t) { GxLog.w("GX", "err " + t); }
    }

    // ═══ Camera1 ═══
    private void hookCam1(ClassLoader cl) {
        Class<?> cam = XposedHelpers.findClass("android.hardware.Camera", cl);

        XposedBridge.hookAllMethods(cam, "setPreviewTexture", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!armed() || vpath() == null) return;
                Object a = p.args[0];
                if (!(a instanceof SurfaceTexture)) return;
                texSurface = new Surface((SurfaceTexture) a);
                p.args[0] = new SurfaceTexture(10);
                GxLog.w("GX", "C1 texture captured");
            }
        });

        XposedBridge.hookAllMethods(cam, "setPreviewDisplay", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!armed() || vpath() == null) return;
                Object a = p.args[0];
                if (!(a instanceof SurfaceHolder)) return;
                holderSurface = ((SurfaceHolder) a).getSurface();
                p.setResult(null);
                GxLog.w("GX", "C1 holder captured");
            }
        });

        XposedBridge.hookAllMethods(cam, "startPreview", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                play((texSurface != null) ? texSurface : holderSurface, vpath());
            }
        });

        XposedBridge.hookAllMethods(cam, "takePicture", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!armed() || ipath() == null) return;
                for (Object a : p.args)
                    if (a instanceof Camera.PictureCallback) hookPicture((Camera.PictureCallback) a);
            }
        });
    }

    // ═══ Camera2 ═══
    private void hookCam2(ClassLoader cl) {
        Class<?> builder = XposedHelpers.findClass(
                "android.hardware.camera2.CaptureRequest$Builder", cl);

        XposedBridge.hookAllMethods(builder, "addTarget", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!armed() || vpath() == null) return;
                Object a = p.args[0];
                if (!(a instanceof Surface)) return;
                if (a.toString().contains("Surface(name=null)")) return;
                c2Surface = (Surface) a;
                p.args[0] = dummy();
                GxLog.w("GX", "C2 target captured");
            }
        });

        XposedBridge.hookAllMethods(builder, "build", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                play(c2Surface, vpath());
            }
        });
    }

    // ═══ الفيديو ═══
    private static synchronized void play(Surface s, String path) {
        if (s == null || path == null) return;
        try {
            if (player != null) { player.release(); player = null; }
            player = new MediaPlayer();
            player.setSurface(s);
            player.setVolume(0, 0);
            player.setLooping(true);
            player.setOnPreparedListener(MediaPlayer::start);
            player.setDataSource(path);
            player.prepare();
            GxLog.w("GX", "playing " + path);
        } catch (Throwable t) { GxLog.w("GX", "play err " + t); }
    }

    // ═══ الصورة ═══
    private void hookPicture(Camera.PictureCallback cb) {
        try {
            XposedHelpers.findAndHookMethod(cb.getClass(), "onPictureTaken",
                    byte[].class, Camera.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Bitmap b = BitmapFactory.decodeFile(ipath());
                        if (b == null) return;
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        b.compress(Bitmap.CompressFormat.JPEG, 95, out);
                        p.args[0] = out.toByteArray();
                        GxLog.w("GX", "photo injected");
                    } catch (Throwable t) { }
                }
            });
        } catch (Throwable t) { }
    }

    // ═══ مساعدات ═══
    private static boolean armed() {
        try { return GxConfig.read().optBoolean("enabled", true); } catch (Throwable t) { return false; }
    }
    private static String vpath() {
        try {
            JSONObject o = GxStore.getSlot(GxConfig.read().optInt("slot", 0));
            if (o == null || !"video".equals(o.optString("type"))) return null;
            String p = o.optString("path", null);
            return (p != null && new File(p).exists()) ? p : null;
        } catch (Throwable t) { return null; }
    }
    private static String ipath() {
        try {
            JSONObject o = GxStore.getSlot(GxConfig.read().optInt("slot", 0));
            if (o == null || !"image".equals(o.optString("type"))) return null;
            String p = o.optString("path", null);
            return (p != null && new File(p).exists()) ? p : null;
        } catch (Throwable t) { return null; }
    }
    private static Surface dummy() { return new Surface(new SurfaceTexture(10)); }
}
