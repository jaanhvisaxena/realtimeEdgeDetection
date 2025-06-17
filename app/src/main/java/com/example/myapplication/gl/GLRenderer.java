
package com.example.myapplication.gl;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class GLRenderer implements GLSurfaceView.Renderer {

    private final int[] textureId = new int[1];
    private volatile Bitmap pendingBitmap;
    private boolean textureInitialised = false;


    private int program   = 0;
    private volatile int  shaderMode = 0;
    private int  currentLinkedMode  = -1;


    private static final float[] VERTICES = {
            -1f,  1f,
            -1f, -1f,
            1f,  1f,
            1f, -1f
    };
    private static final float[] TEX_COORD = {
            0f, 0f,
            0f, 1f,
            1f, 0f,
            1f, 1f
    };
    private final FloatBuffer vb = ByteBuffer.allocateDirect(VERTICES.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(VERTICES);
    private final FloatBuffer tb = ByteBuffer.allocateDirect(TEX_COORD.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(TEX_COORD);

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig cfg) {
        vb.position(0); tb.position(0);

        GLES20.glGenTextures(1, textureId, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        linkProgram(0);
    }

    @Override public void onSurfaceChanged(GL10 gl, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
    }

    @Override public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (pendingBitmap != null) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);
            if (!textureInitialised) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, pendingBitmap, 0);
                textureInitialised = true;
            } else {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, pendingBitmap);
            }
            pendingBitmap.recycle();
            pendingBitmap = null;
        }

        if (currentLinkedMode != shaderMode) linkProgram(shaderMode);

        GLES20.glUseProgram(program);

        int aPos = GLES20.glGetAttribLocation(program, "a_Position");
        int aTex = GLES20.glGetAttribLocation(program, "a_TexCoord");
        int uTex = GLES20.glGetUniformLocation(program, "u_Texture");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);
        GLES20.glUniform1i(uTex, 0);

        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, vb);

        GLES20.glEnableVertexAttribArray(aTex);
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, tb);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aTex);
    }

    public void updateBitmap(Bitmap bmp) {
        if (bmp == null) return;
        if (pendingBitmap != null) pendingBitmap.recycle();
        pendingBitmap = bmp;
    }

    public void setShader(int mode) {
        shaderMode = mode;
    }




    private void linkProgram(int mode) {
        if (program != 0) {
            GLES20.glDeleteProgram(program);
        }

        String vs =
                "attribute vec2 a_Position;\n" +
                        "attribute vec2 a_TexCoord;\n" +
                        "varying   vec2 v_TexCoord;\n" +
                        "void main(){\n" +
                        "  v_TexCoord = a_TexCoord;\n" +
                        "  gl_Position = vec4(a_Position,0.0,1.0);\n" +
                        "}";

        String fs;
        switch (mode) {
            case 1:
                fs =
                        "precision mediump float;\n" +
                                "uniform sampler2D u_Texture;\n" +
                                "varying vec2 v_TexCoord;\n" +
                                "void main(){\n" +
                                "  vec3 c = texture2D(u_Texture, v_TexCoord).rgb;\n" +
                                "  float g = dot(c, vec3(0.299,0.587,0.114));\n" +
                                "  gl_FragColor = vec4(vec3(g),1.0);\n" +
                                "}";
                break;
            case 2:
                fs =
                        "precision mediump float;\n" +
                                "uniform sampler2D u_Texture;\n" +
                                "varying vec2 v_TexCoord;\n" +
                                "void main(){\n" +
                                "  vec4 c = texture2D(u_Texture, v_TexCoord);\n" +
                                "  gl_FragColor = vec4(vec3(1.0) - c.rgb, 1.0);\n" +
                                "}";
                break;
            default:
                fs =
                        "precision mediump float;\n" +
                                "uniform sampler2D u_Texture;\n" +
                                "varying vec2 v_TexCoord;\n" +
                                "void main(){\n" +
                                "  gl_FragColor = texture2D(u_Texture, v_TexCoord);\n" +
                                "}";
        }

        int v = compile(GLES20.GL_VERTEX_SHADER,   vs);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, v);
        GLES20.glAttachShader(program, f);
        GLES20.glLinkProgram(program);

        int[] link = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, link, 0);
        if (link[0] == 0) {
            Log.e("Shader", "Link error: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        } else {
            currentLinkedMode = mode;
        }
    }

    private static int compile(int type, String src) {
        int id = GLES20.glCreateShader(type);
        GLES20.glShaderSource(id, src);
        GLES20.glCompileShader(id);

        int[] ok = new int[1];
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            Log.e("Shader", "Compile error: " + GLES20.glGetShaderInfoLog(id));
            GLES20.glDeleteShader(id);
            return 0;
        }
        return id;
    }


    public static Bitmap bgrBytesToBitmap(byte[] data, int w, int h) {
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            int b = data[i * 3]     & 0xFF;
            int g = data[i * 3 + 1] & 0xFF;
            int r = data[i * 3 + 2] & 0xFF;
            px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.setPixels(px, 0, w, 0, 0, w, h);
        return bmp;
    }
}
