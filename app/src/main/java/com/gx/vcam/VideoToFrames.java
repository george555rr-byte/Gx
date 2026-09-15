package com.gx.vcam;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

import de.robv.android.xposed.XposedBridge;

public class VideoToFrames implements Runnable {
    private static final long DEFAULT_TIMEOUT_US = 10000;
    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;

    private final int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    private LinkedBlockingQueue<byte[]> mQueue;
    private OutputImageFormat outputImageFormat;
    private boolean stopDecode = false;
    private String videoFilePath;
    private Throwable throwable;
    private Thread childThread;
    private Surface play_surf;
    private Callback callback;

    public interface Callback { void onFinishDecode(); void onDecodeFrame(int index); }
    public void setCallback(Callback callback) { this.callback = callback; }
    public void setEnqueue(LinkedBlockingQueue<byte[]> queue) { mQueue = queue; }
    public void setSaveFrames(String dir, OutputImageFormat f) throws IOException { outputImageFormat = f; }
    public void set_surfcae(Surface s) { if (s != null) play_surf = s; }
    public void stopDecode() { stopDecode = true; }

    public void decode(String videoFilePath) throws Throwable {
        this.videoFilePath = videoFilePath;
        if (childThread == null) {
            childThread = new Thread(this, "decode");
            childThread.start();
            if (throwable != null) throw throwable;
        }
    }
    public void run() { try { videoDecode(videoFilePath); } catch (Throwable t) { throwable = t; } }

    @SuppressLint("WrongConstant")
    public void videoDecode(String videoFilePath) throws IOException {
        XposedBridge.log("【GX】【decoder】start");
        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(videoFilePath);
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) { XposedBridge.log("【GX】no video track"); return; }
            extractor.selectTrack(trackIndex);
            MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            if (isColorFormatSupported(decodeColorFormat, decoder.getCodecInfo().getCapabilitiesForType(mime)))
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
            decodeFramesToImage(decoder, extractor, mediaFormat);
            decoder.stop();
            while (!stopDecode) {
                extractor.seekTo(0, 0);
                decodeFramesToImage(decoder, extractor, mediaFormat);
                decoder.stop();
            }
        } catch (Exception e) { XposedBridge.log("【GX】videofile " + e); }
        finally {
            if (decoder != null) { try { decoder.stop(); } catch (Throwable t) { } decoder.release(); }
            if (extractor != null) extractor.release();
        }
    }

    private boolean isColorFormatSupported(int cf, MediaCodecInfo.CodecCapabilities caps) {
        for (int c : caps.colorFormats) if (c == cf) return true;
        return false;
    }

    private void decodeFramesToImage(MediaCodec decoder, MediaExtractor extractor, MediaFormat mediaFormat) {
        boolean is_first = false;
        long startWhen = 0;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        decoder.configure(mediaFormat, play_surf, null, 0);
        boolean sawInputEOS = false, sawOutputEOS = false;
        decoder.start();
        int outputFrameCount = 0;
        while (!sawOutputEOS && !stopDecode) {
            if (!sawInputEOS) {
                int ib = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                if (ib >= 0) {
                    ByteBuffer buf = decoder.getInputBuffer(ib);
                    int size = extractor.readSampleData(buf, 0);
                    if (size < 0) { decoder.queueInputBuffer(ib, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM); sawInputEOS = true; }
                    else { decoder.queueInputBuffer(ib, 0, size, extractor.getSampleTime(), 0); extractor.advance(); }
                }
            }
            int ob = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US);
            if (ob >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) sawOutputEOS = true;
                if (info.size != 0) {
                    outputFrameCount++;
                    if (callback != null) callback.onDecodeFrame(outputFrameCount);
                    if (!is_first) { startWhen = System.currentTimeMillis(); is_first = true; }
                    if (play_surf == null) {
                        Image image = decoder.getOutputImage(ob);
                        if (image != null) {
                            ByteBuffer b = image.getPlanes()[0].getBuffer();
                            byte[] arr = new byte[b.remaining()]; b.get(arr);
                            if (mQueue != null) { try { mQueue.put(arr); } catch (InterruptedException e) { } }
                            HookMain.data_buffer = getDataFromImage(image, COLOR_FormatNV21);
                            image.close();
                        }
                    }
                    long sleep = info.presentationTimeUs / 1000 - (System.currentTimeMillis() - startWhen);
                    if (sleep > 0) { try { Thread.sleep(sleep); } catch (InterruptedException e) { } }
                    decoder.releaseOutputBuffer(ob, true);
                }
            }
        }
        if (callback != null) callback.onFinishDecode();
    }

    private static int selectTrack(MediaExtractor ex) {
        for (int i = 0; i < ex.getTrackCount(); i++) {
            String mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) return i;
        }
        return -1;
    }

    private static boolean isImageFormatSupported(Image image) {
        int f = image.getFormat();
        return f == ImageFormat.YUV_420_888 || f == ImageFormat.NV21 || f == ImageFormat.YV12;
    }

    private static byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) throw new IllegalArgumentException();
        if (!isImageFormatSupported(image)) throw new RuntimeException("unsupported");
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width(), height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        int channelOffset = 0, outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0: channelOffset = 0; outputStride = 1; break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) { channelOffset = width * height; outputStride = 1; }
                    else { channelOffset = width * height + 1; outputStride = 2; }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) { channelOffset = (int)(width*height*1.25); outputStride = 1; }
                    else { channelOffset = width * height; outputStride = 2; }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift, h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w; buffer.get(data, channelOffset, length); channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) { data[channelOffset] = rowData[col * pixelStride]; channelOffset += outputStride; }
                }
                if (row < h - 1) buffer.position(buffer.position() + rowStride - length);
            }
        }
        return data;
    }
}

enum OutputImageFormat {
    I420("I420"), NV21("NV21"), JPEG("JPEG");
    private final String n;
    OutputImageFormat(String n) { this.n = n; }
    public String toString() { return n; }
                      }
