package com.gx.vcam;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Switch;

import org.json.JSONObject;

public class SettingsActivity extends Activity {

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        int[] rid = {
                R.id.swHide, R.id.swSpoof, R.id.swExif,
                R.id.swRoot, R.id.swEmu, R.id.swSound,
                R.id.swMirror, R.id.swAutoPos
        };
        String[] keys = {
                "hide", "spoofMeta", "fakeExif",
                "bypassRoot", "bypassEmu", "sound",
                "mirror", "autoPos"
        };

        for (int i = 0; i < rid.length; i++) {
            final String key = keys[i];
            Switch sw = findViewById(rid[i]);
            try { sw.setChecked(GxConfig.read().optBoolean(key, true)); } catch (Exception e) {}
            sw.setOnCheckedChangeListener((v, checked) -> {
                try {
                    JSONObject c = GxConfig.read();
                    c.put(key, checked);
                    GxConfig.write(c);
                } catch (Exception e) { }
            });
        }
    }
}
