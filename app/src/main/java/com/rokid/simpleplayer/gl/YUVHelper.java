package com.rokid.simpleplayer.gl;

public class YUVHelper {

    static {
        System.loadLibrary("native-lib");
    }

    public static native int yuvI420ToNV21(byte[] i420Src, byte[] nv21Src, int width, int height);
}
