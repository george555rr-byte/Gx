package com.gx.vcam;

import android.content.Context;
import java.util.Random;

public class RootEngine {

    // تشغيل الديمون (محرك الكاميرا)
    public static void start(Context ctx) {
        try {
            // مسار المكتبات الأصلية داخل التطبيق
            String pkgPath = ctx.getApplicationInfo().nativeLibraryDir;
            
            // اسم الخدمة العشوائي
            String name = "gx_" + new Random().nextInt(10000);

            // الأوامر اللي شفناها في التطبيق الصيني
            String cmd = 
                "killall vcplax 2>/dev/null; " +
                "cp " + pkgPath + "/libvc.so /data/libvc.so; " +
                "cp " + pkgPath + "/libshadowhook.so /data/libvc++.so; " +
                "cp " + pkgPath + "/vcplax.so /data/vcplax; " +
                "chmod 700 /data/vcplax; " +
                "/data/vcplax " + name + " &";

            // تنفيذ الأوامر بصلاحية الروت
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            p.waitFor();
            
            GxLog.w("ROOT", "Engine started successfully: " + name);
            
        } catch (Throwable t) {
            GxLog.w("ROOT", "Failed to start engine: " + t);
        }
    }

    // إيقاف الديمون
    public static void stop() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "killall vcplax"});
            p.waitFor();
            GxLog.w("ROOT", "Engine stopped");
        } catch (Exception e) { }
    }
}
