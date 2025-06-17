package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.Color;

public class CameraUtils {


    public static Bitmap yToGrayBitmap(byte[] yPlane, int width, int height) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int[] pixels = new int[width * height];
        for (int i = 0; i < width * height && i < yPlane.length; i++) {
            int gray = yPlane[i] & 0xFF;
            pixels[i] = Color.rgb(gray, gray, gray);
        }

        bmp.setPixels(pixels, 0, width, 0, 0, width, height);
        return bmp;
    }
}
