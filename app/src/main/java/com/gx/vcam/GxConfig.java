package com.gx.vcam;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;

public class GxConfig {

    public static final String DIR  = "/sdcard/DCIM/GX/";
    public static final String FILE = DIR + "config.json";

    public static File dir() {
        File d = new File(DIR);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static void write(JSONObject o) {
        try {
            dir();
            FileOutputStream out = new FileOutputStream(FILE);
            out.write(o.toString(2).getBytes());
            out.close();
        } catch (Exception e) { }
    }

    public static JSONObject read() {
        try {
            File f = new File(FILE);
            if (!f.exists()) return new JSONObject();
            BufferedReader r = new BufferedReader(new FileReader(f));
            StringBuilder s = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) s.append(l);
            r.close();
            return new JSONObject(s.toString());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static JSONObject defaults() {
        try {
            JSONObject o = new JSONObject();
            o.put("enabled", true);
            o.put("slot", 0);
            o.put("zoom", 1.0);
            o.put("panX", 0);
            o.put("panY", 0);
            o.put("rotation", 0);
            o.put("mirror", false);
            o.put("sound", false);
            o.put("hide", true);
            o.put("spoofMeta", true);
            o.put("bypassRoot", true);
            o.put("bypassEmu", true);
            o.put("fakeExif", true);
            return o;
        } catch (Exception e) { return new JSONObject(); }
    }
              }
