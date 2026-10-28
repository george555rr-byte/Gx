package com.gx.vcam;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class MainActivity extends Activity {

    private static final int REQ_VIDEO = 1;
    private static final int REQ_IMAGE = 2;

    private TextView status, statusPill, masterSub;
    private Switch masterSwitch;
    private ImageView[] thumbs = new ImageView[3];
    private TextView[] names = new TextView[3];
    private View[] slotViews = new View[3];
    private int activeSlot = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) { }
        }

        status = findViewById(R.id.status);
        statusPill = findViewById(R.id.statusPill);
        masterSub = findViewById(R.id.masterSub);
        masterSwitch = findViewById(R.id.masterSwitch);

        thumbs[0] = findViewById(R.id.slot1img);
        thumbs[1] = findViewById(R.id.slot2img);
        thumbs[2] = findViewById(R.id.slot3img);
        names[0]  = findViewById(R.id.slot1name);
        names[1]  = findViewById(R.id.slot2name);
        names[2]  = findViewById(R.id.slot3name);
        slotViews[0] = findViewById(R.id.slot1);
        slotViews[1] = findViewById(R.id.slot2);
        slotViews[2] = findViewById(R.id.slot3);

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            slotViews[i].setOnClickListener(v -> { 
                activeSlot = idx; 
                saveActive(); 
                syncVideoToEngine(); 
                refresh(); 
            });
        }

        findViewById(R.id.pickVideo).setOnClickListener(v -> pick(true));
        findViewById(R.id.pickImage).setOnClickListener(v -> pick(false));

        masterSwitch.setOnCheckedChangeListener((v, checked) -> {
            try { JSONObject c = GxConfig.read(); c.put("enabled", checked); GxConfig.write(c); } catch (Exception e) { }
            refresh();
        });

        findViewById(R.id.startBtn).setOnClickListener(v -> toggleRun());
        findViewById(R.id.overlayBtn).setOnClickListener(v -> overlay());
        findViewById(R.id.settingsBtn).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        if (!GxConfig.read().has("enabled")) GxConfig.write(GxConfig.defaults());
        activeSlot = GxConfig.read().optInt("slot", 0);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void pick(boolean video) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType(video ? "video/*" : "image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, video ? REQ_VIDEO : REQ_IMAGE);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        boolean isVideo = (req == REQ_VIDEO);
        String name = uri.getLastPathSegment();
        String path = GxStore.importMedia(this, activeSlot, uri, isVideo);
        if (path != null) {
            GxStore.setSlot(activeSlot, isVideo ? "video" : "image", path, name);
            status.setText("Slot " + (activeSlot + 1) + " → " + name);
            syncVideoToEngine();
        } else {
            status.setText("Import failed");
        }
        refresh();
    }

    private void saveActive() {
        try { JSONObject c = GxConfig.read(); c.put("slot", activeSlot); GxConfig.write(c); } catch (Exception e) { }
    }

    private void toggleRun() {
        try {
            JSONObject c = GxConfig.read();
            boolean en = c.optBoolean("enabled", true);
            
            if (!en) {
                // تشغيل
                c.put("enabled", true);
                c.put("slot", activeSlot);
                GxConfig.write(c);
                
                // طلب الروت وتشغيل المحرك
                RootEngine.start(this);
                syncVideoToEngine();
                
                status.setText("Injection ARMED · slot " + (activeSlot + 1));
            } else {
                // إيقاف
                c.put("enabled", false);
                GxConfig.write(c);
                
                RootEngine.stop();
                status.setText("Injection DISARMED");
            }
        } catch (Exception e) { }
        refresh();
    }

    private void syncVideoToEngine() {
        try {
            JSONObject s = GxStore.getSlot(activeSlot);
            if (s != null && "video".equals(s.optString("type"))) {
                String srcPath = s.optString("path");
                File src = new File(srcPath);
                
                File moviesDir = new File("/sdcard/Movies");
                if (!moviesDir.exists()) moviesDir.mkdirs();
                
                File dst = new File("/sdcard/Movies/1.mp4");
                
                FileInputStream in = new FileInputStream(src);
                FileOutputStream out = new FileOutputStream(dst);
                byte[] buf = new byte[8192]; int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                in.close(); out.close();
                
                GxLog.w("SYNC", "Video copied to /sdcard/Movies/1.mp4");
            }
        } catch (Exception e) {
            GxLog.w("SYNC", "sync err: " + e);
        }
    }

    private void overlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        startService(new Intent(this, GxOverlayService.class));
        Toast.makeText(this, "GX overlay ON", Toast.LENGTH_SHORT).show();
    }

    private void refresh() {
        boolean en;
        try { en = GxConfig.read().optBoolean("enabled", true); } catch (Exception e) { en = true; }
        masterSwitch.setChecked(en);
        masterSub.setText(en ? "Active · slot " + (activeSlot + 1) : "Inactive");

        if (en) {
            statusPill.setText("ARMED");
            statusPill.setTextColor(getResources().getColor(R.color.gx_green));
            statusPill.setBackgroundResource(R.drawable.pill_on);
        } else {
            statusPill.setText("STANDBY");
            statusPill.setTextColor(getResources().getColor(R.color.gx_muted));
            statusPill.setBackgroundResource(R.drawable.pill_off);
        }

        for (int i = 0; i < 3; i++) {
            JSONObject s = GxStore.getSlot(i);
            boolean has = (s != null);
            String nm = has ? s.optString("name", "SLOT " + (i + 1)) : "SLOT " + (i + 1);
            names[i].setText(nm.length() > 12 ? nm.substring(0, 12) + "…" : nm);
            names[i].setTextColor(getResources().getColor(
                    i == activeSlot ? R.color.gx_purple : R.color.gx_muted));
            if (has) {
                Bitmap bm = loadThumb(s.optString("path", ""), "video".equals(s.optString("type")));
                if (bm != null) thumbs[i].setImageBitmap(bm);
            }
        }
    }

    private Bitmap loadThumb(String path, boolean isVideo) {
        try {
            if (isVideo) return ThumbnailUtils.createVideoThumbnail(path,
                    android.provider.MediaStore.Video.Thumbnails.MINI_KIND);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;
            return BitmapFactory.decodeFile(path, o);
        } catch (Exception e) { return null; }
    }
}
