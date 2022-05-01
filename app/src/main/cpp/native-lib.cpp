#include <jni.h>
#include <string>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>
#include <iostream>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

using namespace std;
using namespace cv;

#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, "native-lib::", __VA_ARGS__))

jbyteArray matToByteArray(JNIEnv *env, const cv::Mat &image) {
    jbyteArray resultImage = env->NewByteArray(image.total() * 4);
    jbyte *_data = new jbyte[image.total() * 4];
    for (int i = 0; i < image.total() * 4; i++) {
        _data[i] = image.data[i];
    }
    env->SetByteArrayRegion(resultImage, 0, image.total() * 4, _data);
    delete[]_data;

    return resultImage;
}

jintArray matToBitmapIntArray(JNIEnv *env, const cv::Mat &image) {
    jintArray resultImage = env->NewIntArray(image.total());
    jint *_data = new jint[image.total()];
    for (int i = 0; i < image.total(); i++) {
        char r = image.data[4 * i + 2];
        char g = image.data[4 * i + 1];
        char b = image.data[4 * i + 0];
        char a = image.data[4 * i + 3];
        _data[i] = (((jint) a << 24) & 0xFF000000) + (((jint) r << 16) & 0x00FF0000) +
                   (((jint) g << 8) & 0x0000FF00) + ((jint) b & 0x000000FF);
    }
    env->SetIntArrayRegion(resultImage, 0, image.total(), _data);
    delete[]_data;

    return resultImage;
}

jbyteArray matToBitmapByteArray(JNIEnv *env, const cv::Mat &image) {
    jbyteArray resultImage = env->NewByteArray(image.total());
    jbyte *_data = new jbyte[image.total()];
    for (int i = 0; i < image.total(); i++) {
        char r = image.data[4 * i + 2];
        char g = image.data[4 * i + 1];
        char b = image.data[4 * i + 0];
        char a = image.data[4 * i + 3];
        _data[i] = (((jbyte) a << 6) & 0xFF000000) + (((jbyte) r << 4) & 0x00FF0000) +
                   (((jbyte) g << 2) & 0x0000FF00) + ((jbyte) b & 0x000000FF);
    }
    env->SetByteArrayRegion(resultImage, 0, image.total(), _data);
    delete[]_data;

    return resultImage;
}

class LogTimer {
    timeval init {};
    timeval previous {};
    timeval now {};

public:
    LogTimer() {
        gettimeofday(&init, nullptr);
        previous = init;
        now = init;
    }

    timeval mark() {
        previous = now;
        gettimeofday(&now, nullptr);
        return now;
    }

    uint64_t getTimeSinceInit() const {
        uint64_t startTime = (init.tv_sec * 1000000) + init.tv_usec;
        uint64_t endTime = (now.tv_sec * 1000000) + now.tv_usec;
        return endTime - startTime;
    }

    uint64_t getTimeSincePrevious() const {
        uint64_t startTime = (previous.tv_sec * 1000000) + previous.tv_usec;
        uint64_t endTime = (now.tv_sec * 1000000) + now.tv_usec;
        return endTime - startTime;
    }
};

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_hinge_MainActivity_performImageResize(
        JNIEnv *env,
        jobject thiz,
        jbyteArray bitmap,
        jint width,
        jint height,
        jobject assetManager) {
    LogTimer logTimer = LogTimer();

    jboolean isCopy {false};
    jbyte* bytes = env->GetByteArrayElements(bitmap, &isCopy);
    Mat image = Mat(height, width, CV_8UC4, bytes);

    //instead of passing in a byte array to native code, I want to try loading the image in C++
    const char* path = "tirelion.bmp";
    AAsset* asset;
    asset = AAssetManager_open(assetManager, path, AASSET_MODE_UNKNOWN);

    logTimer.mark();
    //about ~14-16us after start, very fast
    LOGE("Image init(%d us)  width--%d || height--%d", logTimer.getTimeSincePrevious(), image.cols, image.rows);

    Size newSize = Size(640, 480);
    Mat resultImage;
    //resize down
    try {
        resize(image, resultImage, newSize);
    } catch(const exception& e) {
        cerr << e.what() << endl;
    }

    logTimer.mark();
    //about ~7435us after start
    LOGE("Image resize(%d us)  width--%d || height--%d", logTimer.getTimeSincePrevious(), resultImage.cols, resultImage.rows);

    Mat colorCorrectImage;

    //TODO: how to avoid this???  OpenCV wants to convert to BGR, and this is expensive!
    cv::cvtColor(resultImage, colorCorrectImage, COLOR_BGR2RGB);

    logTimer.mark();
    //about >15,000us after start
    LOGE("Image cvtColor(%d us)", logTimer.getTimeSincePrevious());

    std::vector<unsigned char> imageDesV;
    cv::imencode(".bmp", colorCorrectImage, imageDesV);

    logTimer.mark();
    //about >19,000us after start
    LOGE("Image imgencode(%d us)", logTimer.getTimeSincePrevious());

    //convert vector<char> to jbyteArray
    jbyte* result_e = new jbyte[imageDesV.size()];
    for (int i = 0; i < imageDesV.size(); i++) {
        result_e[i] = (jbyte)imageDesV[i];
    }

    logTimer.mark();
    //about >33,000us after start
    LOGE("Image vAssign(%d us)", logTimer.getTimeSincePrevious());
    jbyteArray result = env->NewByteArray(imageDesV.size());

    env->SetByteArrayRegion(result, 0, imageDesV.size(), result_e);

    logTimer.mark();
    //about >34,000us after start
    LOGE("Image jByteAssign(%d us)", logTimer.getTimeSincePrevious());

    //jbyteArray result = matToBitmapByteArray(env, resultImage);
    LOGE("Total(%d us)", logTimer.getTimeSinceInit());
    return result;
}