package com.rokid.simpleplayer;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.rokid.simpleplayer.utils.GLShaderUtil;
import com.rokid.simpleplayer.utils.GLTextureUtil;
import com.rokid.simpleplayer.utils.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLRawDataRender implements GLSurfaceView.Renderer {

    protected int videoWidth;

    protected int videoHeight;

    protected int mProgram;

    private int av_Position;
    private int af_Position;

    private int myTextureLoc;
    private int muvTextureLoc;

    //顶点坐标 Buffer
    private FloatBuffer mVertexBuffer;
    protected int mVertexBufferId;

    //纹理坐标 Buffer
    private FloatBuffer mTextureBuffer;
    protected int mTextureBufferId;

    private int[] mTextureID = new int[2];

    protected float vertexData[] = {
            -1f, -1f,// 左下角
            1f, -1f, // 右下角
            -1f, 1f, // 左上角
            1f, 1f,  // 右上角
    };

    protected float textureData[] = {
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f,
    };

    private ByteBuffer yBuffer;
    private ByteBuffer uvBuffer;

    protected final int CoordsPerVertexCount = 2;
    protected final int VertexCount = vertexData.length / CoordsPerVertexCount;
    protected final int VertexStride = CoordsPerVertexCount * 4;
    protected final int CoordsPerTextureCount = 2;
    protected final int TextureStride = CoordsPerTextureCount * 4;


    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        mProgram = GLShaderUtil.createProgram(vertexSource, fragmentSourceNV21);
        initVertexBufferObjects();
        av_Position = GLES20.glGetAttribLocation(mProgram, "av_Position");
        af_Position = GLES20.glGetAttribLocation(mProgram, "af_Position");
        myTextureLoc = GLES20.glGetUniformLocation(mProgram,  "yTexture");
        muvTextureLoc = GLES20.glGetUniformLocation(mProgram,  "uvTexture");
        Logger.d( "onSurfaceCreated : mProgram="+mProgram
                +", av_Position="+av_Position+", af_Position="+af_Position
                +", myTextureLoc="+myTextureLoc+", muvTextureLoc="+muvTextureLoc); // 这里有可能为空
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        Logger.d( "onSurfaceChanged width="+width+", height="+height);
        GLES20.glViewport(0, 0, width,  height);
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        if(mTextureID[0] == 0 || mTextureID[1] == 0) {
            mTextureID[0] = GLTextureUtil.GenImageTexture();
            mTextureID[1] = GLTextureUtil.GenImageTexture();
        }

        synchronized (GLRawDataRender.class) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            GLES20.glUseProgram(mProgram);

            // 绑定顶点和纹理坐标
            GLES20.glEnableVertexAttribArray(av_Position);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertexBufferId);
            GLES20.glVertexAttribPointer(av_Position, CoordsPerVertexCount, GLES20.GL_FLOAT, false, 0, 0);

            GLES20.glEnableVertexAttribArray(af_Position);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mTextureBufferId);
            GLES20.glVertexAttribPointer(af_Position, CoordsPerTextureCount, GLES20.GL_FLOAT, false, 0, 0);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

            // 绑定Y和UV纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID[0]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, videoWidth, videoHeight, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, yBuffer);
            GLES20.glUniform1i(myTextureLoc, 0);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID[1]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE_ALPHA, videoWidth / 2, videoHeight / 2, 0, GLES20.GL_LUMINANCE_ALPHA, GLES20.GL_UNSIGNED_BYTE, uvBuffer);
            GLES20.glUniform1i(muvTextureLoc, 1);

            // 绘制 GLES20.GL_TRIANGLE_STRIP:复用坐标
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VertexCount);

            GLES20.glDisableVertexAttribArray(av_Position);
            GLES20.glDisableVertexAttribArray(af_Position);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }
    }

    public synchronized void setRawData(ByteBuffer ybuf, ByteBuffer uvbuf) {
        Logger.d("setVideoWidthAndHeight setRawData");
        this.yBuffer = ybuf;
        this.uvBuffer = uvbuf;
    }

    public void setVideoWidthAndHeight(int width, int height) {
        Logger.d("setVideoWidthAndHeight width="+width+", height="+height);
        this.videoWidth = width;
        this.videoHeight = height;
    }

    private void initVertexBufferObjects() {
        int[] vbo = new int[2];
        GLES20.glGenBuffers(2, vbo, 0);

        mVertexBuffer = ByteBuffer.allocateDirect(vertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData);
        mVertexBuffer.position(0);
        mVertexBufferId = vbo[0];
        // ARRAY_BUFFER 将使用 Float*Array 而 ELEMENT_ARRAY_BUFFER 必须使用 Uint*Array
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVertexBufferId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexData.length * 4, mVertexBuffer, GLES20.GL_STATIC_DRAW);

        mTextureBuffer = ByteBuffer.allocateDirect(textureData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(textureData);
        mTextureBuffer.position(0);
        mTextureBufferId = vbo[1];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mTextureBufferId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, textureData.length * 4, mTextureBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,0);
    }


    private final String vertexSource = "attribute vec4 av_Position; " +
            "attribute vec2 af_Position; " +
            "varying vec2 v_texPo; " +
            "void main() { " +
            "    v_texPo = af_Position; " +
            "    gl_Position = av_Position; " +
            "}";

    private final String fragmentSourceNV21 = "precision highp float;" +
            "uniform sampler2D yTexture;" +
            "uniform sampler2D uvTexture;" +
            "varying highp vec2 v_texPo;" +
            "void main()" +
            "{" +
            "   float r, g, b, y, u, v;\n" +
            "   y = texture2D(yTexture, v_texPo).r;\n" +
            "   u = texture2D(uvTexture, v_texPo).a - 0.5;\n" +
            "   v = texture2D(uvTexture, v_texPo).r - 0.5;\n" +
            "   r = y + 1.57481*v;\n" +
            "   g = y - 0.18732*u - 0.46813*v;\n" +
            "   b = y + 1.8556*u;\n" +
            "   gl_FragColor = vec4(r, g, b, 1.0);\n" +
            "}";
}

