package com.gx.vcam;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;

public class GxStore {

    public static final String DIR   = "/sdcard/DCIM/GX/";
    public static final String SLOTS = DIR + "slots/";
    public static final String FILE  = DIR + "slots.json";

    public static File dir() {
        File d = new File(DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File slotsDir() {
        File d = new File(SLOTS);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static JSONObject load() {
        try {
            File f = new File(FILE);
            if (!f.exists()) return new JSONObject();
            BufferedReader r = new BufferedReader(new FileReader(f));
            StringBuilder s = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) s.append(l);
            r.close();
            return new JSONObject(s.toString());
        } catch (Exception e) { return new JSONObject(); }
    }

    public static void save(JSONObject o) {
        try {
            dir();
            FileOutputStream out = new FileOutputStream(FILE);
            out.write(o.toString(2).getBytes());
            out.close();
        } catch (Exception e) { }
    }

    /** ينسخ الوسيط المختار إلى مجلد GX ويرجّع المسار الحقيقي */
    public static String importMedia(Context ctx, int slot, Uri uri, boolean isVideo) {
        try {
            slotsDir();
            String ext = isVideo ? ".mp4" : ".jpg";
            File dst = new File(slotsDir(), "slot" + slot + ext);
            InputStream in = ctx.getContentResolver().openInputStream(uri);
            FileOutputStream out = new FileOutputStream(dst);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            return dst.getAbsolutePath();
        } catch (Exception e) { return null; }
    }

    public static void setSlot(int slot, String type, String path, String name) {
        try {
            JSONObject all = load();
            JSONObject s = new JSONObject();
            s.put("type", type);
            s.put("path", path);
            s.put("name", name);
            all.put("slot" + slot, s);
            save(all);
        } catch (Exception e) { }
    }

    public static JSONObject getSlot(int slot) {
        try { return load().optJSONObject("slot" + slot); }
        catch (Exception e) { return null; }
    }

    public static void clearSlot(int slot) {
        try {
            JSONObject all = load();
            all.remove("slot" + slot);
            save(all);
        } catch (Exception e) { }
    }
}
