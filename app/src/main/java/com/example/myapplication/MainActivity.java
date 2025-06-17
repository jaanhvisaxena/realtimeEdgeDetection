
package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.gl.GLRenderer;

public class MainActivity extends AppCompatActivity {

    static { System.loadLibrary("native-lib"); }
    public native byte[] processFrame(byte[] yPlane, int w, int h);

    private CameraController cameraController;
    private GLRenderer       glRenderer;

    private boolean showEdges  = true;
    private int     shaderMode = 0;

    private TextView fpsLabel;
    private long     lastTime   = 0;
    private int      frameCount = 0;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextureView  textureView  = findViewById(R.id.textureView);
        GLSurfaceView glView      = findViewById(R.id.glView);
        fpsLabel                 = findViewById(R.id.fpsLabel);
        Button toggleBtn         = findViewById(R.id.toggleButton);
        Button shaderBtn         = findViewById(R.id.shaderButton);
        shaderBtn.setOnClickListener(v -> {
            shaderMode = (shaderMode + 1) % 3;
            shaderBtn.setText(shaderMode == 0 ? "Shader: None" :
                    shaderMode == 1 ? "Shader: Gray" : "Shader: Invert");
            glRenderer.setShader(shaderMode);
        });

        glView.setEGLContextClientVersion(2);
        glRenderer = new GLRenderer();
        glView.setRenderer(glRenderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        toggleBtn.setOnClickListener(v -> {
            showEdges = !showEdges;
            toggleBtn.setText(showEdges ? "Show Raw" : "Show Edges");

            glView.setVisibility(showEdges ? View.VISIBLE : View.INVISIBLE);
        });


        shaderBtn.setOnClickListener(v -> {
            shaderMode = (shaderMode + 1) % 3;
            shaderBtn.setText(shaderMode==0 ? "Shader: None" :
                    shaderMode==1 ? "Shader: Gray" : "Shader: Invert");
            glRenderer.setShader(shaderMode);
        });

        cameraController = new CameraController(
                this,
                textureView,
                (yPlane, w, h, rotationDeg) -> {

                    if (!showEdges) return;

                    frameCount++;
                    long now = System.nanoTime();
                    if (now - lastTime >= 1_000_000_000L) {
                        final int fps = frameCount;
                        frameCount = 0;
                        lastTime   = now;
                        runOnUiThread(() -> fpsLabel.setText(fps + " FPS"));
                        Log.d("FPS", fps + " frames/s");
                    }

                    byte[] edgeBytes = processFrame(yPlane, w, h);
                    Bitmap bmp = GLRenderer.bgrBytesToBitmap(edgeBytes, w, h);


                    if (rotationDeg != 0) {
                        Matrix matrix = new Matrix();
                        matrix.postRotate(rotationDeg);
                        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
                    }

                    glRenderer.updateBitmap(bmp);
                    glView.requestRender();
                }
        );

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int w,int h){
                cameraController.startCamera();
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int w,int h){}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){ return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st){}
        });
    }

    @Override protected void onDestroy() {
        cameraController.stopCamera();
        super.onDestroy();
    }
}
