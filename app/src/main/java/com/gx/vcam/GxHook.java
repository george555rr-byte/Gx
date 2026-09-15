package com.gx.vcam;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;

import org.json.JSONObject;
import java.io.File;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class GxHook implements IXposedHookLoadPackage {
    private static MediaPlayer player;
    private static Surface surface;
    private static SurfaceTexture tex;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        try {
            if ("com.gx.vcam".equals(lp.packageName)) return;
            GxLog.w("GX", "hook " + lp.packageName);

            Class<?> cam = XposedHelpers.findClass("android.hardware.Camera", lp.classLoader);

            XposedBridge.hookAllMethods(cam, "setPreviewTexture", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (!armed() || p.args[0] == null) return;
                        tex = (SurfaceTexture) p.args[0];
                        play();
                    } catch (Throwable t) { GxLog.w("GX", "tex " + t); }
                }
            });

            XposedBridge.hookAllMethods(cam, "startPreview", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { play(); }
            });

            GxLog.w("GX", "installed");
        } catch (Throwable t) { GxLog.w("GX", "err " + t); }
    }

    private static synchronized void play() {
        try {
            String path = vpath();
            if (path == null || tex == null) return;

            if (surface != null) { surface.release(); surface = null; }
            if (player != null) { player.release(); player = null; }

            surface = new Surface(tex);
            player = new MediaPlayer();
            player.setSurface(surface);
            player.setVolume(0, 0);
            player.setLooping(true);
            player.setOnPreparedListener(MediaPlayer::start);
            player.setDataSource(path);
            player.prepareAsync();
            GxLog.w("GX", "playing " + path);
        } catch (Throwable t) { GxLog.w("GX", "play " + t); }
    }

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
}
