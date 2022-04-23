package com.example.hinge;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.hinge.databinding.ActivityMainBinding;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'hinge' library on application startup.
    static {
        System.loadLibrary("hinge");
    }

    private ActivityMainBinding binding;
    static final int REQUEST_IMAGE_CAPTURE = 1;

    String currentPhotoPath;

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
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
            Log.w(this.getClass().getSimpleName(), "Error creating image file!", ex);
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
            startSimple(BitmapFactory.decodeFile(currentPhotoPath));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.getRoot().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
            }
        });
        // Example of a call to a native method
//        TextView tv = binding.sampleText;
//        tv.setText(stringFromJNI());
        startSimple(null);
    }

    public void startSimple(Bitmap bitmap) {
        long start = System.currentTimeMillis();
        long now;

        ImageView imageView = binding.sample;
        ImageView imageView2 = binding.sample2;
        AssetManager assetManager = getAssets();
        //declaration of inputStream in try-with-resources statement will automatically close inputStream
        // ==> no explicit inputStream.close() in additional block finally {...} necessary
        if(bitmap == null) {
            try (InputStream inputStream = assetManager.open("tirelion.jpeg")) {
                now = System.currentTimeMillis();
                Log.i(this.getClass().getSimpleName(), "before Decode Stream (" + (now - start) + "ms)");
                start = now;
                /**
                 * reading and decoding this InputStream takes ~134ms on my Pixel 2 XL!!!
                 */
                bitmap = BitmapFactory.decodeStream(inputStream);
                now = System.currentTimeMillis();
                Log.i(this.getClass().getSimpleName(), "Decode Stream (" + (now - start) + "ms)  width:" + bitmap.getWidth() + " | height:" + bitmap.getHeight());
                start = now;
            } catch (IOException e) {
                Log.i(this.getClass().getSimpleName(), "error loading image...", e);
            }
        }

        imageView.setImageBitmap(bitmap);

        /**
         * only 6ms in Java
         */
        //imageView.setImageBitmap(getResizedBitmap(bitmap));

        //attempt to resize image in native code
        if(bitmap != null) {
            now = System.currentTimeMillis();
            Log.i(this.getClass().getSimpleName(), "before byte convert (" + (now - start) + "ms)");
            start = now;
            /**
             * only ~18ms
             */
            byte[] byteArray = convertBitmapToByteArray(bitmap);
            now = System.currentTimeMillis();
            Log.i(this.getClass().getSimpleName(), "bytes converted (" + (now - start) + "ms)");
            start = now;
            /**
             * total time is ~50ms, but native code shows ~31ms, so we're losing 38% to overhead (19ms)!?
             */
            byte[] resizedImageBytes = performImageResize(byteArray);
            now = System.currentTimeMillis();
            Log.i(this.getClass().getSimpleName(), "Resized (" + (now - start) + "ms)  length:" + resizedImageBytes.length);
            start = now;
            //check some bytes...
//            for(int i = (resizedImageBytes.length - 10);  i < resizedImageBytes.length; i++) {
//                Log.i(this.getClass().getSimpleName(), "" + resizedImageBytes[i]);
//            }
            /**
             * very fast, ~1ms
             */
            bitmap = BitmapFactory.decodeByteArray(resizedImageBytes, 0, resizedImageBytes.length);
            now = System.currentTimeMillis();
            //Log.i(this.getClass().getSimpleName(), "Decode Byte Array (" + (now - start) + "ms)  width:" + bitmap.getWidth() + " | height:" + bitmap.getHeight());
            Log.i(this.getClass().getSimpleName(), "Resized (" + (now - start) + "ms)");
            start = now;
            //ByteBuffer buffer = ByteBuffer.wrap(resizedImageBytes);
            //bitmap.copyPixelsFromBuffer(buffer);
            imageView2.setImageBitmap(bitmap);
        } else {
            Log.w(this.getClass().getSimpleName(), "bitmap was null!");
        }
    }

    public static Bitmap getResizedBitmap(Bitmap image) {
        return Bitmap.createScaledBitmap(image, 640, 480, true);
    }

    public static byte[] convertBitmapToByteArray(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(bitmap.getByteCount());
        bitmap.copyPixelsToBuffer(byteBuffer);
        byteBuffer.rewind();
        byte[] buffer = byteBuffer.array();
        Log.i(MainActivity.class.getSimpleName(), "length:" + buffer.length);
        //check some bytes...
//        for(int i = (buffer.length - 10);  i < buffer.length; i++) {
//            Log.i(MainActivity.class.getSimpleName(), "" + buffer[i]);
//        }
        return buffer;
    }

    /**
     * A native method that is implemented by the 'hinge' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    public native byte[] performImageResize(byte[] bitmap);
}