package com.rokid.simpleplayer.face.utils;

import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileUtils {

    //检查SDCard存在并且可以读写
    public static boolean isSDCardState() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    /**
     * 判断文件是否已经存在
     *
     * @param fileName 要检查的文件名
     * @return boolean, true表示存在，false表示不存在
     */
    public static boolean isFileExist(String fileName) {
        try {
            File f = new File(fileName);
            if (!f.exists()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * 新建目录
     *
     * @param path 目录的绝对路径
     * @return 创建成功则返回true
     */
    public static boolean createFolder(String path) {
        File file = new File(path);
        return file.mkdir();
    }

    /**
     * 创建文件
     *
     * @param path     文件所在目录的目录名
     * @param fileName 文件名
     * @return 文件新建成功则返回true
     */
    public static boolean createFile(String path, String fileName) {
        File file = new File(path + File.separator + fileName);
        if (file.exists()) {
            return false;
        } else {
            try {
                return file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static boolean createFile(String fileName) {
        File file = new File(fileName);
        if (file.exists()) {
            return false;
        } else {
            try {
                return file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 删除单个文件
     *
     * @param path     文件所在的绝对路径
     * @param fileName 文件名
     * @return 删除成功则返回true
     */
    public static boolean deleteFile(String path, String fileName) {
        File file = new File(path + File.separator + fileName);
        return file.exists() && file.delete();
    }

    public static boolean deleteFile(String fileName) {
        File file = new File(fileName);
        return file.exists() && file.delete();
    }

    /**
     * 删除一个目录（可以是非空目录）
     *
     * @param dir 目录绝对路径
     */
    public static boolean deleteDirection(File dir) {
        if (dir == null || !dir.exists() || dir.isFile()) {
            return false;
        }
        for (File file : dir.listFiles()) {
            if (file.isFile()) {
                file.delete();
            } else if (file.isDirectory()) {
                deleteDirection(file);//递归
            }
        }
        dir.delete();
        return true;
    }

    /**
     * 将字符串写入文件
     *
     * @param text     写入的字符串
     * @param fileStr  文件的绝对路径
     * @param isAppend true从尾部写入，false从头覆盖写入
     */
    public static void writeFile(String text, String fileStr, boolean isAppend) {
        try {
            File file = new File(fileStr);
            File parentFile = file.getParentFile();
            if (!parentFile.exists()) {
                parentFile.mkdirs();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream f = new FileOutputStream(fileStr, isAppend);
            f.write(text.getBytes());
            f.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public static boolean copyFile(File source, File target) {
        if (source.isDirectory()) {
            if (!target.exists()) {
                if (!target.mkdir()) {
                    return false;
                }
            }
            String[] children = source.list();
            for (int i = 0; i < source.listFiles().length; i++) {
                if (!copyFile(new File(source, children[i]), new File(target, children[i]))) {
                    return false;
                }
            }
        } else {
            try {
                InputStream in = new FileInputStream(source);
                OutputStream out = new FileOutputStream(target);

                byte[] buf = new byte[65536];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
            } catch (IOException ioe) {
                ioe.printStackTrace();
                return false;
            }
        }
        return true;
    }


    /**
     * 拷贝文件
     *
     * @param srcPath 绝对路径
     * @param destDir 目标文件所在目录
     * @return boolean true拷贝成功
     */
    public static boolean copyFileToDir(String srcPath, String destDir) {
        boolean flag = false;
        File srcFile = new File(srcPath); // 源文件
        if (!srcFile.exists()) {
            Log.i("FileUtils is copyFile：", "源文件不存在");
            return false;
        }
        // 获取待复制文件的文件名
        String fileName = srcPath.substring(srcPath.lastIndexOf(File.separator));
        String destPath = destDir + fileName;
        if (destPath.equals(srcPath)) {
            Log.i("FileUtils is copyFile：", "源文件路径和目标文件路径重复");
            return false;
        }
        File destFile = new File(destPath); // 目标文件
        if (destFile.exists() && destFile.isFile()) {
            Log.i("FileUtils is copyFile：", "该路径下已经有一个同名文件");
            return false;
        }
        File destFileDir = new File(destDir);
        destFileDir.mkdirs();
        try {
            FileInputStream fis = new FileInputStream(srcPath);
            FileOutputStream fos = new FileOutputStream(destFile);
            byte[] buf = new byte[1024];
            int c;
            while ((c = fis.read(buf)) != -1) {
                fos.write(buf, 0, c);
            }
            fis.close();
            fos.close();
            flag = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return flag;
    }

    /**
     * 重命名文件
     *
     * @param oldPath 旧文件的绝对路径
     * @param newPath 新文件的绝对路径
     * @return 文件重命名成功则返回true
     */
    public static boolean renameTo(String oldPath, String newPath) {
        if (oldPath.equals(newPath)) {
            Log.i("FileUtils is renameTo：", "文件重命名失败：新旧文件名绝对路径相同");
            return false;
        }
        File oldFile = new File(oldPath);
        File newFile = new File(newPath);

        return oldFile.renameTo(newFile);
    }

    /**
     * 计算某个文件的大小
     *
     * @param path 文件的绝对路径
     * @return 文件大小
     */
    public static long getFileSize(String path) {
        File file = new File(path);
        return file.length();
    }

    /**
     * 计算某个文件夹的大小
     *
     * @param file 目录所在绝对路径
     * @return 文件夹的大小
     */
    public static double getDirSize(File file) {
        if (file.exists()) {
            //如果是目录则递归计算其内容的总大小
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                double size = 0;
                for (File f : children)
                    size += getDirSize(f);
                return size;
            } else {//如果是文件则直接返回其大小,以“兆”为单位
                return (double) file.length() / 1024 / 1024;
            }
        } else {
            return 0.0;
        }
    }

    /**
     * 获取某个路径下的文件列表
     *
     * @param path 文件路径
     * @return 文件列表File[] files
     */
    public static File[] getFileList(String path) {
        File file = new File(path);
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                return files;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * 计算某个目录包含的文件数量
     *
     * @param path 目录的绝对路径
     * @return 文件数量
     */
    public static int getFileCount(String path) {
        File directory = new File(path);
        File[] files = directory.listFiles();
        return files.length;
    }

    /**
     * 获取SDCard 总容量大小(MB)
     *
     * @param path 目录的绝对路径
     * @return 总容量大小
     */
    public long getSDCardTotal(String path) {
        if (null != path && path.equals("")) {
            StatFs statfs = new StatFs(path);
            //获取SDCard的Block总数
            long totalBlocks = statfs.getBlockCount();
            //获取每个block的大小
            long blockSize = statfs.getBlockSize();
            //计算SDCard 总容量大小MB
            return totalBlocks * blockSize / 1024 / 1024;

        } else {
            return 0;
        }
    }

    /**
     * 获取SDCard 可用容量大小(MB)
     *
     * @param path 目录的绝对路径
     * @return 可用容量大小
     */
    public long getSDCardFree(String path) {

        if (null != path && path.equals("")) {

            StatFs statfs = new StatFs(path);
            //获取SDCard的Block可用数
            long availaBlocks = statfs.getAvailableBlocks();
            //获取每个block的大小
            long blockSize = statfs.getBlockSize();
            //计算SDCard 可用容量大小MB
            return availaBlocks * blockSize / 1024 / 1024;

        } else {
            return 0;
        }
    }

    /**
     * 获取目录下所有文件(按时间排序)
     *
     * @param path
     * @return
     */
    public static List<File> getFileSort(String path) {

        List<File> list = getFiles(path, new ArrayList<File>());

        if (list != null && list.size() > 0) {

            Collections.sort(list, new Comparator<File>() {
                public int compare(File file, File newFile) {
                    if (file.lastModified() < newFile.lastModified()) {
                        return 1;
                    } else if (file.lastModified() == newFile.lastModified()) {
                        return 0;
                    } else {
                        return -1;
                    }

                }
            });
        }

        return list;
    }


    /**
     * 获取目录下所有文件
     *
     * @param realpath
     * @param files
     * @return
     */
    public static List<File> getFiles(String realpath, List<File> files) {

        File realFile = new File(realpath);
        if (realFile.isDirectory()) {
            File[] subfiles = realFile.listFiles();
            for (File file : subfiles) {
                if (file.isDirectory()) {
                    getFiles(file.getAbsolutePath(), files);
                } else {
                    files.add(file);
                }
            }
        }
        return files;
    }

    /**
     * 获取不带扩展名的文件名
     */
    public static String getFileNameNoEx(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot > -1) && (dot < (filename.length()))) {
                return filename.substring(0, dot);
            }
        }
        return filename;
    }

    /**
     * Java文件操作 获取文件扩展名
     */
    public static String getExtensionName(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot > -1) && (dot < (filename.length() - 1))) {
                return filename.substring(dot + 1);
            }
        }
        return filename;
    }

    public static String removerRootPath(String path) {
        if (!TextUtils.isEmpty(path) && path.startsWith("/storage/emulated/0")) {
            path = path.replaceAll("/storage/emulated/0", "");
        }
        return path;
    }

    public static void createFolders(final String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 获取指定目录下文件列表，并按修改时间排序
     */
    public static File[] getSortedFileList(String filePath) {
        File file = new File(filePath);
        File[] files = file.listFiles();
        Arrays.sort(files, new Comparator<File>() {
            public int compare(File f1, File f2) {
                long diff = f1.lastModified() - f2.lastModified();
                if (diff > 0)
                    return 1;
                else if (diff == 0)
                    return 0;
                else
                    return -1;//如果 if 中修改为 返回-1 同时此处修改为返回 1  排序就会是递减
            }

            public boolean equals(Object obj) {
                return true;
            }

        });
        return files;
    }
}