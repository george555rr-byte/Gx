package com.gx.vcam;

import android.app.*;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

import org.json.JSONObject;

public class GxOverlayService extends Service {

    private WindowManager wm;
    private View bubble, panel;
    private WindowManager.LayoutParams bLp, pLp;
    private boolean panelShown = false;
    private TextView zoomVal;

    private static final int PURPLE  = 0xFFB14BFF;
    private static final int MAGENTA = 0xFFFF2D95;
    private static final int CARD    = 0xF00D0D18;
    private static final int BORDER  = 0x66B14BFF;
    private static final int TEXT    = 0xFFFFFFFF;
    private static final int RIPPLE  = 0x55B14BFF;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        startForeground(1, notif());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildBubble();
        buildPanel();
    }

    // ─── helpers ───
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable round(int color, float r, int strokeC, int strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(r);
        if (strokeW > 0) g.setStroke(strokeW, strokeC);
        return g;
    }

    private GradientDrawable grad(float r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{ PURPLE, MAGENTA });
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(r);
        return g;
    }

    private Drawable pressable(float radius) {
        GradientDrawable bg   = round(0x1AFFFFFF, radius, BORDER, dp(1));
        GradientDrawable mask = round(0xFFFFFFFF, radius, 0, 0);
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), bg, mask);
    }

    private void setD(String k, double v) { try { JSONObject c = GxConfig.read(); c.put(k, v); GxConfig.write(c); } catch (Exception e) {} }
    private void setB(String k, boolean v) { try { JSONObject c = GxConfig.read(); c.put(k, v); GxConfig.write(c); } catch (Exception e) {} }
    private double getD(String k, double d) { try { return GxConfig.read().optDouble(k, d); } catch (Exception e) { return d; } }
    private boolean getB(String k, boolean d) { try { return GxConfig.read().optBoolean(k, d); } catch (Exception e) { return d; } }

    private TextView icon(String sym, View.OnClickListener c) {
        TextView v = new TextView(this);
        v.setText(sym);
        v.setTextColor(TEXT);
        v.setTextSize(16);
        v.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        v.setGravity(Gravity.CENTER);
        v.setBackground(pressable(dp(14)));
        v.setOnClickListener(c);

        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start();
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
                view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            return false;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(50), dp(50));
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        v.setLayoutParams(lp);
        return v;
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
        p.x = dp(20); p.y = dp(140);
        return p;
    }

    // ─── bubble ───
    private void buildBubble() {
        TextView tv = new TextView(this);
        tv.setText("GX");
        tv.setTextColor(TEXT);
        tv.setTextSize(15);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(grad(dp(60)));
        tv.setElevation(dp(12));

        bLp = lp(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        bLp.width = dp(60); bLp.height = dp(60);

        tv.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int ox, oy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX(); dy = e.getRawY(); ox = bLp.x; oy = bLp.y;
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90).start();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        bLp.x = ox + (int)(e.getRawX() - dx);
                        bLp.y = oy + (int)(e.getRawY() - dy);
                        wm.updateViewLayout(bubble, bLp);
                        if (panelShown) {
                            pLp.x = bLp.x + dp(70);
                            pLp.y = bLp.y;
                            try { wm.updateViewLayout(panel, pLp); } catch (Exception ex) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        if (Math.abs(e.getRawX()-dx) < 10 && Math.abs(e.getRawY()-dy) < 10) togglePanel();
                        return true;
                }
                return false;
            }
        });
        bubble = tv;
        wm.addView(bubble, bLp);
    }

    // ─── panel ───
    private void buildPanel() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.setBackground(round(CARD, dp(22), BORDER, dp(1)));
        root.setElevation(dp(14));

        // header
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("GX  CONTROL");
        title.setTextColor(PURPLE);
        title.setTextSize(11);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setLetterSpacing(0.20f);
        title.setPadding(dp(10), 0, 0, 0);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1f));
        h.addView(title);
        h.addView(icon("\u2715", v -> togglePanel()));   // ✕
        root.addView(h);

        // transport
        LinearLayout r1 = new LinearLayout(this);
        r1.addView(icon("\u25B7", v -> setB("playing", true)));   // ▷
        r1.addView(icon("\u275A\u275A", v -> setB("playing", false))); // ❚❚
        r1.addView(icon("\u25A0", v -> setB("enabled", false)));  // ■
        root.addView(r1);

        // rotate + mirror
        LinearLayout r2 = new LinearLayout(this);
        r2.addView(icon("\u27F2", v -> setD("rotation", getD("rotation",0) - 5))); // ⟲
        r2.addView(icon("\u27F3", v -> setD("rotation", getD("rotation",0) + 5))); // ⟳
        r2.addView(icon("\u21C4", v -> setB("mirror", !getB("mirror",false))));    // ⇄
        root.addView(r2);

        // zoom
        LinearLayout r3 = new LinearLayout(this);
        r3.setGravity(Gravity.CENTER_VERTICAL);
        r3.addView(icon("\u2212", v -> { setD("zoom", Math.max(0.3, getD("zoom",1.0) - 0.1)); upd(); })); // −
        zoomVal = new TextView(this);
        zoomVal.setText("1.0x");
        zoomVal.setTextColor(PURPLE);
        zoomVal.setGravity(Gravity.CENTER);
        zoomVal.setTextSize(14);
        zoomVal.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        zoomVal.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(50)));
        r3.addView(zoomVal);
        r3.addView(icon("\uFF0B", v -> { setD("zoom", Math.min(4.0, getD("zoom",1.0) + 0.1)); upd(); })); // ＋
        root.addView(r3);

        // pan
        LinearLayout r4 = new LinearLayout(this);
        r4.addView(icon("\u2190", v -> setD("panX", getD("panX",0) - 5))); // ←
        r4.addView(icon("\u2191", v -> setD("panY", getD("panY",0) - 5))); // ↑
        r4.addView(icon("\u2193", v -> setD("panY", getD("panY",0) + 5))); // ↓
        r4.addView(icon("\u2192", v -> setD("panX", getD("panX",0) + 5))); // →
        root.addView(r4);

        // size
        LinearLayout r5 = new LinearLayout(this);
        r5.addView(icon("W\u2212", v -> setD("scaleX", Math.max(0.3, getD("scaleX",1.0) - 0.05))));
        r5.addView(icon("W\uFF0B", v -> setD("scaleX", Math.min(3.0, getD("scaleX",1.0) + 0.05))));
        r5.addView(icon("H\u2212", v -> setD("scaleY", Math.max(0.3, getD("scaleY",1.0) - 0.05))));
        r5.addView(icon("H\uFF0B", v -> setD("scaleY", Math.min(3.0, getD("scaleY",1.0) + 0.05))));
        root.addView(r5);

        pLp = lp(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        panel = root;
    }

    private void upd() {
        try { zoomVal.setText(String.format("%.1fx", getD("zoom",1.0))); } catch (Exception e) {}
    }

    private void togglePanel() {
        if (panelShown) {
            try { wm.removeView(panel); } catch (Exception e) {}
            panelShown = false;
        } else {
            pLp.x = bLp.x + dp(70);
            pLp.y = bLp.y;
            upd();
            try { wm.addView(panel, pLp); } catch (Exception e) {}
            panelShown = true;
        }
    }

    @Override public void onDestroy() {
        super.onDestroy();
        try { if (bubble != null) wm.removeView(bubble); } catch (Exception e) {}
        try { if (panelShown) wm.removeView(panel); } catch (Exception e) {}
    }
                        }
