package com.rokid.simpleplayer;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.rokid.simpleplayer.gl.Logger;
import com.rokid.simpleplayer.gl.YUVHelper;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaDecodeHelper {

    private static final long TIMEOUT_US = 10000;

    private MediaDecodeListener mMediaDecodeListener;
    private VideoDecodeThread mVideoDecodeThread;
    private boolean isPlaying;
    private boolean isPause;
    private String filePath;

    private int videoWidth;
    private int videoHeight;

    // 是否取消播放线程
    private boolean cancel = false;

    private final int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

    public MediaDecodeHelper(String filePath) {
        this.filePath = filePath;
        isPlaying = false;
        isPause = false;
    }

    public MediaDecodeHelper() {
        isPlaying = false;
        isPause = false;
    }

    public void setVideoFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setMediaDecodeListener(MediaDecodeListener mListener) {
        this.mMediaDecodeListener = mListener;
    }

    /**
     * 是否处于播放状态
     * @return
     */
    public boolean isPlaying() {
        return isPlaying && !isPause;
    }

    /**
     * 开始播放
     */
    public void play() {
        isPlaying = true;
        if (mVideoDecodeThread == null) {
            mVideoDecodeThread = new VideoDecodeThread();
            mVideoDecodeThread.start();
        }
    }

    /**
     * 暂停
     */
    public void pause() {
        isPause = true;
    }

    /**
     * 继续播放
     */
    public void continuePlay() {
        isPause = false;
    }

    /**
     * 停止播放
     */
    public void stop() {
        isPlaying = false;
    }

    /**
     * 销毁
     */
    public void destroy() {
        stop();
        if (mVideoDecodeThread != null) {
            mVideoDecodeThread.interrupt();
            mVideoDecodeThread = null;
        }
    }

    /**
     * 解复用，得到需要解码的数据
     * @param extractor
     * @param decoder
     * @param inputBuffers
     * @return 如果返回true，表示视频以及采样
     */
    private static boolean decodeMediaData(MediaExtractor extractor, MediaCodec decoder, ByteBuffer[] inputBuffers) {
        boolean isMediaEOS = false;
        int inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            int sampleSize = extractor.readSampleData(inputBuffer, 0);
            if (sampleSize < 0) {
                decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                isMediaEOS = true;
                Logger.d("end of stream");
            } else {
                decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                extractor.advance();
            }
        }
        return isMediaEOS;
    }

    /**
     * 解码延时
     * @param bufferInfo
     * @param startMillis
     */
    private void decodeDelay(MediaCodec.BufferInfo bufferInfo, long startMillis) {
        while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMillis) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    /**
     * 获取媒体类型的轨道
     * @param extractor
     * @param mediaType
     * @return
     */
    private static int getTrackIndex(MediaExtractor extractor, String mediaType) {
        int trackIndex = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat mediaFormat = extractor.getTrackFormat(i);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(mediaType)) {
                trackIndex = i;
                break;
            }
        }
        return trackIndex;
    }


    /**
     * 视频解码线程
     */
    private class VideoDecodeThread extends Thread {

        public VideoDecodeThread() {
            super("RokidVideo");
        }

        @Override
        public void run() {
            MediaExtractor videoExtractor = new MediaExtractor();
            MediaCodec videoCodec = null;
            try {
                videoExtractor.setDataSource(filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            int videoTrackIndex;
            // 获取视频所在轨道
            videoTrackIndex = getTrackIndex(videoExtractor, "video/");
            if (videoTrackIndex >= 0) {
                MediaFormat mediaFormat = videoExtractor.getTrackFormat(videoTrackIndex);
                videoWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                videoHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                float time = mediaFormat.getLong(MediaFormat.KEY_DURATION) / 1000000;
                // 回调准备好的宽高
                if (mMediaDecodeListener != null) {
                    mMediaDecodeListener.onPrepared(videoWidth, videoHeight);
                }
                videoExtractor.selectTrack(videoTrackIndex);
                try {

                    String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                    videoCodec = MediaCodec.createDecoderByType(mime);

                    showSupportedColorFormat(videoCodec.getCodecInfo().getCapabilitiesForType(mime));
                    if(isColorFormatSupported(decodeColorFormat, videoCodec.getCodecInfo().getCapabilitiesForType(mime))){
                        Logger.d( "设置COLOR_FormatYUV420Flexible格式, videoWidth="+videoWidth+", videoHeight="+videoHeight);
                        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
                    }

                    // surface设置为空，这样才能得到YUV数据
                    videoCodec.configure(mediaFormat, /*surface*/null, null, 0);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (videoCodec == null) {
                Logger.d("video decoder is unexpectedly null");
                return;
            }

            videoCodec.start();
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            ByteBuffer[] inputBuffers = videoCodec.getInputBuffers();
            boolean isVideoEOS = false;

            long startMs = System.currentTimeMillis();

            while (!Thread.interrupted() && !cancel) {
                if (isPlaying) {
                    // 暂停
                    if (isPause) {
                        continue;
                    }
                    // 将资源传递到解码器
                    if (!isVideoEOS) {
                        isVideoEOS = decodeMediaData(videoExtractor, videoCodec, inputBuffers);
                    }
                    // 获取解码后的数据
                    int outputBufferIndex = videoCodec.dequeueOutputBuffer(videoBufferInfo, TIMEOUT_US);
                    switch (outputBufferIndex) {
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            Logger.d("INFO_OUTPUT_FORMAT_CHANGED");
                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            Logger.d("INFO_TRY_AGAIN_LATER");
                            break;
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            Logger.d("INFO_OUTPUT_BUFFERS_CHANGED");
                            break;
                        default:
                            // 延迟解码
                            decodeDelay(videoBufferInfo, startMs);

                            // 解出YUV数据
                            Image image = videoCodec.getOutputImage(outputBufferIndex);
                            if (image != null) {
                                byte[] data = getDataFromImage(image);
//                                byte[] data = getDataFromImageNative(image, videoWidth, videoHeight);
                                if (mMediaDecodeListener != null && data!=null) {
                                    mMediaDecodeListener.onPreviewCallback(data, startMs);
                                }
                            }

                            // 释放资源
                            videoCodec.releaseOutputBuffer(outputBufferIndex, true);
                            break;
                    }
                    // 结尾
                    if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Logger.d("buffer stream end");
                        break;
                    }
                }
            }
            // 释放解码器
            videoCodec.stop();
            videoCodec.release();
            videoExtractor.release();

            // 播放完成
            MediaDecodeHelper.this.stop();
            if (mMediaDecodeListener != null) {
                mMediaDecodeListener.onStopped();
            }
        }
    }

    //================ For NV21 格式转换 ==========================
    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities codecCapabilities){
        for (int c:codecCapabilities.colorFormats){
            if(c==colorFormat){
                return true;
            }
        }
        return false;
    }

    private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
        StringBuilder builder = new StringBuilder();
        for (int c : caps.colorFormats) {
            builder.append(c + "\t");
        }
        Logger.d("supported color format: "+builder.toString());
    }

    private byte[] mPreviewData;
    private byte[] getDataFromImage(Image image) {
        if (image != null) {
            Image.Plane[] planes = image.getPlanes();
            if (planes.length > 0) {
                ByteBuffer buffer = planes[0].getBuffer();
                int bufferSize = image.getWidth()*image.getHeight()*3/2;
                if (mPreviewData == null || mPreviewData.length != bufferSize) {
                    mPreviewData = new byte[bufferSize];
                }
                buffer.get(mPreviewData,0,image.getWidth()*image.getHeight());
                ByteBuffer buffer2 = planes[2].getBuffer();
                buffer2.get(mPreviewData,image.getWidth()*image.getHeight(), buffer2.remaining());
                return mPreviewData;
            }
        }
        return null;
    }

//    private byte[] getDataFromImageNative(Image image, int width, int height) {
//        if (image != null) {
//            Image.Plane[] planes = image.getPlanes();
//            if (planes.length > 0) {
//                ByteBuffer buffer = planes[0].getBuffer();
//                int bufferSize = image.getWidth()*image.getHeight()*3/2;
//                if (mPreviewData == null || mPreviewData.length != bufferSize) {
//                    mPreviewData = new byte[bufferSize];
//                }
//
//                YUVHelper.yuvI420ToNV21( , mPreviewData, width, height);
//
//                buffer.get(mPreviewData,0,image.getWidth()*image.getHeight());
//                ByteBuffer buffer2 = planes[2].getBuffer();
//                buffer2.get(mPreviewData,image.getWidth()*image.getHeight(),buffer2.remaining());
//                return mPreviewData;
//            }
//        }
//        return null;
//    }

}
