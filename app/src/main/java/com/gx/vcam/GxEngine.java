package com.gx.vcam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;

import org.json.JSONObject;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GxEngine {

    // ── حالة ──
    private static GxEngine sInst;
    public static synchronized GxEngine get() {
        if (sInst == null) sInst = new GxEngine();
        return sInst;
    }

    private volatile boolean running = false;
    private Thread renderThread;
    private Surface target;
    private Bitmap frame;
    private String loadedPath;
    private int vw = 720, vh = 1280;

    // ── تحميل الوسيط حسب الإعدادات ──
    public synchronized void ensureFrame() {
        try {
            JSONObject c = GxConfig.read();
            int slot = c.optInt("slot", 0);
            JSONObject s = GxStore.getSlot(slot);
            if (s == null) return;
            String path = s.optString("path", "");
            if (path.equals(loadedPath) && frame != null) return;
            File f = new File(path);
            if (!f.exists()) { GxLog.w("ENG", "missing " + path); return; }
            Bitmap bm = BitmapFactory.decodeFile(path);
            if (bm == null) { GxLog.w("ENG", "decode fail"); return; }
            if (frame != null) frame.recycle();
            frame = bm;
            loadedPath = path;
            vw = bm.getWidth();
            vh = bm.getHeight();
            GxLog.w("ENG", "frame loaded " + vw + "x" + vh);
        } catch (Throwable t) { GxLog.w("ENG", "ensureFrame " + t); }
    }

    // ── تشغيل الرسم على Surface ──
    public synchronized void start(final Surface surface) {
        if (running) { stop(); }
        target = surface;
        running = true;
        renderThread = new Thread(this::loop, "gx-gl");
        renderThread.start();
        GxLog.w("ENG", "render START");
    }

    public synchronized void stop() {
        running = false;
        try { if (renderThread != null) renderThread.join(800); } catch (Throwable t) {}
        renderThread = null;
        target = null;
        GxLog.w("ENG", "render STOP");
    }

    // ── حلقة الرسم ──
    private void loop() {
        EGLDisplay dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] ver = new int[2];
        EGL14.eglInitialize(dpy, ver, 0, ver, 1);

        int[] cfgAttr = {
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE };
        EGLConfig[] cfgs = new EGLConfig[1];
        int[] n = new int[1];
        EGL14.eglChooseConfig(dpy, cfgAttr, 0, cfgs, 0, 1, n, 0);
        if (n[0] == 0) { GxLog.w("ENG", "no EGL config"); return; }
        EGLConfig cfg = cfgs[0];

        int[] ctxAttr = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE };
        EGLContext ctx = EGL14.eglCreateContext(dpy, cfg, EGL14.EGL_NO_CONTEXT, ctxAttr, 0);

        int[] sAttr = { EGL14.EGL_NONE };
        EGLSurface egl = EGL14.eglCreateWindowSurface(dpy, cfg, target, sAttr, 0);
        if (egl == EGL14.EGL_NO_SURFACE) { GxLog.w("ENG", "no EGL surface"); return; }
        EGL14.eglMakeCurrent(dpy, egl, egl, ctx);

        int prog = buildProgram();
        int tex  = buildTexture();
        FloatBuffer quad = quadBuffer();

        while (running) {
            try {
                ensureFrame();
                if (frame == null) { sleep(200); continue; }

                GLES20.glViewport(0, 0, vw, vh);
                GLES20.glClearColor(0f, 0f, 0f, 1f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                GLES20.glUseProgram(prog);
                int aPos = GLES20.glGetAttribLocation(prog, "aPos");
                int aTex = GLES20.glGetAttribLocation(prog, "aTex");
                GLES20.glEnableVertexAttribArray(aPos);
                GLES20.glEnableVertexAttribArray(aTex);
                GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad);
                quad.position(2);
                GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad);
                quad.position(0);

                int uMat = GLES20.glGetUniformLocation(prog, "uMat");
                int uFlip = GLES20.glGetUniformLocation(prog, "uFlip");
                GLES20.glUniformMatrix4fv(uMat, 1, false, buildMatrix(), 0);
                GLES20.glUniform1f(uFlip, 0f);

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, frame, 0);
                GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uTex"), 0);

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                EGL14.eglSwapBuffers(dpy, egl);

                sleep(33); // ~30fps
            } catch (Throwable t) { GxLog.w("ENG", "loop " + t); sleep(150); }
        }

        GLES20.glDeleteTextures(1, new int[]{tex}, 0);
        GLES20.glDeleteProgram(prog);
        EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(dpy, egl);
        EGL14.eglDestroyContext(dpy, ctx);
        EGL14.eglTerminate(dpy);
    }

    private float[] buildMatrix() {
        float zoom = 1f, rot = 0f, sx = 1f, sy = 1f;
        boolean mirror = false;
        try {
            JSONObject c = GxConfig.read();
            zoom = (float) c.optDouble("zoom", 1.0);
            rot  = (float) c.optDouble("rotation", 0);
            sx   = (float) c.optDouble("scaleX", 1.0);
            sy   = (float) c.optDouble("scaleY", 1.0);
            mirror = c.optBoolean("mirror", false);
        } catch (Throwable t) {}

        float zx = zoom * sx * (mirror ? -1 : 1);
        float zy = zoom * sy;
        double r = Math.toRadians(rot);
        float cs = (float) Math.cos(r), sn = (float) Math.sin(r);

        // 4x4 column-major
        return new float[]{
                zx * cs, zx * sn, 0, 0,
                -zy * sn, zy * cs, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1
        };
    }

    private static FloatBuffer quadBuffer() {
        // x,y,u,v
        float[] q = { -1f,-1f, 0f,0f,  1f,-1f, 1f,0f,  -1f,1f, 0f,1f,  1f,1f, 1f,1f };
        FloatBuffer b = ByteBuffer.allocateDirect(q.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(q).position(0);
        return b;
    }

    private int buildTexture() {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return t[0];
    }

    private int buildProgram() {
        String vs = "attribute vec2 aPos; attribute vec2 aTex; uniform mat4 uMat; varying vec2 vTex; void main(){ vTex=aTex; gl_Position = uMat * vec4(aPos,0.0,1.0); }";
        String fs = "precision mediump float; varying vec2 vTex; uniform sampler2D uTex; void main(){ gl_FragColor = texture2D(uTex, vTex); }";
        int v = compile(GLES20.GL_VERTEX_SHADER, vs);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        return p;
    }

    private int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        return s;
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (Throwable t) {} }
          }
