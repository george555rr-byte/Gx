package com.gx.vcam;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Random;

public class RootEngine {

    // تشغيل المحرك
    public static void start(Context ctx) {
        try {
            // 1. مسار الملفات داخل التطبيق (jniLibs)
            File filesDir = ctx.getFilesDir();
            String arch = android.os.Build.SUPPORTED_ABIS[0].contains("64") ? "arm64-v8a" : "armeabi-v7a";
            
            // 2. نسخ المكتبات لـ /data/ (عن طريق الروت)
            // ملاحظة: نحن نفترض أنك وضعت المكتبات في مجلد assets أو jniLibs
            // هنا نستخدم مسار التطبيق المثبت
            
            String pkgPath = ctx.getApplicationInfo().nativeLibraryDir;
            
            String name = "gx_" + new Random().nextInt(10000); // اسم عشوائي للخدمة
            
            String cmd = 
                "su -c 'killall vcplax' \n" +
                "su -c 'cp " + pkgPath + "/libvc.so /data/libvc.so' \n" +
                "su -c 'cp " + pkgPath + "/libshadowhook.so /data/libvc++.so' \n" +
                "su -c 'cp " + pkgPath + "/vcplax.so /data/vcplax' \n" +
                "su -c 'chmod 700 /data/vcplax' \n" +
                "su -c '/data/vcplax " + name + " &'";

            runRoot(cmd);
            GxLog.w("ROOT", "Engine started with name: " + name);
            
        } catch (Throwable t) {
            GxLog.w("ROOT", "start err: " + t);
        }
    }

    // إيقاف المحرك
    public static void stop() {
        runRoot("su -c 'killall vcplax'");
        GxLog.w("ROOT", "Engine stopped");
    }

    // دالة تنفيذ أوامر الروت
    private static void runRoot(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            p.waitFor();
        } catch (Exception e) {
            GxLog.w("ROOT", "exec err: " + e);
        }
    }
}
