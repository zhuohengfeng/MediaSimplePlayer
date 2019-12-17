package com.rokid.simpleplayer;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import com.rokid.simpleplayer.gl.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaDecodeHelper {

    private static final long TIMEOUT_US = 10000;

    private MediaDecodeListener mMediaDecodeListener;
    private VideoDecodeThread mVideoDecodeThread;
    private boolean isPlaying;
    private boolean isPause;
    private String filePath;

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
                int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                float time = mediaFormat.getLong(MediaFormat.KEY_DURATION) / 1000000;
                // 回调准备好的宽高
                if (mMediaDecodeListener != null) {
                    mMediaDecodeListener.onPrepared(width, height);
                }
                videoExtractor.selectTrack(videoTrackIndex);
                try {

                    String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                    videoCodec = MediaCodec.createDecoderByType(mime);

                    showSupportedColorFormat(videoCodec.getCodecInfo().getCapabilitiesForType(mime));
                    if(isColorFormatSupported(decodeColorFormat, videoCodec.getCodecInfo().getCapabilitiesForType(mime))){
                        Logger.d( "设置COLOR_FormatYUV420Flexible格式");
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
                            //Log.d(TAG, "zhf-nv21: 得到解码后的nv21数据： image="+image);
                            if (image != null) {
                                byte[] data = getDataFromImage(image, COLOR_FormatNV21);
                                if (mMediaDecodeListener != null && data!=null) {
                                    mMediaDecodeListener.onPreviewCallback(data, startMs);
                                }
//                                Logger.d("zhf-nv21: 得到解码后的nv21数据： data.size="+data.length);
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

    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;

    private static byte[] getDataFromImage(Image image, int colorFormat) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
                throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
            }
            if (!isImageFormatSupported(image)) {
                throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
            }
            Rect crop = null;

            crop = image.getCropRect();

            int format = image.getFormat();
            int width = crop.width();
            int height = crop.height();
            Image.Plane[] planes = image.getPlanes();
            byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
            byte[] rowData = new byte[planes[0].getRowStride()];
            int channelOffset = 0;
            int outputStride = 1;
            for (int i = 0; i < planes.length; i++) {
                switch (i) {
                    case 0:
                        channelOffset = 0;
                        outputStride = 1;
                        break;
                    case 1:
                        if (colorFormat == COLOR_FormatI420) {
                            channelOffset = width * height;
                            outputStride = 1;
                        } else if (colorFormat == COLOR_FormatNV21) {
                            channelOffset = width * height + 1;
                            outputStride = 2;
                        }
                        break;
                    case 2:
                        if (colorFormat == COLOR_FormatI420) {
                            channelOffset = (int) (width * height * 1.25);
                            outputStride = 1;
                        } else if (colorFormat == COLOR_FormatNV21) {
                            channelOffset = width * height;
                            outputStride = 2;
                        }
                        break;
                }
                ByteBuffer buffer = planes[i].getBuffer();
                int rowStride = planes[i].getRowStride();
                int pixelStride = planes[i].getPixelStride();
                int shift = (i == 0) ? 0 : 1;
                int w = width >> shift;
                int h = height >> shift;
                buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
                for (int row = 0; row < h; row++) {
                    int length;
                    if (pixelStride == 1 && outputStride == 1) {
                        length = w;
                        buffer.get(data, channelOffset, length);
                        channelOffset += length;
                    } else {
                        length = (w - 1) * pixelStride + 1;
                        buffer.get(rowData, 0, length);
                        for (int col = 0; col < w; col++) {
                            data[channelOffset] = rowData[col * pixelStride];
                            channelOffset += outputStride;
                        }
                    }
                    if (row < h - 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                }
            }
            return data;
        }
        return null;
    }

    private static boolean isImageFormatSupported(Image image) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            int format = image.getFormat();
            switch (format) {
                case ImageFormat.YUV_420_888:
                case ImageFormat.NV21:
                case ImageFormat.YV12:
                    return true;
            }
            return false;
        }
        return false;
    }
}
