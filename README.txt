This project is meant to demonstrate OpenCV bilinear filtering performance.  The test device used
was a Pixel 2 XL.

Native execution:
Within the native lib, there are some inefficiencies introduced with OpenCV which I'm not sure
how to avoid.  The image resizing alone takes approximately ~7ms.  Compared with the Android
SDK built-in method Bitmap, the performance is nearly the same, with the Android SDK perhaps being
micro seconds faster than OpenCV.  The color channels were reversed after performing filtering and
resize operation on the image with OpenCV, which necessitated a reversal of the change back to
default RGBA channel order (OpenCV defaults to BGR as stated in the documentation, but I could not
yet find a way to circumvent this during the resize operation).  The color channel conversion
took an additional 6ms, effectively doubling the time necessary to resize the image.  Next, there
is a bitmap encoding step which may be unnecessary, but without this step, the image was distorted.
The encoding and vector reassignment is rather expensive and adds another 20ms to the native code
execution time.  There is likely a way to avoid this step, but I do not know how at this time.  The
total execution time of the native code clocks in at around 31ms.  However, from the point where
Java makes the JNI call to the time at which execution returns to Java execution, ~50ms has passed,
which suggests some 38% overhead lost somewhere.  However, when we perform only the resize
operation and return only the original byte array passed in to JNI with no changes, then the
time the execution returns to Java agrees with the resize operation in the native code at 6ms.
I assume that there is some memory reclamation or other cleanup in the background which causes
the extra delay introduced when performing more operations within the NDK.


2022-04-23 17:48:49.381 2826-2826/com.example.hinge I/MainActivity: before Decode Stream (0ms)
2022-04-23 17:48:49.513 2826-2826/com.example.hinge I/MainActivity: Decode Stream (131ms)  width:3840 | height:2160
2022-04-23 17:48:49.513 2826-2826/com.example.hinge I/MainActivity: before byte convert (6ms)
2022-04-23 17:48:49.530 2826-2826/com.example.hinge W/m.example.hing: JNI critical lock held for 16.691ms on Thread[1,tid=2826,Runnable,Thread*=0x7e15345380,peer=0x71e99338,"main"]
2022-04-23 17:48:49.531 2826-2826/com.example.hinge I/MainActivity: length:33177600
2022-04-23 17:48:49.531 2826-2826/com.example.hinge I/MainActivity: bytes converted (18ms)
2022-04-23 17:48:49.531 2826-2826/com.example.hinge E/native-lib::: Image init(13 us)  width--3840 || height--2160
2022-04-23 17:48:49.538 2826-2826/com.example.hinge E/native-lib::: Image resize(6850 us)  width--640 || height--480
2022-04-23 17:48:49.544 2826-2826/com.example.hinge E/native-lib::: Image cvtColor(6032 us)
2022-04-23 17:48:49.548 2826-2826/com.example.hinge E/native-lib::: Image imgencode(3968 us)
2022-04-23 17:48:49.561 2826-2826/com.example.hinge E/native-lib::: Image vAssign(13546 us)
2022-04-23 17:48:49.562 2826-2826/com.example.hinge E/native-lib::: Image jByteAssign(566 us)
2022-04-23 17:48:49.562 2826-2826/com.example.hinge E/native-lib::: Total(30975 us)
2022-04-23 17:48:49.582 2826-2826/com.example.hinge I/MainActivity: Resized (50ms)  length:921654
2022-04-23 17:48:49.582 2826-2826/com.example.hinge I/MainActivity: Decode Byte Array (1ms)  width:640 | height:480


Comparison:
If we assume that only the OpenCV resize operation is necessary (which is most likely the case),
then it would be fair to compare NDK/OpenCV performance considering only the execution time of the
resize operation.  Since the operations take almost the exact same amount of time, it seems that
using the NDK adds an unnecessary level of complexity for no returns in the form of performance
for this simple example.