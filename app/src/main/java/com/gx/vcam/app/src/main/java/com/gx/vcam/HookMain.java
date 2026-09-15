package com.gx.vcam;

import android.hardware.Camera;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.MediaPlayer;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Handler;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {

    public static Surface mSurface;
    public static SurfaceTexture mSurfacetexture;
    public static MediaPlayer mMediaPlayer;
    public static SurfaceTexture fake_SurfaceTexture;
    public static Camera origin_preview_camera;
    public static MediaPlayer mplayer1;
    public static Camera mcamera1;
    public static SurfaceTexture c1_fake_texture;
    public static Surface c1_fake_surface;
    public static SurfaceHolder ori_holder;
    public static boolean is_hooked;
    public static Surface c2_preview_Surfcae, c2_preview_Surfcae_1;
    public static Surface c2_reader_Surfcae, c2_reader_Surfcae_1;
    public static MediaPlayer c2_player, c2_player_1;
    public static Surface c2_virtual_surface;
    public static SurfaceTexture c2_virtual_surfaceTexture;
    public boolean need_recreate;
    public static CameraDevice.StateCallback c2_state_cb;
    public static Class c2_state_callback;
    public static boolean is_first_hook_build = true;
    public int imageReaderFormat = 0;
    public static VideoToFrames c2_hw_decode_obj;
    public static int c2_ori_width = 1280, c2_ori_height = 720;

    // ── مساعدات GX ──
    private static boolean armed() {
        try { return GxConfig.read().optBoolean("enabled", true); } catch (Throwable t) { return false; }
    }
    private static String vpath() {
        try {
            JSONObject o = GxStore.getSlot(GxConfig.read().optInt("slot", 0));
            if (o == null || !"video".equals(o.optString("type"))) return null;
            String p = o.optString("path", null);
            return (p != null && new File(p).exists()) ? p : null;
        } catch (Throwable t) { return null; }
    }

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) throws Exception {
        try {
            GxLog.w("GX", "hook " + lp.packageName);
            if (!armed()) return;

            // ═══ Camera1: setPreviewTexture ═══
            XposedHelpers.findAndHookMethod("android.hardware.Camera", lp.classLoader,
                    "setPreviewTexture", SurfaceTexture.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (vpath() == null || p.args[0] == null) return;
                    if (p.args[0].equals(c1_fake_texture)) return;
                    if (origin_preview_camera != null && origin_preview_camera.equals(p.thisObject)) {
                        p.args[0] = fake_SurfaceTexture; return;
                    }
                    origin_preview_camera = (Camera) p.thisObject;
                    mSurfacetexture = (SurfaceTexture) p.args[0];
                    if (fake_SurfaceTexture == null) fake_SurfaceTexture = new SurfaceTexture(10);
                    else { fake_SurfaceTexture.release(); fake_SurfaceTexture = new SurfaceTexture(10); }
                    p.args[0] = fake_SurfaceTexture;
                }
            });

            // ═══ Camera1: startPreview ═══
            XposedHelpers.findAndHookMethod("android.hardware.Camera", lp.classLoader,
                    "startPreview", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    String vp = vpath(); if (vp == null) return;

                    if (ori_holder != null) {
                        if (mplayer1 != null) { mplayer1.release(); mplayer1 = null; }
                        mplayer1 = new MediaPlayer();
                        if (!ori_holder.getSurface().isValid()) return;
                        mplayer1.setSurface(ori_holder.getSurface());
                        mplayer1.setVolume(0, 0); mplayer1.setLooping(true);
                        mplayer1.setOnPreparedListener(MediaPlayer::start);
                        try { mplayer1.setDataSource(vp); mplayer1.prepare(); }
                        catch (IOException e) { GxLog.w("GX", "mp1 " + e); }
                    }
                    if (mSurfacetexture != null) {
                        if (mSurface != null) { mSurface.release(); mSurface = null; }
                        mSurface = new Surface(mSurfacetexture);
                        if (mMediaPlayer != null) { mMediaPlayer.release(); mMediaPlayer = null; }
                        mMediaPlayer = new MediaPlayer();
                        mMediaPlayer.setSurface(mSurface);
                        mMediaPlayer.setVolume(0, 0); mMediaPlayer.setLooping(true);
                        mMediaPlayer.setOnPreparedListener(MediaPlayer::start);
                        try { mMediaPlayer.setDataSource(vp); mMediaPlayer.prepare(); }
                        catch (IOException e) { GxLog.w("GX", "mp2 " + e); }
                    }
                }
            });

            // ═══ Camera1: setPreviewDisplay ═══
            XposedHelpers.findAndHookMethod("android.hardware.Camera", lp.classLoader,
                    "setPreviewDisplay", SurfaceHolder.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (vpath() == null || p.args[0] == null) return;
                    mcamera1 = (Camera) p.thisObject;
                    ori_holder = (SurfaceHolder) p.args[0];
                    if (c1_fake_texture == null) c1_fake_texture = new SurfaceTexture(11);
                    else { c1_fake_texture.release(); c1_fake_texture = new SurfaceTexture(11); }
                    if (c1_fake_surface == null) c1_fake_surface = new Surface(c1_fake_texture);
                    else { c1_fake_surface.release(); c1_fake_surface = new Surface(c1_fake_texture); }
                    is_hooked = true;
                    mcamera1.setPreviewTexture(c1_fake_texture);
                    p.setResult(null);
                }
            });

            // ═══ Camera2: openCamera ═══
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lp.classLoader,
                    "openCamera", String.class, CameraDevice.StateCallback.class, Handler.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args[1] == null || p.args[1].equals(c2_state_cb)) return;
                    c2_state_cb = (CameraDevice.StateCallback) p.args[1];
                    if (vpath() == null) return;
                    c2_state_callback = p.args[1].getClass();
                    is_first_hook_build = true;
                    process_camera2_init(c2_state_callback);
                }
            });

            // ═══ Camera2: addTarget ═══
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest$Builder", lp.classLoader,
                    "addTarget", Surface.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (vpath() == null || p.args[0] == null || c2_virtual_surface == null) return;
                    if (p.args[0].equals(c2_virtual_surface)) return;
                    String info = p.args[0].toString();
                    if (info.contains("Surface(name=null)")) {
                        if (c2_reader_Surfcae == null) c2_reader_Surfcae = (Surface) p.args[0];
                        else if (!c2_reader_Surfcae.equals(p.args[0]) && c2_reader_Surfcae_1 == null) c2_reader_Surfcae_1 = (Surface) p.args[0];
                    } else {
                        if (c2_preview_Surfcae == null) c2_preview_Surfcae = (Surface) p.args[0];
                        else if (!c2_preview_Surfcae.equals(p.args[0]) && c2_preview_Surfcae_1 == null) c2_preview_Surfcae_1 = (Surface) p.args[0];
                    }
                    p.args[0] = c2_virtual_surface;
                }
            });

            // ═══ Camera2: build ═══
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest$Builder", lp.classLoader,
                    "build", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { process_camera2_play(); }
            });

            // ═══ ImageReader ═══
            XposedHelpers.findAndHookMethod("android.media.ImageReader", lp.classLoader,
                    "newInstance", int.class, int.class, int.class, int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    c2_ori_width = (int) p.args[0];
                    c2_ori_height = (int) p.args[1];
                    imageReaderFormat = (int) p.args[2];
                }
            });

        } catch (Throwable t) { GxLog.w("GX", "handleLoadPackage " + t); }
    }

    // ═══ تشغيل الفيديو (Camera2) ═══
    private void process_camera2_play() {
        String vp = vpath(); if (vp == null) return;

        if (c2_reader_Surfcae != null) {
            if (c2_hw_decode_obj != null) c2_hw_decode_obj.stopDecode();
            c2_hw_decode_obj = new VideoToFrames();
            try { c2_hw_decode_obj.setSaveFrames("null", OutputImageFormat.NV21);
                  c2_hw_decode_obj.set_surfcae(c2_reader_Surfcae);
                  c2_hw_decode_obj.decode(vp); } catch (Throwable t) { }
        }
        if (c2_preview_Surfcae != null) {
            if (c2_player != null) { c2_player.release(); c2_player = null; }
            c2_player = new MediaPlayer();
            c2_player.setSurface(c2_preview_Surfcae);
            c2_player.setVolume(0, 0); c2_player.setLooping(true);
            c2_player.setOnPreparedListener(MediaPlayer::start);
            try { c2_player.setDataSource(vp); c2_player.prepare(); }
            catch (Exception e) { GxLog.w("GX", "c2p " + e); }
        }
    }

    // ═══ Camera2 init ═══
    private Surface create_virtual_surface() {
        if (need_recreate) {
            if (c2_virtual_surfaceTexture != null) { c2_virtual_surfaceTexture.release(); c2_virtual_surfaceTexture = null; }
            if (c2_virtual_surface != null) { c2_virtual_surface.release(); c2_virtual_surface = null; }
            c2_virtual_surfaceTexture = new SurfaceTexture(15);
            c2_virtual_surface = new Surface(c2_virtual_surfaceTexture);
            need_recreate = false;
        } else if (c2_virtual_surface == null) {
            need_recreate = true;
            c2_virtual_surface = create_virtual_surface();
        }
        return c2_virtual_surface;
    }

    private void process_camera2_init(Class hooked_class) {
        XposedHelpers.findAndHookMethod(hooked_class, "onOpened", CameraDevice.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                need_recreate = true;
                create_virtual_surface();
                if (c2_player != null) { try { c2_player.release(); } catch (Throwable t) { } c2_player = null; }
                c2_preview_Surfcae = c2_preview_Surfcae_1 = null;
                c2_reader_Surfcae = c2_reader_Surfcae_1 = null;
                if (vpath() == null || c2_virtual_surface == null) return;

                final Class devClass = param.args[0].getClass();

                XposedHelpers.findAndHookMethod(devClass, "createCaptureSession",
                        List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p2) {
                        if (p2.args[0] != null && c2_virtual_surface != null)
                            p2.args[0] = Arrays.asList(c2_virtual_surface);
                    }
                });
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    XposedHelpers.findAndHookMethod(devClass, "createCaptureSessionByOutputConfigurations",
                            List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p2) {
                            if (p2.args[0] != null && c2_virtual_surface != null)
                                p2.args[0] = Arrays.asList(new OutputConfiguration(c2_virtual_surface));
                        }
                    });
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    XposedHelpers.findAndHookMethod(devClass, "createCaptureSession",
                            SessionConfiguration.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p2) {
                            if (p2.args[0] != null && c2_virtual_surface != null) {
                                SessionConfiguration sc = (SessionConfiguration) p2.args[0];
                                p2.args[0] = new SessionConfiguration(sc.getSessionType(),
                                        Arrays.asList(new OutputConfiguration(c2_virtual_surface)),
                                        sc.getExecutor(), sc.getStateCallback());
                            }
                        }
                    });
                }
            }
        });
    }
                                                                                              }
