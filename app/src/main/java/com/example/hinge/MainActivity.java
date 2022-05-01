package com.example.hinge;

import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.hinge.databinding.ActivityMainBinding;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Instant;

public class MainActivity extends AppCompatActivity {
    public final String LOG_TAG = this.getClass().getSimpleName();

    // Used to load the 'hinge' library on application startup.
    static {
        System.loadLibrary("hinge");
    }

    private final Handler updateHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            imageView.setImageBitmap(imageOriginal);
            imageView.setImageBitmap(imageArtResized);
            imageView2.setImageBitmap(imageNativeResized);
        }
    };

    static final int REQUEST_IMAGE_CAPTURE = 1;

    String currentPhotoPath;
    ImageView imageView;
    ImageView imageView2;
    Bitmap imageOriginal;
    Bitmap imageNativeResized;
    Bitmap imageArtResized;

    private AssetManager mgr;

    private File createImageFile() throws IOException {
        // Create an image file name
        String imageFileName = "JPEG_" + Instant.now() + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Create the File where the photo should go
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            // Error occurred while creating the File
            Log.w(LOG_TAG, "Error creating image file!", ex);
        }
        // Continue only if the File was successfully created
        if (photoFile != null) {
            Uri photoURI = FileProvider.getUriForFile(this,
                    "com.example.android.fileprovider",
                    photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            new Thread(() -> {
                Looper.prepare();
                startSimple(BitmapFactory.decodeFile(currentPhotoPath));
                updateHandler.sendEmptyMessage(0);
            }).start();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mgr = getAssets();

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.getRoot().setOnClickListener(view -> dispatchTakePictureIntent());

        imageView = binding.sample;
        imageView2 = binding.sample2;

        new Thread(() -> {
            Looper.prepare();
            startSimple(null);
            updateHandler.sendEmptyMessage(0);
        }).start();
    }

    public void startSimple(Bitmap bitmap) {
        LogTimer logTimer = new LogTimer();

        //AssetManager assetManager = getAssets();
        if(bitmap == null) {
            try (InputStream inputStream = mgr.open("tirelion.jpeg")) {
                Log.e(LOG_TAG, "Decoding asset InputStream... (" + logTimer + ")");
                //reading and decoding this InputStream takes ~134ms on my Pixel 2 XL!!!
                imageOriginal = BitmapFactory.decodeStream(inputStream);
                Log.e(LOG_TAG, "InputStream decode complete (" + logTimer + ")  width:" + imageOriginal.getWidth() + " | height:" + imageOriginal.getHeight());
            } catch (IOException e) {
                Log.e(LOG_TAG, "error loading image...", e);
            }
        } else {
            imageOriginal = bitmap;
        }

        //write file to disk
//        try {
//            saveBitmap(imageOriginal);
//        } catch(Exception e) {
//            Log.e(LOG_TAG, "Can't save file!", e);
//        }

        //only 6ms in Java
        imageArtResized = getResizedBitmap(imageOriginal);
        Log.e(LOG_TAG, "ART image resize complete (" + logTimer + ")");

        //attempt to resize image in native code
        if(imageOriginal != null) {
            Log.e(LOG_TAG, "Converting Bitmap to byte[]... (" + logTimer + ")");
            //only ~18ms
            byte[] byteArray = convertBitmapToByteArray(imageOriginal);
            Log.e(LOG_TAG, "byte[] conversion complete (" + logTimer + ")");
            //total time is ~50ms, but native code shows ~31ms, so we're losing 38% to overhead (19ms)!?
            byte[] resizedImageBytes = performImageResize(byteArray, imageOriginal.getWidth(), imageOriginal.getHeight(), mgr);
            Log.e(LOG_TAG, "Native image resize complete (" + logTimer + ")  length:" + resizedImageBytes.length + "bytes");
            //check some bytes...
//            for(int i = (resizedImageBytes.length - 10);  i < resizedImageBytes.length; i++) {
//                Log.e(LOG_TAG, "" + resizedImageBytes[i]);
//            }
            //very fast, ~1ms
            imageNativeResized = BitmapFactory.decodeByteArray(resizedImageBytes, 0, resizedImageBytes.length);
            //Log.e(LOG_TAG, "Decode Byte Array (" + (now - start) + "ms)  width:" + bitmap.getWidth() + " | height:" + bitmap.getHeight());
            Log.e(LOG_TAG, "Decode of native resized image complete (" + logTimer + ")");
            //ByteBuffer buffer = ByteBuffer.wrap(resizedImageBytes);
            //bitmap.copyPixelsFromBuffer(buffer);
            //imageView2.setImageBitmap(imageNativeResized);
        } else {
            Log.w(LOG_TAG, "bitmap was null!");
        }
    }

    public static Bitmap getResizedBitmap(Bitmap image) {
        //when filter param is set to 'true', filters using bilinear algorithm
        return Bitmap.createScaledBitmap(image, 640, 480, true);
    }

    public static byte[] convertBitmapToByteArray(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(bitmap.getByteCount());
        bitmap.copyPixelsToBuffer(byteBuffer);
        byteBuffer.rewind();
        byte[] buffer = byteBuffer.array();
        Log.e(MainActivity.class.getSimpleName(), "length:" + buffer.length + "bytes");
        //check some bytes...
//        for(int i = (buffer.length - 10);  i < buffer.length; i++) {
//            Log.e(MainActivity.class.getSimpleName(), "" + buffer[i]);
//        }
        return buffer;
    }

    public static File savebitmap(Bitmap bmp) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 60, bytes);
        File f = new File(Environment.getExternalStorageDirectory()
                + File.separator + "testimage.jpg");
        f.createNewFile();
        FileOutputStream fo = new FileOutputStream(f);
        fo.write(bytes.toByteArray());
        fo.close();
        return f;
    }

    public static boolean saveBitmap(Bitmap bmp) throws IOException {
        String filepath = Environment.getExternalStorageDirectory()
                + File.separator + "Download" + File.separator + "testimage2.bmp";
        return AndroidBitmapUtil.save(bmp, filepath);
    }

    public native byte[] performImageResize(byte[] bitmap, int width, int height, AssetManager amgr);
}