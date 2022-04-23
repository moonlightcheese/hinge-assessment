#include <jni.h>
#include <string>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/imgcodecs.hpp>
#include <iostream>
#include <android/log.h>

using namespace std;
using namespace cv;

#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, "native-lib::", __VA_ARGS__))

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_hinge_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_hinge_MainActivity_performImageResize(
        JNIEnv *env,
        jobject thiz,
        jbyteArray bitmap) {
    // TODO: implement performImageResize()
    struct timeval Time;
    gettimeofday( &Time, NULL );
    uint64_t startTime = (Time.tv_sec * 1000000) + Time.tv_usec;
    uint64_t entryTime = startTime;

    jbyte* bytes = env->GetByteArrayElements(bitmap, 0);
    Mat image = Mat(2160, 3840, CV_8UC4, bytes);

    gettimeofday( &Time, NULL );
    uint64_t usecs = ((Time.tv_sec * 1000000) + Time.tv_usec) - startTime;
    //about ~14-16us after start, very fast
    LOGE("Image init(%d us)  width--%d || height--%d", usecs, image.cols, image.rows);
    startTime = (Time.tv_sec * 1000000) + Time.tv_usec;

    Size newSize = Size(640, 480);
    Mat resultImage;
    //resize down
    try {
        resize(image, resultImage, newSize);
    } catch(const exception& e) {
        cerr << e.what() << endl;
    }

    gettimeofday( &Time, NULL );
    usecs = ((Time.tv_sec * 1000000) + Time.tv_usec) - startTime;
    //about ~7435us after start
    LOGE("Image resize(%d us)  width--%d || height--%d", usecs, resultImage.cols, resultImage.rows);
    startTime = (Time.tv_sec * 1000000) + Time.tv_usec;

    Mat colorCorrectImage;

    //TODO: how to avoid this???  OpenCV wants to convert to BGR, and this is expensive!
    cv::cvtColor(resultImage, colorCorrectImage, COLOR_BGR2RGB);

    gettimeofday( &Time, NULL );
    usecs = ((Time.tv_sec * 1000000) + Time.tv_usec) - startTime;
    //about >15,000us after start
    LOGE("Image cvtColor(%d us)", usecs);
    startTime = (Time.tv_sec * 1000000) + Time.tv_usec;

    std::vector<unsigned char> imageDesV;
    cv::imencode(".bmp", colorCorrectImage, imageDesV);

    gettimeofday( &Time, NULL );
    usecs = ((Time.tv_sec * 1000000) + Time.tv_usec) - startTime;
    //about >19,000us after start
    LOGE("Image imgencode(%d us)", usecs);
    startTime = (Time.tv_sec * 1000000) + Time.tv_usec;

    //convert vector<char> to jbyteArray
    jbyte* result_e = new jbyte[imageDesV.size()];
    for (int i = 0; i < imageDesV.size(); i++) {
        result_e[i] = (jbyte)imageDesV[i];
    }

    gettimeofday( &Time, NULL );
    usecs = ((Time.tv_sec * 1000000) + Time.tv_usec) - startTime;
    //about >33,000us after start
    LOGE("Image vAssign(%d us)", usecs);
    startTime = (Time.tv_sec * 1000000) + Time.tv_usec;
    jbyteArray result = env->NewByteArray(imageDesV.size());

    env->SetByteArrayRegion(result, 0, imageDesV.size(), result_e);

    gettimeofday( &Time, NULL );
    usecs = ((Time.tv_sec * 1000000) + Time.tv_usec) - startTime;
    //about >34,000us after start
    LOGE("Image jByteAssign(%d us)", usecs);
    startTime = (Time.tv_sec * 1000000) + Time.tv_usec;

    LOGE("Total(%d us)", (startTime - entryTime));
    return result;
}