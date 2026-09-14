package com.gx.vcam;

import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import android.widget.*;

public class GxOverlayService extends Service {
    private WindowManager wm;
    private View bubble, panel;
    private WindowManager.LayoutParams bLp, pLp;
    private boolean panelShown = false;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        startForeground(1, notif());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildBubble(); buildPanel();
    }

    private Notification notif() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("gx","GX",NotificationManager.IMPORTANCE_MIN);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
            ? new Notification.Builder(this,"gx") : new Notification.Builder(this);
        return b.setContentTitle("GX").setSmallIcon(android.R.drawable.ic_menu_camera).build();
    }

    private WindowManager.LayoutParams lp(int flags) {
        int type = (Build.VERSION.SDK_INT >= 26)
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type, flags, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = 20; p.y = 120;
        return p;
    }

    private void buildBubble() {
        TextView tv = new TextView(this);
        tv.setText("GX"); tv.setTextColor(Color.parseColor("#FFD700"));
        tv.setTextSize(16); tv.setPadding(28,28,28,28);
        tv.setBackgroundColor(Color.parseColor("#CC0D0D1A"));
        bLp = lp(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        tv.setOnTouchListener(new View.OnTouchListener() {
            float dx,dy; int ox,oy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: dx=e.getRawX(); dy=e.getRawY(); ox=bLp.x; oy=bLp.y; return true;
                    case MotionEvent.ACTION_MOVE: bLp.x=ox+(int)(e.getRawX()-dx); bLp.y=oy+(int)(e.getRawY()-dy); wm.updateViewLayout(bubble,bLp); return true;
                    case MotionEvent.ACTION_UP: if (Math.abs(e.getRawX()-dx)<8 && Math.abs(e.getRawY()-dy)<8) togglePanel(); return true;
                }
                return false;
            }
        });
        bubble = tv; wm.addView(bubble,bLp);
    }

    private Button btn(String t) {
        Button b = new Button(this);
        b.setText(t); b.setTextColor(Color.parseColor("#FFD700"));
        b.setBackgroundColor(Color.parseColor("#22000000")); b.setTextSize(11);
        return b;
    }

    private void buildPanel() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(Color.parseColor("#EE07070E"));
        c.setPadding(16,16,16,16);
        LinearLayout r1 = new LinearLayout(this); r1.addView(btn("▶")); r1.addView(btn("⏸")); r1.addView(btn("⏹"));
        LinearLayout r2 = new LinearLayout(this); r2.addView(btn("+")); r2.addView(btn("−")); r2.addView(btn("↑"));
        LinearLayout r3 = new LinearLayout(this); r3.addView(btn("↓")); r3.addView(btn("→")); r3.addView(btn("←"));
        LinearLayout r4 = new LinearLayout(this); r4.addView(btn("🖼")); r4.addView(btn("🎬")); r4.addView(btn("✕"));
        c.addView(r1); c.addView(r2); c.addView(r3); c.addView(r4);
        pLp = lp(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        panel = c;
    }

    private void togglePanel() {
        if (panelShown) { try { wm.removeView(panel); } catch (Exception e) {} panelShown=false; }
        else { pLp.x=bLp.x+70; pLp.y=bLp.y; try { wm.addView(panel,pLp); } catch (Exception e) {} panelShown=true; }
    }

    @Override public void onDestroy() {
        super.onDestroy();
        try { if (bubble!=null) wm.removeView(bubble); } catch (Exception e) {}
        try { if (panelShown) wm.removeView(panel); } catch (Exception e) {}
    }
}
