//
// Created by hengfeng zhuo on 2019/3/25.
//
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <istream>

#include <libyuv.h>
using namespace libyuv;

extern "C"
JNIEXPORT jint JNICALL Java_com_rokid_simpleplayer_gl_YUVHelper_yuvI420ToNV21 (
        JNIEnv *env, jclass type,
        jbyteArray i420Src,
        jbyteArray nv21Src,
        jint width, jint height)
{
    jbyte *src_i420_data = env->GetByteArrayElements(i420Src, NULL);
    jbyte *src_nv21_data = env->GetByteArrayElements(nv21Src, NULL);

    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_y_size + src_u_size;

    jbyte *src_nv21_y_data = src_nv21_data;
    jbyte *src_nv21_vu_data = src_nv21_data + src_y_size;

    libyuv::I420ToNV21(
            (const uint8 *) src_i420_y_data, width,
            (const uint8 *) src_i420_u_data, width >> 1,
            (const uint8 *) src_i420_v_data, width >> 1,
            (uint8 *) src_nv21_y_data, width,
            (uint8 *) src_nv21_vu_data, width,
            width, height);

    env->ReleaseByteArrayElements(i420Src, src_i420_data, 0);
    env->ReleaseByteArrayElements(nv21Src, src_nv21_data, 0);
    return 0;
}