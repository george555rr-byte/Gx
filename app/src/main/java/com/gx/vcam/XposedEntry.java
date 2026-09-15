package com.gx.vcam;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedEntry implements IXposedHookLoadPackage {
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        try {
            if (!"com.gx.vcam".equals(lp.packageName))
                new HookMain().handleLoadPackage(lp);
        } catch (Throwable t) { GxLog.w("GX", "entry " + t); }
    }
}
