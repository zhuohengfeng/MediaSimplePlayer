package com.rokid.simpleplayer;

import androidx.appcompat.app.AppCompatActivity;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements MediaDecodeListener {

    private MediaDecodeHelper mMediaDecodeHelper;

    private GLSurfaceView mGLSurfaceView;
    private GLRawDataRender mGLRawDataRender;

    private int mWidth;
    private int mHeight;
    private ByteBuffer ybuf;
    private ByteBuffer uvbuf;

    private long timeStamp;

    private Object sync = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mGLRawDataRender = new GLRawDataRender();
        mGLSurfaceView = findViewById(R.id.play_textureview);
        mGLSurfaceView.setEGLContextClientVersion(2);
        mGLSurfaceView.setRenderer(mGLRawDataRender);
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mMediaDecodeHelper = new MediaDecodeHelper();
        mMediaDecodeHelper.setMediaDecodeListener(this);
        mMediaDecodeHelper.setVideoFilePath("/sdcard/videoTest/DJI_720p_scene5_博物馆_20191205.mp4");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMediaDecodeHelper != null) {
            mMediaDecodeHelper.continuePlay();
        }
        mGLSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMediaDecodeHelper != null) {
            mMediaDecodeHelper.pause();
        }
        mGLSurfaceView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMediaDecodeHelper.destroy();
    }

    public void onPlayClick(View view) {
        if (!mMediaDecodeHelper.isPlaying()) {
            mMediaDecodeHelper.play();
        }
        else {
            Toast.makeText(this, "已经在播放了！！！", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPrepared(int width, int height) {
        mWidth = width;
        mHeight = height;
        mGLRawDataRender.setVideoWidthAndHeight(mWidth, mHeight);
    }

    @Override
    public void onPreviewCallback(byte[] bytes, long time) {
        synchronized (sync) {
            if (ybuf == null || uvbuf == null) {
                ybuf = ByteBuffer.allocate(mWidth * mHeight);
                uvbuf = ByteBuffer.allocate(mWidth * mHeight / 2);
            }
            ybuf.position(0);
            ybuf.put(bytes, 0, mWidth * mHeight);
            ybuf.position(0);

            uvbuf.position(0);
            uvbuf.put(bytes, mWidth * mHeight, mWidth * mHeight / 2);
            uvbuf.position(0);

            mGLRawDataRender.setRawData(ybuf, uvbuf);
            mGLSurfaceView.requestRender();
            timeStamp = time;
        }


    }

    @Override
    public void onStopped() {

    }
}
