package com.gx.vcam;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if ("com.gx.vcam".equals(lpparam.packageName)) return;
            new HookMain().handleLoadPackage(lpparam);
        } catch (Throwable t) {
            GxLog.w("GX", "entry err: " + t);
        }
    }
}
