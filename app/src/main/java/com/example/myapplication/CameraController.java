
package com.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.view.Surface;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.TextureView;

import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;


public class CameraController {

    public interface FrameListener {
        void onFrame(byte[] yPlane, int width, int height, int rotationDegrees);
    }

    private final Activity    activity;
    private final TextureView textureView;
    private FrameListener listener;

    private CameraDevice         camera;
    private CameraCaptureSession session;
    private ImageReader          reader;
    private HandlerThread        bgThread;
    private Handler              bgHandler;

    private int sensorToDisplay = 0;
    private static final int SKIP_EVERY = 2;
    private int frameIndex = 0;

    public CameraController(Activity act, TextureView tex, FrameListener listener) {
        this.activity    = act;
        this.textureView = tex;
        this.listener    = listener;
    }

    public void startCamera() {
        bgThread = new HandlerThread("CameraThread");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());

        CameraManager mgr = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        if (mgr == null) return;

        try {
            String camId = mgr.getCameraIdList()[0];

            sensorToDisplay = computeSensorRotation(mgr, camId);


            Size[] all = mgr.getCameraCharacteristics(camId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(ImageFormat.YUV_420_888);

            Size selected = Arrays.stream(all)
                    .filter(s -> s.getWidth() <= 1280 && s.getHeight() <= 720)
                    .max(Comparator.comparingInt(s -> s.getWidth() * s.getHeight()))
                    .orElse(all[0]);


            reader = ImageReader.newInstance(
                    selected.getWidth(), selected.getHeight(),
                    ImageFormat.YUV_420_888, /*maxImages*/2);

            reader.setOnImageAvailableListener(onImage, bgHandler);

            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.CAMERA}, 123);
                return;
            }
            mgr.openCamera(camId, stateCb, bgHandler);

        } catch (CameraAccessException e) { e.printStackTrace(); }
    }

    public void stopCamera() {
        if (session  != null) session.close();
        if (camera   != null) camera.close();
        if (reader   != null) reader.close();
        if (bgThread != null) {
            bgThread.quitSafely();
            try { bgThread.join(); } catch (InterruptedException ignored) {}
        }
    }

    private final CameraDevice.StateCallback stateCb = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice cam) {
            camera = cam;
            try {
                SurfaceTexture st = textureView.getSurfaceTexture();
                st.setDefaultBufferSize(reader.getWidth(), reader.getHeight());

                Surface preview = new Surface(st);
                Surface target  = reader.getSurface();

                CaptureRequest.Builder req =
                        camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                req.addTarget(preview);
                req.addTarget(target);

                camera.createCaptureSession(
                        Arrays.asList(preview, target),
                        new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession s) {
                                session = s;
                                try { session.setRepeatingRequest(req.build(), null, bgHandler); }
                                catch (CameraAccessException e) { e.printStackTrace(); }
                            }
                            @Override public void onConfigureFailed(CameraCaptureSession s) {}
                        }, bgHandler);
            } catch (CameraAccessException e) { e.printStackTrace(); }
        }
        @Override public void onDisconnected(CameraDevice cam) { cam.close(); }
        @Override public void onError(CameraDevice cam, int err) { cam.close(); }
    };


    private final ImageReader.OnImageAvailableListener onImage = r -> {
        Image img = r.acquireLatestImage();
        if (img == null) return;

        if (++frameIndex % SKIP_EVERY != 0) { img.close(); return; }

        int w = img.getWidth(), h = img.getHeight();
        Image.Plane yP   = img.getPlanes()[0];
        ByteBuffer  buf  = yP.getBuffer();
        int rowStride    = yP.getRowStride();
        int pixelStride  = yP.getPixelStride();

        byte[] out = new byte[w * h];
        if (pixelStride == 1 && rowStride == w) {
            buf.get(out);
        } else {
            for (int rIdx=0; rIdx<h; rIdx++) {
                int bufPos = rIdx*rowStride;
                int outPos = rIdx*w;
                for (int c=0;c<w;c++)
                    out[outPos+c]=buf.get(bufPos+c*pixelStride);
            }
        }
        img.close();

        listener.onFrame(out, w, h, sensorToDisplay);
    };

    private int computeSensorRotation(CameraManager mgr, String camId)
            throws CameraAccessException {

        int deviceRot = activity.getWindowManager()
                .getDefaultDisplay()
                .getRotation();

        int deviceDeg;
        switch (deviceRot) {
            case Surface.ROTATION_90:  deviceDeg = 90;  break;
            case Surface.ROTATION_180: deviceDeg = 180; break;
            case Surface.ROTATION_270: deviceDeg = 270; break;
            default:                   deviceDeg = 0;   break;
        }

        Integer sensorOri = mgr.getCameraCharacteristics(camId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION);

        if (sensorOri == null) return 0;

        return (sensorOri - deviceDeg + 360) % 360;
    }

}
