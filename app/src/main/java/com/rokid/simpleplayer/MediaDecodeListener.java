package com.rokid.simpleplayer;

public interface MediaDecodeListener {

    void onPrepared(int width, int height);

    void onPreviewCallback(final byte[] bytes, long time);

    void onStopped();

}
