package com.rokid.simpleplayer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.arcsoft.imageutil.ArcSoftImageFormat;
import com.arcsoft.imageutil.ArcSoftImageUtil;
import com.arcsoft.imageutil.ArcSoftImageUtilError;
import com.rokid.simpleplayer.face.faceserver.FaceServer;
import com.rokid.simpleplayer.face.widget.ProgressDialog;
import com.rokid.simpleplayer.gl.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BaseActivity extends Activity {

    protected Object sync = new Object();

    // 注册图所在的目录
    protected static final String ROOT_DIR = Environment.getExternalStorageDirectory().getAbsolutePath();
    protected static final String REGISTER_DIR = ROOT_DIR + File.separator + "faceid";
    protected static final String REGISTER_FAILED_DIR = REGISTER_DIR + File.separator + "failed";
    protected ExecutorService executorService;
    protected ProgressDialog progressDialog = null;
    protected TextView tvNotificationRegisterResult;


    protected final static String VIDEO_PATH = "/sdcard/videoTest/";
    protected final static String VIDEO_LOG_PATH = "/sdcard/videoLog/";
    protected FileWriter writer;

    protected boolean libraryExists = true;
    // Demo 所需的动态库文件
    protected static final String[] LIBRARIES = new String[]{
            // 人脸相关
            "libarcsoft_face_engine.so",
            "libarcsoft_face.so",
            // 图像库相关
            "libarcsoft_image_util.so",
    };

    protected static final int ACTION_REQUEST_PERMISSIONS = 0x001;
    // 在线激活所需的权限
    protected static final String[] NEEDED_PERMISSIONS = new String[]{
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //保持亮屏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            getWindow().setAttributes(attributes);
        }

        // Activity启动后就锁定为启动时的方向
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        libraryExists = checkSoFile(LIBRARIES);
        ApplicationInfo applicationInfo = getApplicationInfo();
        Logger.d("onCreate: " + applicationInfo.nativeLibraryDir);
        if (!libraryExists) {
            showToast("错误: 没有找到人脸识别so库");
            this.finish();
        }


        // 注册进度条
        progressDialog = new ProgressDialog(this);
    }

    /**
     * 权限检查
     *
     * @param neededPermissions 需要的权限
     * @return 是否全部被允许
     */
    protected boolean checkPermissions(String[] neededPermissions) {
        if (neededPermissions == null || neededPermissions.length == 0) {
            return true;
        }
        boolean allGranted = true;
        for (String neededPermission : neededPermissions) {
            allGranted &= ContextCompat.checkSelfPermission(this, neededPermission) == PackageManager.PERMISSION_GRANTED;
        }
        return allGranted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean isAllGranted = true;
        for (int grantResult : grantResults) {
            isAllGranted &= (grantResult == PackageManager.PERMISSION_GRANTED);
        }
        afterRequestPermission(requestCode, isAllGranted);
    }

    /**
     * 请求权限的回调
     *
     * @param requestCode  请求码
     * @param isAllGranted 是否全部被同意
     */
    abstract void afterRequestPermission(int requestCode, boolean isAllGranted);

    protected void showToast(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }
    protected void showLongToast(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    /**
     * 检查能否找到动态链接库，如果找不到，请修改工程配置
     *
     * @param libraries 需要的动态链接库
     * @return 动态库是否存在
     */
    protected boolean checkSoFile(String[] libraries) {
        File dir = new File(getApplicationInfo().nativeLibraryDir);
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return false;
        }
        List<String> libraryNameList = new ArrayList<>();
        for (File file : files) {
            libraryNameList.add(file.getName());
        }
        boolean exists = true;
        for (String library : libraries) {
            exists &= libraryNameList.contains(library);
        }
        return exists;
    }

    protected String getVersionName(Context context) throws Exception {
        // 获取packagemanager的实例
        PackageManager packageManager = context.getPackageManager();
        // getPackageName()是你当前类的包名，0代表是获取版本信息
        PackageInfo packInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
        String version = packInfo.versionName;
        return version;
    }


    protected void writeLog(String name, int trackId) {
        try {
            StringBuffer buffer = new StringBuffer();
            String recgname = TextUtils.isEmpty(name) ? "null": name;
            buffer.append("timeStamp:").append(System.currentTimeMillis())
                    .append(" trackId:").append(trackId)
                    .append(" name:").append(recgname)
                    .append("\n");
            writer.write(buffer.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void doRegister() {
        File dir = new File(REGISTER_DIR);
        if (!dir.exists()) {
            showToast(getString(R.string.batch_process_path_is_not_exists, REGISTER_DIR));
            return;
        }
        if (!dir.isDirectory()) {
            showToast(getString(R.string.batch_process_path_is_not_dir, REGISTER_DIR));
            return;
        }
        // 只过滤图片
        final File[] jpgFiles = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(FaceServer.IMG_SUFFIX);
            }
        });

        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor();
        }

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                final int totalCount = jpgFiles.length;

                int successCount = 0;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.setMaxProgress(totalCount);
                        progressDialog.show();
                        tvNotificationRegisterResult.setText("");
                        tvNotificationRegisterResult.append(getString(R.string.batch_process_processing_please_wait));
                    }
                });
                for (int i = 0; i < totalCount; i++) {
                    final int finalI = i;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (progressDialog != null) {
                                progressDialog.refreshProgress(finalI);
                            }
                        }
                    });
                    final File jpgFile = jpgFiles[i];
                    Bitmap bitmap = BitmapFactory.decodeFile(jpgFile.getAbsolutePath());
                    if (bitmap == null) {
                        File failedFile = new File(REGISTER_FAILED_DIR + File.separator + jpgFile.getName());
                        if (!failedFile.getParentFile().exists()) {
                            failedFile.getParentFile().mkdirs();
                        }
                        jpgFile.renameTo(failedFile);
                        continue;
                    }
                    bitmap = ArcSoftImageUtil.getAlignedBitmap(bitmap, true);
                    if (bitmap == null) {
                        File failedFile = new File(REGISTER_FAILED_DIR + File.separator + jpgFile.getName());
                        if (!failedFile.getParentFile().exists()) {
                            failedFile.getParentFile().mkdirs();
                        }
                        jpgFile.renameTo(failedFile);
                        continue;
                    }
                    byte[] bgr24 = ArcSoftImageUtil.createImageData(bitmap.getWidth(), bitmap.getHeight(), ArcSoftImageFormat.BGR24);
                    int transformCode = ArcSoftImageUtil.bitmapToImageData(bitmap, bgr24, ArcSoftImageFormat.BGR24);
                    if (transformCode != ArcSoftImageUtilError.CODE_SUCCESS) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.dismiss();
                                tvNotificationRegisterResult.append("");
                            }
                        });
                        return;
                    }
                    boolean success = FaceServer.getInstance().registerBgr24(BaseActivity.this.getApplicationContext(), bgr24, bitmap.getWidth(), bitmap.getHeight(),
                            jpgFile.getName().substring(0, jpgFile.getName().lastIndexOf(".")));
                    if (!success) {
                        File failedFile = new File(REGISTER_FAILED_DIR + File.separator + jpgFile.getName());
                        if (!failedFile.getParentFile().exists()) {
                            failedFile.getParentFile().mkdirs();
                        }
                        jpgFile.renameTo(failedFile);
                    } else {
                        successCount++;
                    }
                }
                final int finalSuccessCount = successCount;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        tvNotificationRegisterResult.append(getString(R.string.batch_process_finished_info, totalCount, finalSuccessCount, totalCount - finalSuccessCount, REGISTER_FAILED_DIR));
                    }
                });
                Logger.d("run: " + executorService.isShutdown());
            }
        });
    }

    protected void doCleanRegister() {
        int faceNum = FaceServer.getInstance().getFaceNumber(this);
        if (faceNum == 0) {
            showToast(getString(R.string.batch_process_no_face_need_to_delete));
        } else {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.batch_process_notification)
                    .setMessage(getString(R.string.batch_process_confirm_delete, faceNum))
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int deleteCount = FaceServer.getInstance().clearAllFaces(BaseActivity.this.getApplicationContext());
                            showToast(deleteCount + " faces cleared!");
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .create();
            dialog.show();
        }
    }

}
