package com.gx.vcam;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GxLog {
    public static final String FILE = "/sdcard/DCIM/GX/hook.log";

    public static void w(String tag, String msg) {
        try {
            File f = new File(FILE);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            FileWriter fw = new FileWriter(f, true);
            String t = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            fw.write("[" + t + "] " + tag + " :: " + msg + "\n");
            fw.close();
        } catch (Throwable ignored) { }
    }

    public static void clear() {
        try { File f = new File(FILE); if (f.exists()) f.delete(); } catch (Throwable ignored) { }
    }
}
