package com.gx.vcam;

import android.content.Context;
import java.util.Random;

public class RootEngine {

    public static void start(Context ctx) {
        try {
            String pkgPath = ctx.getApplicationInfo().nativeLibraryDir;
            String name = "gx_" + new Random().nextInt(10000);

            String cmd = 
                "killall vcplax 2>/dev/null; " +
                "cp " + pkgPath + "/libvc.so /data/libvc.so; " +
                "cp " + pkgPath + "/libshadowhook.so /data/libvc++.so; " +
                "cp " + pkgPath + "/vcplax.so /data/vcplax; " +
                "chmod 700 /data/vcplax; " +
                "/data/vcplax " + name + " &";

            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            p.waitFor();
            GxLog.w("ROOT", "SUCCESS: Engine started " + name);
            
        } catch (Throwable t) {
            GxLog.w("ROOT", "ERROR: " + t.getMessage());
        }
    }

    public static void stop() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "killall vcplax"});
            p.waitFor();
            GxLog.w("ROOT", "SUCCESS: Engine stopped");
        } catch (Exception e) {
            GxLog.w("ROOT", "ERROR: stop " + e.getMessage());
        }
    }
}
