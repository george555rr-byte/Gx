package com.gx.vcam;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_VIDEO = 1;
    private static final int REQ_IMAGE = 2;
    private TextView status;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = (TextView) findViewById(R.id.status);

        findViewById(R.id.pickVideo).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("video/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, REQ_VIDEO);
        });
        findViewById(R.id.pickImage).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, REQ_IMAGE);
        });
        findViewById(R.id.overlayBtn).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else {
                startService(new Intent(this, GxOverlayService.class));
                Toast.makeText(this, "GX overlay ON", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        String name = uri.getLastPathSegment();
        if (req == REQ_VIDEO) status.setText("VIDEO: " + name);
        if (req == REQ_IMAGE) status.setText("IMAGE: " + name);
    }
}
