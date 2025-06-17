
#include <jni.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define TAG "NativeLib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_example_myapplication_MainActivity_processFrame(
        JNIEnv* env, jobject,
        jbyteArray yBytes_, jint width, jint height) {

    jbyte* yBytes = env->GetByteArrayElements(yBytes_, nullptr);
    if (yBytes == nullptr) return nullptr;

    cv::Mat gray(height, width, CV_8UC1, reinterpret_cast<uchar*>(yBytes));

    cv::Mat blurred;
    cv::GaussianBlur(gray, blurred, cv::Size(3, 3), 1.2);

    cv::Mat edges;
    cv::Canny(blurred, edges, 30, 90);  // 🔧 lower thresholds reveal finer detail

    cv::Mat edgesBGR;
    cv::cvtColor(edges, edgesBGR, cv::COLOR_GRAY2BGR);

    int size = edgesBGR.total() * edgesBGR.elemSize();
    jbyteArray result = env->NewByteArray(size);
    env->SetByteArrayRegion(result, 0, size, reinterpret_cast<jbyte*>(edgesBGR.data));
    env->ReleaseByteArrayElements(yBytes_, yBytes, JNI_ABORT);

    return result;
}

