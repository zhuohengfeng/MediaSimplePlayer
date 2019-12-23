package com.rokid.simpleplayer;

import android.opengl.GLSurfaceView;
import android.os.Bundle;

import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.arcsoft.face.AgeInfo;
import com.arcsoft.face.ErrorInfo;
import com.arcsoft.face.FaceEngine;

import com.arcsoft.face.FaceFeature;
import com.arcsoft.face.GenderInfo;
import com.arcsoft.face.LivenessInfo;
import com.arcsoft.face.VersionInfo;
import com.arcsoft.face.enums.DetectMode;
import com.rokid.simpleplayer.face.FaceConstants;
import com.rokid.simpleplayer.face.faceserver.CompareResult;
import com.rokid.simpleplayer.face.faceserver.FaceServer;
import com.rokid.simpleplayer.face.model.DrawInfo;
import com.rokid.simpleplayer.face.model.FacePreviewInfo;
import com.rokid.simpleplayer.face.utils.ConfigUtil;
import com.rokid.simpleplayer.face.utils.DrawHelper;
import com.rokid.simpleplayer.face.utils.FaceHelper;
import com.rokid.simpleplayer.face.utils.FaceListener;
import com.rokid.simpleplayer.face.utils.FileUtils;
import com.rokid.simpleplayer.face.utils.RecognizeColor;
import com.rokid.simpleplayer.face.utils.RequestFeatureStatus;
import com.rokid.simpleplayer.face.widget.FaceRectView;
import com.rokid.simpleplayer.face.widget.FaceSearchResultAdapter;
import com.rokid.simpleplayer.gl.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static com.arcsoft.face.enums.DetectFaceOrientPriority.ASF_OP_ALL_OUT;

public class MainActivity extends BaseActivity implements MediaDecodeListener {

    private MediaDecodeHelper mMediaDecodeHelper;
    private GLSurfaceView mGLSurfaceView;
    private GLRawDataRender mGLRawDataRender;

    private int mWidth;
    private int mHeight;
    private ByteBuffer ybuf;
    private ByteBuffer uvbuf;
    private long timeStamp;

    private static final int MAX_DETECT_NUM = 10;
    /**
     * 失败重试间隔时间（ms）
     */
    private static final long FAIL_RETRY_INTERVAL = 1000;
    /**
     * 出错重试最大次数
     */
    private static final int MAX_RETRY_TIME = 3;

    private DrawHelper drawHelper;

    /**
     * VIDEO模式人脸检测引擎，用于预览帧人脸追踪
     */
    private FaceEngine ftEngine;
    /**
     * 用于特征提取的引擎
     */
    private FaceEngine frEngine;

    private int ftInitCode = -1;
    private int frInitCode = -1;
    private FaceHelper faceHelper;
    private List<CompareResult> compareResultList;
//    private FaceSearchResultAdapter mFaceSearchAdapter;

    /**
     * 用于记录人脸识别相关状态
     */
    private ConcurrentHashMap<Integer, Integer> requestFeatureStatusMap = new ConcurrentHashMap<>();
    /**
     * 用于记录人脸特征提取出错重试次数
     */
    private ConcurrentHashMap<Integer, Integer> extractErrorRetryMap = new ConcurrentHashMap<>();

    private CompositeDisposable getFeatureDelayedDisposables = new CompositeDisposable();
    private CompositeDisposable delayFaceTaskCompositeDisposable = new CompositeDisposable();
    /**
     * 绘制人脸框的控件
     */
    private FaceRectView faceRectView;

    private static final int ACTION_REQUEST_PERMISSIONS = 0x001;
    /**
     * 识别阈值
     */
    private static final float SIMILAR_THRESHOLD = 0.8F;


    // 用于遍历视频文件
    private ArrayDeque<String> videoPaths = new ArrayDeque<>();
    private boolean stopped = false;
    private Timer timer;
    private TimerTask task;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //保持亮屏
        activeEngine();
    }

    /**
     * 激活引擎
     */
    public void activeEngine() {
        if (!checkPermissions(NEEDED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, NEEDED_PERMISSIONS, ACTION_REQUEST_PERMISSIONS);
            return;
        }
        int activeCode = FaceEngine.activeOnline(MainActivity.this,  FaceConstants.APP_ID, FaceConstants.SDK_KEY);
        if (activeCode == ErrorInfo.MOK) {
            Logger.d("人脸识别引擎激活成功");
        } else if (activeCode == ErrorInfo.MERR_ASF_ALREADY_ACTIVATED) {
            Logger.d("人脸识别引擎已经激活");
        } else {
            Logger.e("人脸识别引擎激活失败");
            showToast("人脸识别引擎激活失败！！退出");
            finish();
        }
        initView();
        initEngine();
        initMediaCodec();
    }

    /**
     * 申请权限后操作
     * @param requestCode  请求码
     * @param isAllGranted 是否全部被同意
     */
    @Override
    void afterRequestPermission(int requestCode, boolean isAllGranted) {
        if (requestCode == ACTION_REQUEST_PERMISSIONS) {
            if (isAllGranted) {
                activeEngine();
            } else {
                showToast("获取权限错误，退出");
                this.finish();
            }
        }
    }

    private void initView() {
        faceRectView = findViewById(R.id.single_camera_face_rect_view);
        tvNotificationRegisterResult = findViewById(R.id.notification_register_result);
        int faceNum = FaceServer.getInstance().getFaceNumber(this);
        if (faceNum > 0) {
            tvNotificationRegisterResult.setText("当前有"+faceNum+"张人脸特征");
        }
        else {
            tvNotificationRegisterResult.setText("请把图片放在/sdcard/faceid目录下");
        }

        //RecyclerView recyclerShowFaceInfo = findViewById(R.id.single_camera_recycler_view_person);
        compareResultList = new ArrayList<>();
        //mFaceSearchAdapter = new FaceSearchResultAdapter(compareResultList, this);
        //recyclerShowFaceInfo.setAdapter(mFaceSearchAdapter);
//        DisplayMetrics dm = getResources().getDisplayMetrics();
//        int spanCount = (int) (dm.widthPixels / (getResources().getDisplayMetrics().density * 100 + 0.5f));
//        recyclerShowFaceInfo.setLayoutManager(new GridLayoutManager(this, spanCount));
//        recyclerShowFaceInfo.setItemAnimator(new DefaultItemAnimator());
    }

    /**
     * 初始化引擎，ft用于注册，fr用于video识别
     */
    private void initEngine() {
        //本地人脸库初始化
        FaceServer.getInstance().init(MainActivity.this.getApplicationContext());

        ftEngine = new FaceEngine();
        ftInitCode = ftEngine.init(this, DetectMode.ASF_DETECT_MODE_VIDEO, ASF_OP_ALL_OUT,
                16, MAX_DETECT_NUM, FaceEngine.ASF_FACE_DETECT);

        frEngine = new FaceEngine();
        frInitCode = frEngine.init(this, DetectMode.ASF_DETECT_MODE_IMAGE, ASF_OP_ALL_OUT,
                16, MAX_DETECT_NUM, FaceEngine.ASF_FACE_RECOGNITION);

        VersionInfo versionInfo = new VersionInfo();
        ftEngine.getVersion(versionInfo);
        Logger.d("initEngine:  init: " + ftInitCode + "  version:" + versionInfo);

        if (ftInitCode != ErrorInfo.MOK) {
            String error = getString(R.string.specific_engine_init_failed, "ftEngine", ftInitCode);
            Logger.e("initEngine: " + error);
            showToast(error);
        }
        if (frInitCode != ErrorInfo.MOK) {
            String error = getString(R.string.specific_engine_init_failed, "frEngine", frInitCode);
            Logger.e("initEngine: " + error);
            showToast(error);
        }
    }

    /**
     * 销毁引擎，faceHelper中可能会有特征提取耗时操作仍在执行，加锁防止crash
     */
    private void unInitEngine() {
        if (ftInitCode == ErrorInfo.MOK && ftEngine != null) {
            synchronized (ftEngine) {
                int ftUnInitCode = ftEngine.unInit();
                Logger.e("unInitEngine: " + ftUnInitCode);
            }
        }
        if (frInitCode == ErrorInfo.MOK && frEngine != null) {
            synchronized (frEngine) {
                int frUnInitCode = frEngine.unInit();
                Logger.e("unInitEngine: " + frUnInitCode);
            }
        }
    }

    private void initMediaCodec() {
        mGLRawDataRender = new GLRawDataRender();
        mGLSurfaceView = findViewById(R.id.play_textureview);
        mGLSurfaceView.setEGLContextClientVersion(2);
        mGLSurfaceView.setRenderer(mGLRawDataRender);
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mMediaDecodeHelper = new MediaDecodeHelper();
        mMediaDecodeHelper.setMediaDecodeListener(this);
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

        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (task != null) {
            task.cancel();
            task = null;
        }

        unInitEngine();
        if (faceHelper != null) {
            ConfigUtil.setTrackedFaceCount(this, faceHelper.getTrackedFaceCount());
            faceHelper.release();
            faceHelper = null;
        }
        if (getFeatureDelayedDisposables != null) {
            getFeatureDelayedDisposables.clear();
        }
        if (delayFaceTaskCompositeDisposable != null) {
            delayFaceTaskCompositeDisposable.clear();
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        FaceServer.getInstance().unInit();
    }


    /**
     * 每个视频开始开始解码操作都会回调
     * @param width
     * @param height
     */
    @Override
    public void onPrepared(int width, int height) {
        stopped = false;
        mWidth = width;
        mHeight = height;

        // -----------绘制相关---------------
        mGLRawDataRender.setVideoWidthAndHeight(mWidth, mHeight);

        // -----------人脸识别相关---------------
        drawHelper = new DrawHelper(mWidth, mHeight, mGLSurfaceView.getWidth(), mGLSurfaceView.getHeight(), 0
                , 1, true, false, false);

        // 切换相机的时候可能会导致预览尺寸发生变化
        Integer trackedFaceCount = null;
        // 记录切换时的人脸序号
        if (faceHelper != null) {
            trackedFaceCount = faceHelper.getTrackedFaceCount();
            faceHelper.release();
            faceHelper = null;
        }
        faceHelper = new FaceHelper.Builder()
                .ftEngine(ftEngine)
                .frEngine(frEngine)
                .frQueueSize(MAX_DETECT_NUM)
                .flQueueSize(MAX_DETECT_NUM)
                .previewSize(mWidth, mHeight)
                .faceListener(faceListener)
                .trackedFaceCount(trackedFaceCount == null ? ConfigUtil.getTrackedFaceCount(MainActivity.this.getApplicationContext()) : trackedFaceCount)
                .build();
    }

    @Override
    public void onPreviewCallback(byte[] bytes, long time) {
        synchronized (sync) {
            // -----------绘制相关---------------
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

            // -----------人脸识别相关---------------
            if (faceRectView != null) {
                faceRectView.clearFaceInfo();
            }
            // 输入人脸nv21数据，得到人脸数据并绘制
            final List<FacePreviewInfo> facePreviewInfoList = faceHelper.onPreviewFrame(bytes);
            if (facePreviewInfoList != null && faceRectView != null && drawHelper != null) {
                drawPreviewInfo(facePreviewInfoList);
            }

            clearLeftFace(facePreviewInfoList);
            if (facePreviewInfoList != null && facePreviewInfoList.size() > 0) {
                for (int i = 0; i < facePreviewInfoList.size(); i++) {
                    Integer status = requestFeatureStatusMap.get(facePreviewInfoList.get(i).getTrackId());
                    if (status == null
                            || status == RequestFeatureStatus.TO_RETRY) {
                        requestFeatureStatusMap.put(facePreviewInfoList.get(i).getTrackId(), RequestFeatureStatus.SEARCHING);
                        // 请求特征值，这里会回调FaceListener，表示提取完成特征值
                        faceHelper.requestFaceFeature(bytes, facePreviewInfoList.get(i).getFaceInfo(), mWidth, mHeight, FaceEngine.CP_PAF_NV21, facePreviewInfoList.get(i).getTrackId());
//                            Log.i(TAG, "onPreview: fr start = " + System.currentTimeMillis() + " trackId = " + facePreviewInfoList.get(i).getTrackedFaceCount());
                    }
                }
            }
        }
    }

    /**
     * 完成一个视频的检测
     */
    @Override
    public void onStopped() {
        this.stopped = true;
        Logger.d("完成解码");
    }

    /**
     * 开始识别人脸
     * @param view
     */
    public void onPlayClick(View view) {
        if (mMediaDecodeHelper.isPlaying()) {
            showToast("已经在播放了！！！");
            return;
        }

        try {
            File logDir = new File(VIDEO_LOG_PATH);
            if(logDir.exists()){
                FileUtils.deleteDirection(logDir);
                FileUtils.createFolder(VIDEO_LOG_PATH);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        File dir = new File(VIDEO_PATH);
        for(String videoPath :dir.list()){
            Logger.d("videoPath:"+videoPath);
            videoPaths.add(VIDEO_PATH+videoPath);
        }
        if(videoPaths.size() > 0) {
            startDetect(videoPaths.poll());
            listenNextVideo();
        }
        else {
            showLongToast("没有视频可以检测");
        }
    }

    private void startDetect(String videoPath){
        File dir = null;
        try {
            dir = new File(VIDEO_LOG_PATH);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(!dir.exists()){
            dir.mkdirs();
        }
        File video = new File(videoPath);
        try {
            writer = new FileWriter(dir+File.separator+video.getName().split("\\.")[0]+".log");
        } catch (IOException e) {
            e.printStackTrace();
        }
        Logger.d("开始解码 startDetect:"+videoPath);
        // 开始解码
        mMediaDecodeHelper.destroy();
        mMediaDecodeHelper.setVideoFilePath(videoPath);
        mMediaDecodeHelper.play();
    }

    /**
     * 每隔10s检测一次
     */
    private void listenNextVideo() {
        timer = new Timer();
        task = new TimerTask() {
            @Override
            public void run() {
                if(stopped){
                    String videoPath = videoPaths.poll();
                    if(!TextUtils.isEmpty(videoPath)) {
                        startDetect(videoPath);
                    }else{
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showLongToast("已完成所有视频检测");
                                if (task != null) {
                                    task.cancel();
                                    task = null;
                                }
                                if (timer != null) {
                                    timer.cancel();
                                    timer = null;
                                }
                            }
                        });
                    }
                }
            }
        };
        timer.schedule(task,0,10000);
    }

    /**
     * 开始提取特征值
     * @param view
     */
    public void onExtractFaceFeature(View view) {
        if (mMediaDecodeHelper.isPlaying()) {
            showToast("正在播放视频，无法提取特征值！！！");
            return;
        }
        // 开始提取特征值
        doRegister();
    }

    /**
     * 清空人脸库
     */
    protected void onCleanFaceFeature(View view) {
        if (mMediaDecodeHelper.isPlaying()) {
            showToast("正在播放视频，无法清空特征值！！！");
            return;
        }
        // 开始提取特征值
        doCleanRegister();
    }


    // ===============================================================================
    private final FaceListener faceListener = new FaceListener() {
        @Override
        public void onFail(Exception e) {
            Logger.e("onFail: " + e.getMessage());
        }

        //请求FR的回调
        @Override
        public void onFaceFeatureInfoGet(final FaceFeature faceFeature, final Integer requestId, final Integer errorCode) {
            //FR成功
            if (faceFeature != null) {
                searchFace(faceFeature, requestId);
            }
            //特征提取失败
            else {
                if (increaseAndGetValue(extractErrorRetryMap, requestId) > MAX_RETRY_TIME) {
                    extractErrorRetryMap.put(requestId, 0);

                    String msg;
                    // 传入的FaceInfo在指定的图像上无法解析人脸，此处使用的是RGB人脸数据，一般是人脸模糊
                    if (errorCode != null && errorCode == ErrorInfo.MERR_FSDK_FACEFEATURE_LOW_CONFIDENCE_LEVEL) {
                        msg = getString(R.string.low_confidence_level);
                    } else {
                        msg = "ExtractCode:" + errorCode;
                    }
                    faceHelper.setName(requestId, getString(R.string.recognize_failed_notice, msg));
                    // 在尝试最大次数后，特征提取仍然失败，则认为识别未通过
                    requestFeatureStatusMap.put(requestId, RequestFeatureStatus.FAILED);
                    retryRecognizeDelayed(requestId);
                } else {
                    requestFeatureStatusMap.put(requestId, RequestFeatureStatus.TO_RETRY);
                }
            }
        }
    };

    private void drawPreviewInfo(List<FacePreviewInfo> facePreviewInfoList) {
        List<DrawInfo> drawInfoList = new ArrayList<>();
        // 有几个人脸识别
        for (int i = 0; i < facePreviewInfoList.size(); i++) {
            int trackId = facePreviewInfoList.get(i).getTrackId();
            String name = faceHelper.getName(trackId);
            Integer recognizeStatus = requestFeatureStatusMap.get(trackId);
            //Logger.d("drawPreviewInfo trackId="+trackId+", name="+name+", recognizeStatus="+recognizeStatus);

            // 根据识别结果和活体结果设置颜色
            int color = RecognizeColor.COLOR_UNKNOWN;
            if (recognizeStatus != null) {
                if (recognizeStatus == RequestFeatureStatus.FAILED) {
                    color = RecognizeColor.COLOR_FAILED;
                }
                if (recognizeStatus == RequestFeatureStatus.SUCCEED) {
                    color = RecognizeColor.COLOR_SUCCESS;
                    Logger.d("Rokid-Face: 找到人脸="+name+", trackId="+trackId);
                }
            }
            writeLog(name, trackId);

            drawInfoList.add(new DrawInfo(drawHelper.adjustRect(facePreviewInfoList.get(i).getFaceInfo().getRect()),
                    GenderInfo.UNKNOWN, AgeInfo.UNKNOWN_AGE, LivenessInfo.UNKNOWN, color,
                    name == null ? String.valueOf(trackId) : name));
        }
        drawHelper.draw(faceRectView, drawInfoList);
    }

    /**
     * 删除已经离开的人脸
     *
     * @param facePreviewInfoList 人脸和trackId列表
     */
    private void clearLeftFace(List<FacePreviewInfo> facePreviewInfoList) {
        if (compareResultList != null) { //compareResultList 存放找到的人脸信息
            for (int i = compareResultList.size() - 1; i >= 0; i--) {
                if (!requestFeatureStatusMap.containsKey(compareResultList.get(i).getTrackId())) {
                    compareResultList.remove(i);
//                    final int removeId = i;
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            mFaceSearchAdapter.notifyItemRemoved(removeId);
//                        }
//                    });
                }
            }
        }
        if (facePreviewInfoList == null || facePreviewInfoList.size() == 0) {
            requestFeatureStatusMap.clear();
            extractErrorRetryMap.clear();
            if (getFeatureDelayedDisposables != null) {
                getFeatureDelayedDisposables.clear();
            }
            return;
        }
        Enumeration<Integer> keys = requestFeatureStatusMap.keys();
        while (keys.hasMoreElements()) {
            int key = keys.nextElement();
            boolean contained = false;
            for (FacePreviewInfo facePreviewInfo : facePreviewInfoList) {
                if (facePreviewInfo.getTrackId() == key) {
                    contained = true;
                    break;
                }
            }
            if (!contained) {
                requestFeatureStatusMap.remove(key);
                extractErrorRetryMap.remove(key);
            }
        }
    }

    private void searchFace(final FaceFeature frFace, final Integer requestId) {
        Observable
                .create(new ObservableOnSubscribe<CompareResult>() {
                    @Override
                    public void subscribe(ObservableEmitter<CompareResult> emitter) {
//                        Log.i(TAG, "subscribe: fr search start = " + System.currentTimeMillis() + " trackId = " + requestId);
                        CompareResult compareResult = FaceServer.getInstance().getTopOfFaceLib(frFace);
//                        Log.i(TAG, "subscribe: fr search end = " + System.currentTimeMillis() + " trackId = " + requestId);
                        emitter.onNext(compareResult);

                    }
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<CompareResult>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(CompareResult compareResult) {
                        if (compareResult == null || compareResult.getUserName() == null) {
                            requestFeatureStatusMap.put(requestId, RequestFeatureStatus.FAILED);
                            faceHelper.setName(requestId, "VISITOR " + requestId);
                            return;
                        }

//                        Log.i(TAG, "onNext: fr search get result  = " + System.currentTimeMillis() + " trackId = " + requestId + "  similar = " + compareResult.getSimilar());
                        if (compareResult.getSimilar() > SIMILAR_THRESHOLD) {
                            boolean isAdded = false;
                            if (compareResultList == null) {
                                requestFeatureStatusMap.put(requestId, RequestFeatureStatus.FAILED);
                                faceHelper.setName(requestId, "VISITOR " + requestId);
                                return;
                            }
                            for (CompareResult compareResult1 : compareResultList) {
                                if (compareResult1.getTrackId() == requestId) {
                                    isAdded = true;
                                    break;
                                }
                            }
                            if (!isAdded) {
                                //对于多人脸搜索，假如最大显示数量为 MAX_DETECT_NUM 且有新的人脸进入，则以队列的形式移除
                                if (compareResultList.size() >= MAX_DETECT_NUM) {
                                    compareResultList.remove(0);
//                                    mFaceSearchAdapter.notifyItemRemoved(0);
                                }
                                //添加显示人员时，保存其trackId
                                compareResult.setTrackId(requestId);
                                compareResultList.add(compareResult);
//                                mFaceSearchAdapter.notifyItemInserted(compareResultList.size() - 1);
                            }
                            requestFeatureStatusMap.put(requestId, RequestFeatureStatus.SUCCEED);
                            faceHelper.setName(requestId, getString(R.string.recognize_success_notice, compareResult.getUserName()));

                        } else {
                            faceHelper.setName(requestId, getString(R.string.recognize_failed_notice, "NOT_REGISTERED"));
                            retryRecognizeDelayed(requestId);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        faceHelper.setName(requestId, getString(R.string.recognize_failed_notice, "NOT_REGISTERED"));
                        retryRecognizeDelayed(requestId);
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    /**
     * 将map中key对应的value增1回传
     *
     * @param countMap map
     * @param key      key
     * @return 增1后的value
     */
    public int increaseAndGetValue(Map<Integer, Integer> countMap, int key) {
        if (countMap == null) {
            return 0;
        }
        Integer value = countMap.get(key);
        if (value == null) {
            value = 0;
        }
        countMap.put(key, ++value);
        return value;
    }

    /**
     * 延迟 FAIL_RETRY_INTERVAL 重新进行人脸识别
     *
     * @param requestId 人脸ID
     */
    private void retryRecognizeDelayed(final Integer requestId) {
        requestFeatureStatusMap.put(requestId, RequestFeatureStatus.FAILED);
        Observable.timer(FAIL_RETRY_INTERVAL, TimeUnit.MILLISECONDS)
                .subscribe(new Observer<Long>() {
                    Disposable disposable;

                    @Override
                    public void onSubscribe(Disposable d) {
                        disposable = d;
                        delayFaceTaskCompositeDisposable.add(disposable);
                    }

                    @Override
                    public void onNext(Long aLong) {

                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        // 将该人脸特征提取状态置为FAILED，帧回调处理时会重新进行活体检测
                        faceHelper.setName(requestId, Integer.toString(requestId));
                        requestFeatureStatusMap.put(requestId, RequestFeatureStatus.TO_RETRY);
                        delayFaceTaskCompositeDisposable.remove(disposable);
                    }
                });
    }




}
