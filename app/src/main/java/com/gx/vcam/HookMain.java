package com.gx.vcam;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {

    // ── حالة عامة ──
    public static Surface mSurface;
    public static SurfaceTexture mSurfacetexture;
    public static MediaPlayer mMediaPlayer;
    public static SurfaceTexture fake_SurfaceTexture;
    public static Camera origin_preview_camera;

    public static Camera camera_onPreviewFrame;
    public static Camera start_preview_camera;
    public static volatile byte[] data_buffer = {0};
    public static byte[] input;
    public static int mhight, mwidth;
    public static boolean is_someone_playing;
    public static boolean is_hooked;
    public static VideoToFrames hw_decode_obj;
    public static VideoToFrames c2_hw_decode_obj;
    public static VideoToFrames c2_hw_decode_obj_1;
    public static SurfaceTexture c1_fake_texture;
    public static Surface c1_fake_surface;
    public static SurfaceHolder ori_holder;
    public static MediaPlayer mplayer1;
    public static Camera mcamera1;
    public int imageReaderFormat = 0;
    public static boolean is_first_hook_build = true;

    public static int onemhight, onemwidth;
    public static Class camera_callback_calss;

    public static Surface c2_preview_Surfcae, c2_preview_Surfcae_1;
    public static Surface c2_reader_Surfcae, c2_reader_Surfcae_1;
    public static MediaPlayer c2_player, c2_player_1;
    public static Surface c2_virtual_surface;
    public static SurfaceTexture c2_virtual_surfaceTexture;
    public boolean need_recreate;
    public static CameraDevice.StateCallback c2_state_cb;
    public static CaptureRequest.Builder c2_builder;
    public static SessionConfiguration fake_sessionConfiguration, sessionConfiguration;
    public static OutputConfiguration outputConfiguration;

    public int c2_ori_width = 1280, c2_ori_height = 720;
    public static Class c2_state_callback;
    public Context toast_content;

    // ── مساعدات GX ──
    private static boolean armed() {
        try { return GxConfig.read().optBoolean("enabled", true); } catch (Throwable t) { return false; }
    }
    private static JSONObject slot() {
        try {
            int s = GxConfig.read().optInt("slot", 0);
            return GxStore.getSlot(s);
        } catch (Throwable t) { return null; }
    }
    private static String vpath() {
        JSONObject s = slot();
        if (s == null || !"video".equals(s.optString("type"))) return null;
        String p = s.optString("path", null);
        return (p != null && new File(p).exists()) ? p : null;
    }
    private static String ipath() {
        JSONObject s = slot();
        if (s == null || !"image".equals(s.optString("type"))) return null;
        String p = s.optString("path", null);
        return (p != null && new File(p).exists()) ? p : null;
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Exception {
        XposedBridge.log("【GX】hook: " + lpparam.packageName);

        // ══ Camera1: setPreviewTexture ══
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "setPreviewTexture", SurfaceTexture.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!armed()) return;
                if (vpath() == null) return;
                if (param.args[0] == null) return;
                if (param.args[0].equals(c1_fake_texture)) return;

                if (origin_preview_camera != null && origin_preview_camera.equals(param.thisObject)) {
                    param.args[0] = fake_SurfaceTexture;
                    return;
                }
                origin_preview_camera = (Camera) param.thisObject;
                mSurfacetexture = (SurfaceTexture) param.args[0];
                if (fake_SurfaceTexture == null) fake_SurfaceTexture = new SurfaceTexture(10);
                else { fake_SurfaceTexture.release(); fake_SurfaceTexture = new SurfaceTexture(10); }
                param.args[0] = fake_SurfaceTexture;
                XposedBridge.log("【GX】C1 preview texture captured");
            }
        });

        // ══ Camera2: openCamera (Handler) ══
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lpparam.classLoader,
                "openCamera", String.class, CameraDevice.StateCallback.class, Handler.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[1] == null) return;
                if (param.args[1].equals(c2_state_cb)) return;
                c2_state_cb = (CameraDevice.StateCallback) param.args[1];
                c2_state_callback = param.args[1].getClass();
                if (!armed() || vpath() == null) return;
                is_first_hook_build = true;
                process_camera2_init(c2_state_callback);
            }
        });

        // ══ Camera2: openCamera (Executor) — API 28+ ══
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lpparam.classLoader,
                    "openCamera", String.class, java.util.concurrent.Executor.class,
                    CameraDevice.StateCallback.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args[2] == null) return;
                    if (param.args[2].equals(c2_state_cb)) return;
                    c2_state_cb = (CameraDevice.StateCallback) param.args[2];
                    if (!armed() || vpath() == null) return;
                    c2_state_callback = param.args[2].getClass();
                    is_first_hook_build = true;
                    process_camera2_init(c2_state_callback);
                }
            });
        }

        // ══ Camera1: callbacks ══
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "setPreviewCallbackWithBuffer", Camera.PreviewCallback.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null && armed()) process_callback(param);
            }
        });
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "setPreviewCallback", Camera.PreviewCallback.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null && armed()) process_callback(param);
            }
        });
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "setOneShotPreviewCallback", Camera.PreviewCallback.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null && armed()) process_callback(param);
            }
        });

        // ══ Camera1: takePicture ══
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "takePicture", Camera.ShutterCallback.class, Camera.PictureCallback.class,
                Camera.PictureCallback.class, Camera.PictureCallback.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!armed()) return;
                if (param.args[1] != null) process_a_shot_YUV(param);
                if (param.args[3] != null) process_a_shot_jpeg(param, 3);
            }
        });

        // ══ Camera1: setPreviewDisplay ══
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "setPreviewDisplay", SurfaceHolder.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!armed() || vpath() == null) return;
                mcamera1 = (Camera) param.thisObject;
                ori_holder = (SurfaceHolder) param.args[0];
                if (c1_fake_texture == null) c1_fake_texture = new SurfaceTexture(11);
                else { c1_fake_texture.release(); c1_fake_texture = new SurfaceTexture(11); }
                if (c1_fake_surface == null) c1_fake_surface = new Surface(c1_fake_texture);
                else { c1_fake_surface.release(); c1_fake_surface = new Surface(c1_fake_texture); }
                is_hooked = true;
                mcamera1.setPreviewTexture(c1_fake_texture);
                param.setResult(null);
                XposedBridge.log("【GX】C1 preview display captured");
            }
        });

        // ══ Camera1: startPreview → شغّل الفيديو ══
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "startPreview", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                String vp = vpath();
                if (!armed() || vp == null) return;
                is_someone_playing = false;
                start_preview_camera = (Camera) param.thisObject;

                if (ori_holder != null) {
                    if (mplayer1 != null) { mplayer1.release(); mplayer1 = null; }
                    mplayer1 = new MediaPlayer();
                    if (!ori_holder.getSurface().isValid()) return;
                    mplayer1.setSurface(ori_holder.getSurface());
                    mplayer1.setVolume(0, 0);
                    mplayer1.setLooping(true);
                    mplayer1.setOnPreparedListener(MediaPlayer::start);
                    try { mplayer1.setDataSource(vp); mplayer1.prepare(); }
                    catch (IOException e) { XposedBridge.log("【GX】mp1 " + e); }
                }

                if (mSurfacetexture != null) {
                    if (mSurface != null) { mSurface.release(); mSurface = null; }
                    mSurface = new Surface(mSurfacetexture);
                    if (mMediaPlayer != null) { mMediaPlayer.release(); mMediaPlayer = null; }
                    mMediaPlayer = new MediaPlayer();
                    mMediaPlayer.setSurface(mSurface);
                    mMediaPlayer.setVolume(0, 0);
                    mMediaPlayer.setLooping(true);
                    mMediaPlayer.setOnPreparedListener(MediaPlayer::start);
                    try { mMediaPlayer.setDataSource(vp); mMediaPlayer.prepare(); }
                    catch (IOException e) { XposedBridge.log("【GX】mp2 " + e); }
                }
            }
        });

        // ══ Camera2: addTarget ══
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder", lpparam.classLoader,
                "addTarget", Surface.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!armed() || param.args[0] == null) return;
                if (vpath() == null) return;
                if (param.args[0].equals(c2_virtual_surface)) return;

                String info = param.args[0].toString();
                if (info.contains("Surface(name=null)")) {
                    if (c2_reader_Surfcae == null) c2_reader_Surfcae = (Surface) param.args[0];
                    else if (!c2_reader_Surfcae.equals(param.args[0]) && c2_reader_Surfcae_1 == null)
                        c2_reader_Surfcae_1 = (Surface) param.args[0];
                } else {
                    if (c2_preview_Surfcae == null) c2_preview_Surfcae = (Surface) param.args[0];
                    else if (!c2_preview_Surfcae.equals(param.args[0]) && c2_preview_Surfcae_1 == null)
                        c2_preview_Surfcae_1 = (Surface) param.args[0];
                }
                if (c2_virtual_surface != null) param.args[0] = c2_virtual_surface;
            }
        });

        // ══ Camera2: build → شغّل الفيديو ══
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder", lpparam.classLoader,
                "build", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!armed() || vpath() == null) return;
                process_camera2_play();
            }
        });

        // ══ ImageReader: التقط أبعاد الرندر ══
        XposedHelpers.findAndHookMethod("android.media.ImageReader", lpparam.classLoader,
                "newInstance", int.class, int.class, int.class, int.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                c2_ori_width = (int) param.args[0];
                c2_ori_height = (int) param.args[1];
                imageReaderFormat = (int) param.args[2];
                XposedBridge.log("【GX】reader " + c2_ori_width + "x" + c2_ori_height + " fmt " + imageReaderFormat);
            }
        });
    }

    // ── Camera2: تشغيل الفيديو على الأسطح المحفوظة ──
    private void process_camera2_play() {
        String vp = vpath();
        if (vp == null) return;

        if (c2_reader_Surfcae != null) {
            if (c2_hw_decode_obj != null) c2_hw_decode_obj.stopDecode();
            c2_hw_decode_obj = new VideoToFrames();
            try {
                c2_hw_decode_obj.setSaveFrames("null",
                        imageReaderFormat == 256 ? OutputImageFormat.JPEG : OutputImageFormat.NV21);
                c2_hw_decode_obj.set_surfcae(c2_reader_Surfcae);
                c2_hw_decode_obj.decode(vp);
            } catch (Throwable t) { XposedBridge.log("【GX】c2dec " + t); }
        }
        if (c2_reader_Surfcae_1 != null) {
            if (c2_hw_decode_obj_1 != null) c2_hw_decode_obj_1.stopDecode();
            c2_hw_decode_obj_1 = new VideoToFrames();
            try {
                c2_hw_decode_obj_1.setSaveFrames("null",
                        imageReaderFormat == 256 ? OutputImageFormat.JPEG : OutputImageFormat.NV21);
                c2_hw_decode_obj_1.set_surfcae(c2_reader_Surfcae_1);
                c2_hw_decode_obj_1.decode(vp);
            } catch (Throwable t) { XposedBridge.log("【GX】c2dec1 " + t); }
        }
        if (c2_preview_Surfcae != null) {
            if (c2_player != null) { c2_player.release(); c2_player = null; }
            c2_player = new MediaPlayer();
            c2_player.setSurface(c2_preview_Surfcae);
            c2_player.setVolume(0, 0);
            c2_player.setLooping(true);
            c2_player.setOnPreparedListener(MediaPlayer::start);
            try { c2_player.setDataSource(vp); c2_player.prepare(); }
            catch (Exception e) { XposedBridge.log("【GX】c2p " + e); }
        }
        if (c2_preview_Surfcae_1 != null) {
            if (c2_player_1 != null) { c2_player_1.release(); c2_player_1 = null; }
            c2_player_1 = new MediaPlayer();
            c2_player_1.setSurface(c2_preview_Surfcae_1);
            c2_player_1.setVolume(0, 0);
            c2_player_1.setLooping(true);
            c2_player_1.setOnPreparedListener(MediaPlayer::start);
            try { c2_player_1.setDataSource(vp); c2_player_1.prepare(); }
            catch (Exception e) { XposedBridge.log("【GX】c2p1 " + e); }
        }
    }

    // ── Camera2 init ──
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
                if (c2_player != null) { try { c2_player.release(); } catch (Throwable t) {} c2_player = null; }
                if (c2_player_1 != null) { try { c2_player_1.release(); } catch (Throwable t) {} c2_player_1 = null; }
                if (c2_hw_decode_obj != null) { c2_hw_decode_obj.stopDecode(); c2_hw_decode_obj = null; }
                if (c2_hw_decode_obj_1 != null) { c2_hw_decode_obj_1.stopDecode(); c2_hw_decode_obj_1 = null; }
                c2_preview_Surfcae = c2_preview_Surfcae_1 = null;
                c2_reader_Surfcae = c2_reader_Surfcae_1 = null;
                is_first_hook_build = true;

                if (!armed() || vpath() == null || c2_virtual_surface == null) return;

                final Class devClass = param.args[0].getClass();

                XposedHelpers.findAndHookMethod(devClass, "createCaptureSession",
                        List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p2) {
                        if (p2.args[0] != null && c2_virtual_surface != null) {
                            p2.args[0] = Arrays.asList(c2_virtual_surface);
                            if (p2.args[1] != null) process_camera2Session_callback((CameraCaptureSession.StateCallback) p2.args[1]);
                        }
                    }
                });

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    XposedHelpers.findAndHookMethod(devClass, "createCaptureSessionByOutputConfigurations",
                            List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p2) {
                            if (p2.args[0] != null && c2_virtual_surface != null) {
                                p2.args[0] = Arrays.asList(new OutputConfiguration(c2_virtual_surface));
                                if (p2.args[1] != null) process_camera2Session_callback((CameraCaptureSession.StateCallback) p2.args[1]);
                            }
                        }
                    });
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    XposedHelpers.findAndHookMethod(devClass, "createCaptureSession",
                            SessionConfiguration.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p2) {
                            if (p2.args[0] != null && c2_virtual_surface != null) {
                                SessionConfiguration sc = (SessionConfiguration) p2.args[0];
                                SessionConfiguration fake = new SessionConfiguration(
                                        sc.getSessionType(),
                                        Arrays.asList(new OutputConfiguration(c2_virtual_surface)),
                                        sc.getExecutor(),
                                        sc.getStateCallback());
                                p2.args[0] = fake;
                                process_camera2Session_callback(sc.getStateCallback());
                            }
                        }
                    });
                }
            }
        });
    }

    private void process_camera2Session_callback(CameraCaptureSession.StateCallback cb) {
        if (cb == null) return;
        try {
            XposedHelpers.findAndHookMethod(cb.getClass(), "onConfigured",
                    CameraCaptureSession.class, new XC_MethodHook() {});
            XposedHelpers.findAndHookMethod(cb.getClass(), "onConfigureFailed",
                    CameraCaptureSession.class, new XC_MethodHook() {});
        } catch (Throwable t) {}
    }

    // ── صورة: JPEG ──
    private void process_a_shot_jpeg(XC_MethodHook.MethodHookParam param, int index) {
        try {
            Class cb = param.args[index].getClass();
            XposedHelpers.findAndHookMethod(cb, "onPictureTaken",
                    byte[].class, Camera.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p2) {
                    try {
                        if (!armed()) return;
                        String ip = ipath();
                        if (ip == null) return;
                        Bitmap pict = BitmapFactory.decodeFile(ip);
                        if (pict == null) return;
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        pict.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                        p2.args[0] = baos.toByteArray();
                        XposedBridge.log("【GX】photo JPEG injected");
                    } catch (Throwable t) { XposedBridge.log("【GX】jpeg " + t); }
                }
            });
        } catch (Throwable t) {}
    }

    // ── صورة: YUV ──
    private void process_a_shot_YUV(XC_MethodHook.MethodHookParam param) {
        try {
            Class cb = param.args[1].getClass();
            XposedHelpers.findAndHookMethod(cb, "onPictureTaken",
                    byte[].class, Camera.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p2) {
                    try {
                        if (!armed()) return;
                        String ip = ipath();
                        if (ip == null) return;
                        Bitmap pict = BitmapFactory.decodeFile(ip);
                        if (pict == null) return;
                        p2.args[0] = getYUVByBitmap(pict);
                        XposedBridge.log("【GX】photo YUV injected");
                    } catch (Throwable t) { XposedBridge.log("【GX】yuv " + t); }
                }
            });
        } catch (Throwable t) {}
    }

    // ── Callback: تغذية الإطارات ──
    private void process_callback(XC_MethodHook.MethodHookParam param) {
        Class preview_cb_class = param.args[0].getClass();
        String vp = vpath();
        if (vp == null || !armed()) return;

        XposedHelpers.findAndHookMethod(preview_cb_class, "onPreviewFrame",
                byte[].class, Camera.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p2) {
                try {
                    Camera localcam = (Camera) p2.args[1];
                    if (localcam.equals(camera_onPreviewFrame)) {
                        if (data_buffer != null && data_buffer.length > 1) {
                            System.arraycopy(data_buffer, 0, p2.args[0], 0,
                                    Math.min(data_buffer.length, ((byte[]) p2.args[0]).length));
                        }
                    } else {
                        camera_onPreviewFrame = localcam;
                        mwidth = localcam.getParameters().getPreviewSize().width;
                        mhight = localcam.getParameters().getPreviewSize().height;
                        if (hw_decode_obj != null) hw_decode_obj.stopDecode();
                        hw_decode_obj = new VideoToFrames();
                        hw_decode_obj.setSaveFrames("", OutputImageFormat.NV21);
                        hw_decode_obj.decode(vp);
                    }
                } catch (Throwable t) { XposedBridge.log("【GX】cb " + t); }
            }
        });
    }

    // ── YUV من Bitmap ──
    private static byte[] rgb2YCbCr420(int[] pixels, int width, int height) {
        int len = width * height;
        byte[] yuv = new byte[len * 3 / 2];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int rgb = pixels[i * width + j] & 0x00FFFFFF;
                int r = rgb & 0xFF, g = (rgb >> 8) & 0xFF, b = (rgb >> 16) & 0xFF;
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                y = y < 16 ? 16 : Math.min(y, 255);
                u = u < 0 ? 0 : Math.min(u, 255);
                v = v < 0 ? 0 : Math.min(v, 255);
                yuv[i * width + j] = (byte) y;
                yuv[len + (i >> 1) * width + (j & ~1)] = (byte) u;
                yuv[len + (i >> 1) * width + (j & ~1) + 1] = (byte) v;
            }
        }
        return yuv;
    }

    private static byte[] getYUVByBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        return rgb2YCbCr420(pixels, w, h);
    }
                    }
