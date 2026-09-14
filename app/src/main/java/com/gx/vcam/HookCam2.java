package com.gx.vcam;

import android.graphics.SurfaceTexture;
import android.view.Surface;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HookCam2 {

    public static void install(ClassLoader cl) {
        try {
            Class<?> dev = XposedHelpers.findClass("android.hardware.camera2.CameraDevice", cl);

            XposedBridge.hookAllMethods(dev, "createCaptureSession", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (!GxConfig.read().optBoolean("enabled", true)) return;

                        boolean swapped = false;
                        for (int i = 0; i < p.args.length && !swapped; i++) {
                            Object a = p.args[i];
                            if (a instanceof List) {
                                List list = (List) a;
                                for (int k = 0; k < list.size(); k++) {
                                    Object o = list.get(k);
                                    if (o instanceof Surface) {
                                        Surface app = (Surface) o;
                                        GxEngine.get().start(app);
                                        list.set(k, dummy());
                                        GxLog.w("CAM2", "surface swapped (#" + k + ")");
                                        swapped = true;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        GxLog.w("CAM2", "before err " + t);
                    }
                }
            });

            GxLog.w("CAM2", "installed");

        } catch (Throwable t) {
            GxLog.w("CAM2", "install " + t);
        }
    }

    private static Surface dummy() {
        return new Surface(new SurfaceTexture(10));
    }
}
