package com.rokid.simpleplayer;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private SimplePlayer mSimplePlayer;

    private SurfaceView mSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSurfaceView = findViewById(R.id.play_textureview);
        mSimplePlayer = new SimplePlayer(mSurfaceView.getHolder().getSurface(), "/sdcard/videoTest/DJI_720p_scene5_博物馆_20191205.mp4");
    }

    /**
     * 开始播放视频
     * @param view
     */
    public void onPlayClick(View view) {
        mSimplePlayer.play();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSimplePlayer.stop();
    }
}
