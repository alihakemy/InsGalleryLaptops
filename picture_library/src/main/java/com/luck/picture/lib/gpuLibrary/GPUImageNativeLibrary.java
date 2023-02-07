/*
 * Copyright (C) 2018 CyberAgent, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.luck.picture.lib.gpuLibrary;

import android.graphics.Bitmap;
import android.opengl.GLES20;

import java.nio.IntBuffer;

public class GPUImageNativeLibrary {
//    static {
//        System.loadLibrary("yuv-decoder");
//    }

    public static  void YUVtoRBGA(byte[] yuv, int width, int height, int[] out){

    }

    public static  void YUVtoARBG(byte[] yuv, int width, int height, int[] out){

    }

    public static Bitmap adjustBitmap(Bitmap srcBitmap){
        int w =srcBitmap.getWidth();
        int h =srcBitmap.getHeight();
        int b[] = new int[w * (0 + h)];
        int bt[] = new int[w * h];
        IntBuffer ib = IntBuffer.wrap(b);
        ib.position(0);
        GLES20.glReadPixels(0, 0, w,  h, GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, ib);
        for (int i = 0, k = 0; i < h; i++, k++) {
            // remember, that OpenGL bitmap is incompatible with Android bitmap
            // and so, some correction need.
            for (int j = 0; j < w; j++) {
                int pix = b[i * w + j];
                int pb = (pix >> 16) & 0xff;
                int pr = (pix << 16) & 0x00ff0000;
                int pix1 = (pix & 0xff00ff00) | pr | pb;
                bt[(h - k - 1) * w + j] = pix1;
            }
        }
        Bitmap sb = Bitmap.createBitmap(bt, w, h, Bitmap.Config.ARGB_8888);

        return sb;
    }
}
