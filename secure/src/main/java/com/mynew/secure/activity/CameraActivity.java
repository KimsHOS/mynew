package com.mynew.secure.activity;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.secure.R;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.mynew.secure.faceantispoofing.FaceAntiSpoofing;
import com.mynew.secure.faceantispoofing.FaceSpoofDetector;
//import com.mynew.secure.mobilefacenet.MobileFaceNet;
import com.mynew.secure.utils.AchalaActions;
import com.mynew.secure.utils.AchalaSdkConfigurations;
import com.mynew.secure.utils.AchalaSecureCallback;
import com.mynew.secure.utils.AchalaSecureResultModel;
import com.mynew.secure.utils.BitmapUtils;
import com.mynew.secure.utils.Comparison;
//import com.mynew.secure.utils.FaceCircleOverlay;
import com.mynew.secure.utils.FaceNetModel;
import com.mynew.secure.utils.FileChecker;
import com.mynew.secure.utils.ModelInfo;
import com.mynew.secure.utils.Models;
import com.mynew.secure.utils.Prediction;


import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private static final int CLOSE_FACE_THRESHOLD = 200; // Adjust as needed
    private static final float YAW_THRESHOLD = 20.0f;
    private static final float PITCH_THRESHOLD = 15.0f;
    private static final float ROLL_THRESHOLD = 10.0f;
    private FaceAntiSpoofing faceAntiSpoofing;
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private int count = 0;
    private FaceNetModel model;
    private FaceSpoofDetector faceSpoofDetector;
    private float[] subject;
    private ArrayList<Pair<String, float[]>> facesList = new ArrayList<>();
    private ProgressDialog progressDialog;

    ArrayList<String> checks = new ArrayList<>(Arrays.asList(AchalaActions.Open_Eyes));
//    "smile", "blink",
    HashMap<String, Boolean> detectionResults = new HashMap<>();
    private int currentIndex = 0;
    private TextView instructionToUser, liveDetection,spoof;
    private String userGid = "Verify_User";
    private Bitmap imageFromThePath;
    private boolean isRegistration;
    private Bitmap originalBitmap;
    private FaceAntiSpoofing fas;
    //private MobileFaceNet mfn;
    private ImageButton closeCamera;
    private ObjectAnimator animator;
    private FaceDetectorOptions realTimeOpts;
    private AchalaSecureResultModel achalaSecureResultModel = new AchalaSecureResultModel();
    private AchalaSdkConfigurations achalaSdkConfigurations = new AchalaSdkConfigurations();


    private boolean isCollectingStableFrames = false;
    private static final int TOTAL_STABLE_FRAMES_TO_COLLECT = 8;
    private static final int TOTAL_QUALITY_CHECKS = 1;
    private int collectedStableFrameCount = 0;
    private int qualityCheckCount = 0;
    //FaceCircleOverlay faceCircleOverlay;
    private List<Bitmap> stableBitmaps = new ArrayList<>();
    private List<Face> stableFaces = new ArrayList<>();
    Rect faceRect1;
    private int numberOfFrame = 0;


    // Constants for the TensorFlow Lite model
    private static final int TF_OD_API_INPUT_SIZE = 224;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "mask_detector.tflite";
    private static final float MINIMUM_CONFIDENCE = 0.6f;

    //private Interpreter maskDetector;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_lib);
      //   faceCircleOverlay = findViewById(R.id.face_circle_overlay);
        //faceCircleOverlay.setRadius(500f);
        // Hide both system bars (status bar and navigation bar)
        hideSystemBars();

        // Set the icons color to dark

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        if (getIntent().getExtras().size()>0) {
            userGid = getIntent().getStringExtra("userGid");
            isRegistration = getIntent().getBooleanExtra("isRegistration", false);
            achalaSdkConfigurations = new Gson().fromJson(getIntent().getStringExtra("configurations"), AchalaSdkConfigurations.class);
            if(achalaSdkConfigurations!=null && achalaSdkConfigurations.getActions().size()>0) {
                checks.clear();
                checks.addAll(achalaSdkConfigurations.getActions());
            }
        }

        // below three lines code is for activity brightness
//        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
//        layoutParams.screenBrightness = 1.0f; // 1.0f is maximum brightness; 0.0f is minimum
//        getWindow().setAttributes(layoutParams);



        previewView = findViewById(R.id.preview_view);
        instructionToUser = findViewById(R.id.to_user);
        spoof = findViewById(R.id.spoof_tv);

        liveDetection = findViewById(R.id.live_detection);
        try {
            faceAntiSpoofing = new FaceAntiSpoofing(getAssets());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Initialize the model when the activity is created
//        try {
//            maskDetector = createInterpreter(this);
//        } catch (IOException e) {
//            e.printStackTrace();
//            // Handle initialization error (e.g., show error message or log)
//        }

        previewView.setScaleX(-1);
        closeCamera = findViewById(R.id.close_camera);
        cameraExecutor = Executors.newSingleThreadExecutor();
        if(!isRegistration) {
            if (!achalaSdkConfigurations.getVerifyImageURL().isEmpty())
                imageFromThePath = new FileChecker().getBitmapFromURL(achalaSdkConfigurations.getVerifyImageURL());
            else {
                originalBitmap = achalaSdkConfigurations.getVerifyImageBitmap();
                Bitmap copiedBitmap = originalBitmap.copy(originalBitmap.getConfig(), true);
                imageFromThePath = copiedBitmap;
            }
        }



        initAntiSpoofing();
        startStableFrameCollection();

        try {
            ModelInfo modelInfo = Models.FACENET;
            model = new FaceNetModel(CameraActivity.this, modelInfo, false, true);
            Log.d("FaceNetModel", "Model initialized successfully");
        } catch (Exception e) {
            Log.e("FaceNetModel", "Model initialization failed", e);
        }

        try {

            faceSpoofDetector = new FaceSpoofDetector(CameraActivity.this, false, false, false,getAssets());
            Log.d("FaceSpoofDetector", "FaceSpoofDetector Model initialized successfully");
        } catch (Exception e) {
            Log.e("FaceSpoofDetector", "FaceSpoofDetector Model initialization failed", e);
        }

        // Initialize detection results
        for (String check : checks) {
            detectionResults.put(check, false); // Initially set all checks to false
        }
        currentIndex = 0;

        closeCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startCamera();
            }
        });

        // Hide both system UI and navigation bar
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    private void initAntiSpoofing() {
        try {
            fas = new FaceAntiSpoofing(getAssets());
            //mfn = new MobileFaceNet(getAssets());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                try {
                    bindPreview(cameraProvider);
                } catch (Exception e) {
                    Log.d("TAG", "startCamera: "+e.getMessage());
                    throw new RuntimeException(e);
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Camera initialization error", e);
            }
        },
                ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();

        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT ).build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::processImage);
        preview.setSurfaceProvider(previewView.getSurfaceProvider());


        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }


    @OptIn(markerClass = ExperimentalGetImage.class)
    public static Bitmap imageProxyToBitsdsadfasmap(ImageProxy image) {
        Image img = image.getImage();
        if (img == null) return null;

        byte[] nv21 = yuv420ToNv21(img);
        return  rotateBitmap(YuvImageToBitmap(nv21, img.getWidth(), img.getHeight()),image.getImageInfo().getRotationDegrees());
    }

    private static byte[] yuv420ToNv21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 4;

        byte[] nv21 = new byte[ySize + uvSize * 2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int yRowStride = image.getPlanes()[0].getRowStride();
        int uvRowStride = image.getPlanes()[1].getRowStride();
        int uvPixelStride = image.getPlanes()[1].getPixelStride();

        int pos = 0;

        // Copy Y plane
        for (int row = 0; row < height; row++) {
            yBuffer.position(row * yRowStride);
            yBuffer.get(nv21, pos, width);
            pos += width;
        }

        // Copy UV planes
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int uIndex = row * uvRowStride + col * uvPixelStride;
                int vIndex = row * uvRowStride + col * uvPixelStride;
                nv21[pos++] = vBuffer.get(vIndex);
                nv21[pos++] = uBuffer.get(uIndex);
            }
        }

        return nv21;
    }

    private static Bitmap YuvImageToBitmap(byte[] nv21, int width, int height) {
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, out);
        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);




    }
    private static Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        if (degrees == 0) return bitmap;

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImage(@NonNull ImageProxy imageProxy) {
        try {
            if (imageProxy.getImage() == null) {
                return;
            }
            //saveImage(imageProxy);
            Image sample = imageProxy.getImage();




            Log.d("processImage: ", "rotation" + imageProxy.getImageInfo().getRotationDegrees());
            InputImage inputImage = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
            FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // Enable classification
                    //.setTrackingEnabled(true)
                    .enableTracking()
                    .build();
            originalBitmap = imageProxyToBitsdsadfasmap(imageProxy);
         /*   try {
                runOnUiThread(() -> actualImagePreviewBitmap.setImageBitmap(originalBitmap));
            }catch (Exception e){
                Log.d("TAG", "processImage: "+ e.getMessage());
            }
*/

//            assert originalBitmap != null;
//            int byteCount = originalBitmap.getByteCount();  // or bitmap.getAllocationByteCount() for Android 4.4+
//            double sizeInKB = byteCount / 1024.0;
//            double sizeInMB = sizeInKB / 1024.0;
//
//            Log.d("BitmapMemory", "Bitmap size in memory: " + sizeInKB + " KB (" + sizeInMB + " MB)");



//            ByteArrayOutputStream stream = new ByteArrayOutputStream();
//            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
//            byte[] byteArray = stream.toByteArray();
//            double compressedKB = byteArray.length / 1024.0;
//            double compressedMB = compressedKB / 1024.0;
//
//            Log.d("BitmapCompressedSize", "JPEG size: " + compressedKB + " KB (" + compressedMB + " MB)");
//


            //objectDetection(originalBitmap);
            FaceDetector detector = FaceDetection.getClient(options);
            Task<List<Face>> result = detector.process(inputImage)
                    .addOnSuccessListener(faces -> {

                        if(!faces.isEmpty() && numberOfFrame<10){
                            numberOfFrame ++;
                            return;
                        }

//                        try {
//                            Face biggestFace = null;
//                            int maxArea = 0;
//
//                            for (Face face : faces) {
//                                if (face != null && face.getBoundingBox() != null) {
//                                    Rect bounds = face.getBoundingBox();
//                                    int area = bounds.width() * bounds.height();
//
//                                    if (area > maxArea) {
//                                        maxArea = area;
//                                        biggestFace = face;
//                                    }
//                                }
//                            }
//
//                            faces.clear();
//                            if (biggestFace != null) {
//                                faces.add(biggestFace);
//                            }
//                        } catch (Exception e) {
//                            throw new RuntimeException(e);
//                        }
//                        if (!faces.isEmpty()) {
//                            Face face = faces.get(0);
//                            Rect faceRect = face.getBoundingBox();
//
//                            if (faceRect == null) {
//                                instructionToUser.setText("Face bounding box not available. Please adjust your position.");
//                                return;
//                            }
//
//                            // ✅ Now safe to call
//                            if (!isFaceCenterInsideCircle(faceRect,previewView, faceCircleOverlay)) {
//                                instructionToUser.setText("Align your face completely inside the circle");
//                                return;
//                            }
//
//                            // Now proceed with your switch-case and checks
//                        } else {
//                            instructionToUser.setText("No face detected. Please look into the camera.");
//                        }

                        String distance;
                        if (faces.isEmpty()) {
                            instructionToUser.setText("Please look in to the camera");
                        } else if (faces.size() > 1) {
                            instructionToUser.setText("more than one face found");
                        } else {
                            instructionToUser.setText("");
                            switch (getFaceDistanceCategory(faces.get(0).getBoundingBox(),inputImage.getWidth())){
                                case "medium":
                                    if (isFaceLookingStraight(faces.get(0))) {
                                        instructionToUser.setText("");
                                        Log.d("faces", "see " + faces);

                                        if (currentIndex < checks.size()) {
                                            if (checks.get(currentIndex).equals("smile") && !instructionToUser.getText().toString().equalsIgnoreCase("Please smile into the camera")) {
                                                instructionToUser.setText("Please smile into the camera");
                                            } else if (checks.get(currentIndex).equals("blink") && !instructionToUser.getText().toString().equalsIgnoreCase("Please blink eyes into the camera")) {
                                                instructionToUser.setText("Please blink eyes into the camera");
                                            } else if (checks.get(currentIndex).equals("open_eyes") && !instructionToUser.getText().toString().equalsIgnoreCase("Please blink eyes into the camera")) {
                                                instructionToUser.setText("Please look in to the camera");
                                            }
                                            try {
                                                livenessChecks(faces);
                                            } catch (Exception e) {

                                            }
                                        } else {
                                            if (checkImageStability(faces.get(0)) || true) {
                                                Face face = faces.get(0);
                                                Rect faceRect = face.getBoundingBox();



//                                                    // 🔆 Brightness check
//                                                    float brightness = calculateBrightness(faceBitmap);
//                                                    Log.d("BrightnessCheck", "Brightness Score: " + brightness);
//
//                                                    if (brightness < 50) {
//                                                        instructionToUser.setText("Too dark. Improve lighting.");
//                                                        return;
//                                                    } else if (brightness > 230) {
//                                                        instructionToUser.setText("Too bright. Avoid direct light.");
//                                                        return;
//                                                    }

//                                                    // 🧠 Spoof detection
//                                                    if (faceAntiSpoofing != null) {
//                                                        float score = faceAntiSpoofing.antiSpoofing(faceBitmap);
//                                                        Log.d("SpoofScore", "Score: " + score);
//                                                    } else {
//                                                        Log.e("FaceAntiSpoofing", "faceAntiSpoofing was not initialized");
//                                                    }
//
//                                                    // 🔍 Blur check
//                                                    int clarityScore = faceAntiSpoofing.laplacian(faceBitmap);
//                                                    Log.d("ClarityCheck", "Clarity Score: " + clarityScore);
//                                                    if (clarityScore < 850) {
//                                                        instructionToUser.setText("Image is too blurry. Move closer.");
//                                                        return;
//                                                    }
                                                Log.d("processImageSmile", "processImageSmile: "+face.getSmilingProbability());
                                                Bitmap faceBitmap = cropFaces(originalBitmap, face); // Crop face


                                                    // 👁️ Eyes open + Quality + Comparison
                                                    if (detectEyesOpen(face,faceBitmap) && collectedStableFrameCount <= TOTAL_STABLE_FRAMES_TO_COLLECT) {
                                                        collectedStableFrameCount++;
                                                       // originalBitmap = applyWDRIfNeeded(originalBitmap);

//                                                        String s =  bitmapToBase64(faceBitmap);
//
//                                                        Log.d("TAG", "processImage: " + s );


//                                                        String s1 =  bitmapToBase64(faceBitmap);
//
//                                                        Log.d("TAG", "processImage: " + s1 );
                                                            if (checkQualityWithModel(faceBitmap, face) && qualityCheckTwo(faceBitmap,face)) {
                                                                qualityCheckCount++;
                                                                if (qualityCheckCount == TOTAL_QUALITY_CHECKS) {
                                                                    compareFaces(faces, originalBitmap);
                                                                }
                                                            } else {
                                                                qualityCheckCount = 0;
                                                            }

                                                    } else {
                                                        if (collectedStableFrameCount >= TOTAL_STABLE_FRAMES_TO_COLLECT) {
                                                            returnQualityCheckFailed();
                                                        }
                                                        instructionToUser.setText("Please look into the camera");
                                                    }


                                            }
                                        }



                                        ///runModel(faces, originalBitmap);
                                        if (originalBitmap != null) {
                                             //Draw face boxes and save the image
                                             //Bitmap bitmapWithBoxes = drawFaceBoxes(originalBitmap, faces);
                                            //saveBitmap(bitmapWithBoxes);
                                            //saveBitmap(originalBitmap);
                                        }
                                    } else {
                                        instructionToUser.setText("Please look straight into the camera");
                                    }
                                    break;
                                case "close":
                                    instructionToUser.setText("Please move away to the camera");
                                    break;
                                case "far":
                                    instructionToUser.setText("Please move closer to the camera");
                                    break;
                                }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("Face Detection", "Face detection failed", e);
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        } catch (Exception e) {
            imageProxy.close();
            Log.e("CameraX", "Error processing image", e);
        }
    }

    private boolean qualityCheckTwo(Bitmap bitmaps, Face face) {
                FaceSpoofDetector.FaceSpoofResult ad = faceSpoofDetector.detectSpoof(bitmaps, face.getBoundingBox());
        Log.d("TAG", "checkQualityWithModelSpoof: "+ad.isSpoof() + " _ " + ad.getScore());

        //spoof.setText(ad.isSpoof()+" - "+ad.getScore());
      //  Toast.makeText(this, ad.isSpoof() + " _ " + ad.getScore() , Toast.LENGTH_SHORT).show();

        boolean allow = (!ad.isSpoof() && ad.getScore()>0.8);
        if(!allow)
            runOnUiThread(() -> instructionToUser.setText("There is heavy light in the background. Please adjust to proper lighting, as your face is not clearly visible."));
        return allow;

    }
//    private float calculateBrightness(Bitmap bitmap) {
//        long totalBrightness = 0;
//        int width = bitmap.getWidth();
//        int height = bitmap.getHeight();
//        int[] pixels = new int[width * height];
//        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
//
//        for (int pixel : pixels) {
//            int r = (pixel >> 16) & 0xff;
//            int g = (pixel >> 8) & 0xff;
//            int b = pixel & 0xff;
//            int brightness = (r + g + b) / 3;
//            totalBrightness += brightness;
//        }
//
//        return totalBrightness / (float) pixels.length;
//    }
//    public int brightnessScore(Bitmap bitmap) {
//        long totalBrightness = 0;
//        int width = bitmap.getWidth();
//        int height = bitmap.getHeight();
//        int pixelCount = width * height;
//
//        for (int x = 0; x < width; x += 4) { // Skip every 4th pixel for speed
//            for (int y = 0; y < height; y += 4) {
//                int pixel = bitmap.getPixel(x, y);
//                int r = Color.red(pixel);
//                int g = Color.green(pixel);
//                int b = Color.blue(pixel);
//
//                // Convert to perceived brightness (luminance)
//                int brightness = (int) (0.299 * r + 0.587 * g + 0.114 * b);
//                totalBrightness += brightness;
//            }
//        }
//
//        int sampleCount = (width / 4) * (height / 4);
//        return (int) (totalBrightness / sampleCount);
//    }
//    private boolean isFaceFullyInsideCircle(Rect faceRect, FaceCircleOverlay overlay) {
//        float circleX = overlay.getCenterX();
//        float circleY = overlay.getCenterY();
//        float radius = overlay.getRadius();
//
//        float left = faceRect.left;
//        float top = faceRect.top;
//        float right = faceRect.right;
//        float bottom = faceRect.bottom;
//
//        return isPointInsideCircle(left, top, circleX, circleY, radius)
//                && isPointInsideCircle(right, top, circleX, circleY, radius)
//                && isPointInsideCircle(left, bottom, circleX, circleY, radius)
//                && isPointInsideCircle(right, bottom, circleX, circleY, radius);
//    }

    private boolean isPointInsideCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }
    private void returnQualityCheckFailed() {

        AchalaSecureCallback achalaSecureCallback = new AchalaSecureCallback() {

            @Override
            public void onCompareSuccess(String result, String score) {
                hideProgress();
                Log.d("result after compilation", result);

                achalaSecureResultModel.setScore(score);
                achalaSecureResultModel.setBitmapResult(originalBitmap);
                achalaSecureResultModel.setStatus("SUCCESS");
                if(isRegistration)
                    achalaSecureResultModel.setMessage("Registered Successfully");
                else
                    achalaSecureResultModel.setMessage("Authentication Successful");

                finishCameraLauncher(result);
            }

            @Override
            public void onCompareFailed(String e) {
                achalaSecureResultModel.setScore("0.0");
                achalaSecureResultModel.setBitmapResult(null);
                achalaSecureResultModel.setStatus("FAILED - Quality Detection Failed");
                if(isRegistration)
                    achalaSecureResultModel.setMessage("Registered Failed " + e);
                else
                    achalaSecureResultModel.setMessage("Authentication Failed " + e);

                finishCameraLauncher(e);
            }
        };

        achalaSecureCallback.onCompareFailed("Quality Detection Failed");


    }

    private Mat convertBitmapToMat(Bitmap originalBitmap) {
        // Check if the bitmap is null or empty
        if (originalBitmap == null || originalBitmap.getWidth() == 0 || originalBitmap.getHeight() == 0) {
            Log.e("Bitmap Error", "Bitmap is null or empty");
            return new Mat(); // Return an empty Mat
        }

        // Create a Mat to hold the converted image
        Mat mat = new Mat();

        // Convert Bitmap to Mat
        Utils.bitmapToMat(originalBitmap, mat);

        // Check if the conversion was successful
        if (mat.empty()) {
            Log.e("Mat Conversion", "Mat is empty after conversion");
        }


        convertMatToBitmap(mat);

        return mat; // Return the Mat
    }

    private Bitmap convertMatToBitmap(Mat mat) {
        // Create a Bitmap with the same size as the Mat
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);

        // Convert the Mat to Bitmap
        Utils.matToBitmap(mat, bitmap);

        return bitmap; // Return the Bitmap
    }

    /**
     * FIXED: Improved quality check method with proper logic and thresholds
     */
    private boolean checkQualityWithModel(Bitmap bitmap, Face face) {
        if (bitmap == null || fas == null) {
            Log.e("QualityCheck", "Bitmap or FaceAntiSpoofing is null");
            return false;
        }

        try {
            // Step 1: Check image sharpness/clarity
            int laplacianScore = fas.laplacian(bitmap);
            Log.d("QualityCheck", "Laplacian clarity score: " + laplacianScore);

            if (laplacianScore < FaceAntiSpoofing.LAPLACIAN_THRESHOLD) {
                Log.d("QualityCheck", "Image clarity check failed: " + laplacianScore + " < " + FaceAntiSpoofing.LAPLACIAN_THRESHOLD);
                runOnUiThread(() -> instructionToUser.setText("Image is blurry. Please hold steady."));
                return false;
            }

            // Step 2: Check brightness
            float brightness = calculateBrightness(bitmap);
            Log.d("QualityCheck", "Brightness score: " + brightness);

            if (brightness < 40) {
                runOnUiThread(() -> instructionToUser.setText("Too dark. Move to better lighting."));
                return false;
            } else if (brightness > 200) {
                runOnUiThread(() -> instructionToUser.setText("Too bright. Avoid direct light."));
                return false;
            }

            // Step 3: Anti-spoofing detection
            long startTime = System.currentTimeMillis();
            float antiSpoofScore = fas.antiSpoofing(bitmap);
            long endTime = System.currentTimeMillis();

            Log.d("QualityCheck", "Anti-spoofing score: " + antiSpoofScore + " (took " + (endTime - startTime) + "ms)");

            // FIXED: Correct logic - lower score means real face for most models
            boolean isRealFace = antiSpoofScore < FaceAntiSpoofing.THRESHOLD;

            if (!isRealFace) {
                Log.d("QualityCheck", "Anti-spoofing failed: " + antiSpoofScore + " >= " + FaceAntiSpoofing.THRESHOLD);
                runOnUiThread(() -> instructionToUser.setText("Face is not clearly visible."));
                return false;
            }

            // Step 4: Face size validation
            Rect faceRect = face.getBoundingBox();
            float faceArea = faceRect.width() * faceRect.height();
            float imageArea = bitmap.getWidth() * bitmap.getHeight();
            float faceRatio = faceArea / imageArea;

            Log.d("QualityCheck", "Face area ratio: " + faceRatio);

            if (faceRatio < 0.05f) { // Face too small
                runOnUiThread(() -> instructionToUser.setText("Move closer to the camera."));
                return false;
            } else if (faceRatio > 0.8f) { // Face too large
                runOnUiThread(() -> instructionToUser.setText("Move away from the camera."));
                return false;
            }

            // All checks passed
            runOnUiThread(() -> instructionToUser.setText("Processing..."));
            Log.d("QualityCheck", "All quality checks passed successfully");
            return true;

        } catch (Exception e) {
            Log.e("QualityCheck", "Error in quality check: " + e.getMessage(), e);
            runOnUiThread(() -> instructionToUser.setText("Quality check error. Please try again."));
            return false;
        }
    }

    /**
     * FIXED: Improved brightness calculation
     */
    private float calculateBrightness(Bitmap bitmap) {
        if (bitmap == null) return 0;

        long totalBrightness = 0;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int pixel : pixels) {
            int r = (pixel >> 16) & 0xff;
            int g = (pixel >> 8) & 0xff;
            int b = pixel & 0xff;
            // Use luminance formula for accurate brightness
            int brightness = (int)(0.299 * r + 0.587 * g + 0.114 * b);
            totalBrightness += brightness;
        }

        return totalBrightness / (float) pixels.length;
    }

    /**
     * FIXED: More robust face cropping with error handling
     */
    public Bitmap cropFaces(Bitmap bitmap, Face face) {
        if (bitmap == null || face == null || face.getBoundingBox() == null) {
            Log.e("FaceCrop", "Invalid input parameters");
            return bitmap;
        }

        Rect boundingBox = face.getBoundingBox();

        // Add padding around face (10% on each side)
        int padding = Math.min(boundingBox.width(), boundingBox.height()) / 10;
        int left = Math.max(0, boundingBox.left - padding);
        int top = Math.max(0, boundingBox.top - padding);
        int right = Math.min(bitmap.getWidth(), boundingBox.right + padding);
        int bottom = Math.min(bitmap.getHeight(), boundingBox.bottom + padding);

        // Validate crop dimensions
        if (right <= left || bottom <= top) {
            Log.e("FaceCrop", "Invalid crop dimensions");
            return bitmap;
        }

        try {
            // Crop the face
            Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);

            // Ensure minimum size for model input
            if (cropped.getWidth() < 100 || cropped.getHeight() < 100) {
                Log.w("FaceCrop", "Cropped face too small, using original image");
                return bitmap;
            }

            return cropped;

        } catch (OutOfMemoryError e) {
            Log.e("FaceCrop", "Out of memory while cropping", e);
            return bitmap;
        } catch (Exception e) {
            Log.e("FaceCrop", "Error cropping face", e);
            return bitmap;
        }
    }

    /**
     * FIXED: Updated frame processing logic with better error handling
     */
//    private void processQualityCheck(Face face, Bitmap originalBitmap) {
//        try {
//            // Crop face for quality assessment
//            Bitmap faceBitmap = cropFaces(originalBitmap, face);
//
//            if (detectEyesOpen(face) && collectedStableFrameCount <= TOTAL_STABLE_FRAMES_TO_COLLECT) {
//                collectedStableFrameCount++;
//
//                if (checkQualityWithModel(faceBitmap, face)) {
//                    qualityCheckCount++;
//                    Log.d("QualityCheck", "Quality check passed. Count: " + qualityCheckCount + "/" + TOTAL_QUALITY_CHECKS);
//
//                    if (qualityCheckCount >= TOTAL_QUALITY_CHECKS) {
//                        // All quality checks passed, proceed with face comparison
//                        List<Face> faces = new ArrayList<>();
//                        faces.add(face);
//                        compareFaces(faces, originalBitmap);
//                    }
//                } else {
//                    // Reset quality check count on failure
//                    qualityCheckCount = Math.max(0, qualityCheckCount - 1);
//                    Log.d("QualityCheck", "Quality check failed. Count reset to: " + qualityCheckCount);
//                }
//            } else {
//                if (collectedStableFrameCount >= TOTAL_STABLE_FRAMES_TO_COLLECT) {
//                    returnQualityCheckFailed();
//                } else {
//                    runOnUiThread(() -> instructionToUser.setText("Please look straight into the camera"));
//                }
//            }
//
//        } catch (Exception e) {
//            Log.e("QualityCheck", "Error in processQualityCheck", e);
//            runOnUiThread(() -> instructionToUser.setText("Processing error. Please try again."));
//        }
//    }

    private void compareFaces(List<Face> faces, Bitmap originalBitmap) {
        try {
            AchalaSecureCallback achalaSecureCallback = new AchalaSecureCallback() {

                @Override
                public void onCompareSuccess(String result, String score) {
                    hideProgress();
                    Log.d("result after compilation", result);

                    achalaSecureResultModel.setScore(score);
                    achalaSecureResultModel.setBitmapResult(originalBitmap);
                    achalaSecureResultModel.setStatus("SUCCESS");
                    if(isRegistration){
                        achalaSecureResultModel.setMessage("Registered Successfully");
                        Toast.makeText(getApplicationContext(), "Registered Successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        achalaSecureResultModel.setMessage("Authentication Successful");
                        Toast.makeText(getApplicationContext(), "Authentication Successful", Toast.LENGTH_SHORT).show(); // Fix here
                    }



                    finishCameraLauncher(result);
                }

                @Override
                public void onCompareFailed(String e) {
                    achalaSecureResultModel.setScore("0.0");
                    achalaSecureResultModel.setBitmapResult(null);
                    achalaSecureResultModel.setStatus("FAILED");
                    if(isRegistration)
                        achalaSecureResultModel.setMessage("Registered Failed " + e);
                    else
                        achalaSecureResultModel.setMessage("Authentication Failed " + e);

                    finishCameraLauncher(e);
                }
            };
            if (!facesList.isEmpty()) {
                facesList.clear();
            }
            facesList = new ArrayList<>();
            if (progressDialog == null && faces.size() == 1) {
                float[] cameraPreview = runModel(faces, originalBitmap);
                showProgress(this);
                previewView.setVisibility(View.GONE);
                cameraExecutor.shutdown();
                facesList.add(new Pair<>(userGid, cameraPreview));
                Log.d("TAG", "processImageaaaa: " + facesList.size());


                boolean isMobileFaceNet = false;
                if(!isMobileFaceNet){
                    if (isRegistration) {
                        //saveBitmap(originalBitmap);
                        new Comparison(this, originalBitmap, facesList, achalaSecureCallback, model);
                    } else{
                        new Comparison(this, imageFromThePath, facesList, achalaSecureCallback, model);
                    }

                }else{
                    if (isRegistration) {
                        //saveBitmap(originalBitmap);
                        faceCompare(cropFaces(originalBitmap, faces.get(0)),cropFaces(originalBitmap, faces.get(0)),achalaSecureCallback);
                    } else{
                        Bitmap croppedFace1 = cropFaces(originalBitmap, faces.get(0));
                        realTimeOpts = new FaceDetectorOptions.Builder()
                                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                                .build();
                        FaceDetector detector = FaceDetection.getClient(realTimeOpts);
                        detector.process(InputImage.fromBitmap(imageFromThePath, 0)).addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                            @Override
                            public void onSuccess(List<Face> faces) {
                                faceCompare(cropFaces(imageFromThePath, faces.get(0)),croppedFace1,achalaSecureCallback);
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {

                            }
                        });
                    }


                }


            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private void livenessChecks(List<Face> faces) {
        for (Face face : faces) {
            String check = checks.get(currentIndex); // Get the current check
            boolean gestureDetected = false; // Flag to track if gesture was detected
            switch (check) {
                case "smile":
                    detectSmile(face);
                    gestureDetected = detectionResults.get("smile"); // Check if smile was detected
                    break;
                case "blink":
                    detectBlink(face);
                    gestureDetected = detectionResults.get("blink"); // Check if blink was detected
                    break;
                case "open_eyes":
                    detectEyesOpen(face,null);
                    gestureDetected = detectionResults.get("open_eyes"); // Check if open eyes was detected
                    break;
                default:
                    Log.d("Detection", "Unknown check: " + check);
            }
            // Increment currentIndex only if the gesture was detected
            if (gestureDetected) {
                currentIndex++;
            }
        }
        // After checking, log the detection results
        Log.d("Detection Results", detectionResults.toString());
    }


    private void detectSmile(Face face) {
        float smileProb = face.getSmilingProbability();
        if (smileProb != -1.0f) {
            if (smileProb > 0.8) {
                //Toast.makeText(this, "Smile Detected", Toast.LENGTH_SHORT).show();
                // instructionToUser.setText("");
                Log.d("Smile Detection", "Face is smiling with probability: " + smileProb);
                detectionResults.put("smile", true); // Smile detected
            } else {
                Log.d("Smile Detection", "Face is not smiling.");
            }
        } else {
            Log.d("Smile Detection", "Smile probability could not be determined.");
        }
    }

    private void detectRightHead(Face face) {
        float headAngleY = face.getHeadEulerAngleY(); // Get the Y-axis head angle
        if (headAngleY != -1.0f) {
            if (headAngleY > 15.0) { // Threshold for detecting head turned right
                Log.d("Head Detection", "Face is turned to the right with angle: " + headAngleY);
                detectionResults.put("head_right", true); // Head turned right
            } else {
                Log.d("Head Detection", "Face is not turned to the right.");
            }
        } else {
            Log.d("Head Detection", "Head angle could not be determined.");
        }
    }

    private void detectLeftHead(Face face) {
        float headAngleY = face.getHeadEulerAngleY(); // Get the Y-axis head angle
        if (headAngleY != -1.0f) {
            if (headAngleY < -15.0) { // Threshold for detecting head turned left
                Log.d("Head Detection", "Face is turned to the left with angle: " + headAngleY);
                detectionResults.put("head_left", true); // Head turned left
            } else {
                Log.d("Head Detection", "Face is not turned to the left.");
            }
        } else {
            Log.d("Head Detection", "Head angle could not be determined.");
        }
    }

    private void detectBlink(Face face) {
        float leftEyeProb = face.getLeftEyeOpenProbability();
        float rightEyeProb = face.getRightEyeOpenProbability();

        if (leftEyeProb != -1.0f && rightEyeProb != -1.0f) {
            if (leftEyeProb < 0.5 && rightEyeProb < 0.5) {
                //Toast.makeText(this, "Eye blink Detected", Toast.LENGTH_SHORT).show();
                //instructionToUser.setText("");
                Log.d("Blink Detection", "Face is blinking.");
                detectionResults.put("blink", true); // Blink detected
            } else {
                Log.d("Blink Detection", "Face is not blinking.");
            }
        } else {
            Log.d("Blink Detection", "Eye open probability could not be determined.");
        }
    }

    private boolean detectEyesOpen(Face face, Bitmap portraitBmp) {


//      try{
//          if(portraitBmp!=null){
//              RectF boundingBox = new RectF(face.getBoundingBox());
//              if (boundingBox != null) {
//                  boolean hasMask = isMaskDetected(portraitBmp, boundingBox);
//                  Log.d("TAG", "maskDetection: " + hasMask);
//                  if(hasMask){
//                      return false;
//                  }
//
//              }
//          }
//      } catch (Exception e) {
//          throw new RuntimeException(e);
//      }



        float leftEyeProb = face.getLeftEyeOpenProbability();
        float rightEyeProb = face.getRightEyeOpenProbability();

        float averageProb = (leftEyeProb + rightEyeProb) / 2.0f;

        if (leftEyeProb != -1.0f && rightEyeProb != -1.0f) {
            if (averageProb >= 0.5f) {
                // Eyes are open, save the result
                //Toast.makeText(this, "Eyes are open", Toast.LENGTH_SHORT).show();
                Log.d("Eye Detection", "Eyes are open.");
                //instructionToUser.setText("");
                detectionResults.put("open_eyes", true);
                return true; // Save open eyes detection
            } else {
                Log.d("Eye Detection", "Eyes are not open.");
                return false;
            }
        } else {
            Log.d("Eye Detection", "Eye open probability could not be determined.");
        }
        return false;
    }

    public static String getFaceDistanceCategory(Rect bounds, int imageWidth) {
        float faceWidthRatio = (float) bounds.width() / imageWidth;

        if (faceWidthRatio > 0.45) {
            return "close";
        } else if (faceWidthRatio > 0.25) {
            return "medium";
        } else {
            return "far";
        }
    }


    public static boolean isFaceLookingStraight(Face face) {

        boolean result = false;

        float yaw = face.getHeadEulerAngleY();   // Left-right angle
        float pitch = face.getHeadEulerAngleX(); // Up-down angle
        float roll = face.getHeadEulerAngleZ();  // Tilt angle

        result = Math.abs(yaw) < YAW_THRESHOLD &&
                Math.abs(pitch) < PITCH_THRESHOLD &&
                Math.abs(roll) < ROLL_THRESHOLD;

        return result;
    }


    private static final double BRIGHTNESS_THRESHOLD = 100.0; // Example threshold
    private static final double SATURATION_THRESHOLD = 80.0;  // Example threshold

    private void finishCameraLauncher(String result) {
        Intent resultIntent = new Intent();
        Log.d("TAG", "finishCameraLauncher: "+result);
        resultIntent.putExtra("sdkResult", new Gson().toJson(achalaSecureResultModel));
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void showProgress(CameraActivity cameraActivity) {
        try {
            progressDialog = new ProgressDialog(cameraActivity);
            progressDialog.setMessage("Please wait...");
            progressDialog.setCancelable(false); // Prevent dismissal
            progressDialog.show();
        } catch (Exception e) {

        }

    }

    private void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void saveBitmap(Bitmap bitmap) {
        //String filename = "face_detection_" + System.currentTimeMillis() + ".png";
        String filename = userGid +"_"+new Date().getTime() + ".png";

        String directoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/FacesDetected/";
        File directory = new File(directoryPath);
        File file = new File(directory, filename);


        // Check if directory exists, if not, create it
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                Log.d("Directory", "Directory created: " + directoryPath);
            } else {
                Log.e("Directory", "Failed to create directory: " + directoryPath);
            }
        }

        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 10, out);
            Log.d("Image Save", "Image saved: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e("Image Save", "Error saving image", e);
        }
    }

    private void saveFailedBitmap(Bitmap bitmap) {
        //String filename = "face_detection_" + System.currentTimeMillis() + ".png";
        String filename = userGid + ".png";

        String directoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/FacesFailed/";
        File directory = new File(directoryPath);

        // Check if directory exists, if not, create it
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                Log.d("Directory", "Directory created: " + directoryPath);
            } else {
                Log.e("Directory", "Failed to create directory: " + directoryPath);
            }
        }


        File file = new File(directory, filename);

        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            Log.d("Image Save", "Image saved: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e("Image Save", "Error saving image", e);
        }
    }

    private Bitmap drawFaceBoxes(Bitmap bitmap, List<Face> faces) {
        try {
            // Create a mutable copy of the original bitmap
            Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            Paint paint = new Paint();
            paint.setColor(Color.GREEN);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);

            Canvas canvas = new Canvas(mutableBitmap);
            for (Face face : faces) {
                RectF boundingBox = new RectF(face.getBoundingBox());
                canvas.drawRect(boundingBox, paint);
            }

            return mutableBitmap; // Return the modified bitmap

        } catch (Exception e) {
            Log.d("TAG", "drawFaceBoxes error: " + e.getMessage());
            return bitmap; // Return the original bitmap if there's an error
        }
    }

    public Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        // Optionally rotate the bitmap if the image is not upright
        Matrix rotationMatrix = new Matrix();
        rotationMatrix.postRotate(image.getImageInfo().getRotationDegrees());
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), rotationMatrix, true);

        return bitmap;
    }


    @OptIn(markerClass = ExperimentalGetImage.class)
    public void saveImage(ImageProxy imageProxy) {
        // Convert ImageProxy to Bitmap
        Image image = imageProxy.getImage();
        if (image != null) {
            // Get image dimensions
            int width = image.getWidth();
            int height = image.getHeight();

            // Create a Bitmap to hold the image data
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            // Get the pixel data from the image planes
            Image.Plane[] planes = image.getPlanes();
            int[] pixels = new int[width * height];

            // Read the Y, U, and V planes
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            // Read the Y, U, and V bytes
            byte[] yBytes = new byte[yBuffer.remaining()];
            byte[] uBytes = new byte[uBuffer.remaining()];
            byte[] vBytes = new byte[vBuffer.remaining()];

            yBuffer.get(yBytes);
            uBuffer.get(uBytes);
            vBuffer.get(vBytes);

            // Convert YUV to RGB
            for (int j = 0; j < height; j++) {
                for (int i = 0; i < width; i++) {
                    int yIndex = j * width + i;
                    int uIndex = (j >> 1) * (width >> 1) + (i >> 1);
                    int vIndex = (j >> 1) * (width >> 1) + (i >> 1);

                    int y = (yBytes[yIndex] & 0xff);
                    int u = (uBytes[uIndex] & 0xff) - 128;
                    int v = (vBytes[vIndex] & 0xff) - 128;

                    // YUV to RGB conversion formula
                    int r = (int) (y + 1.402 * v);
                    int g = (int) (y - 0.344136 * u - 0.714136 * v);
                    int b = (int) (y + 1.772 * u);

                    r = Math.min(255, Math.max(0, r));
                    g = Math.min(255, Math.max(0, g));
                    b = Math.min(255, Math.max(0, b));

                    pixels[yIndex] = 0xff000000 | (r << 16) | (g << 8) | b; // ARGB
                }
            }

            // Set pixels to Bitmap
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

            // Save the Bitmap to a file
            if (count < 10) {
                count++;
                saveBitmapToFile(bitmap);
            }

            // Recycle the bitmap if needed
            bitmap.recycle();
        }

        // Close the ImageProxy
        //imageProxy.close();
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    public Bitmap saveImage1(ImageProxy imageProxy) {
        // Convert ImageProxy to Bitmap
        Image image = imageProxy.getImage();
        if (image != null) {
            // Get image dimensions
            int width = image.getWidth();
            int height = image.getHeight();

            // Create a Bitmap to hold the image data
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            // Get the pixel data from the image planes
            Image.Plane[] planes = image.getPlanes();
            int[] pixels = new int[width * height];

            // Read the Y, U, and V planes
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            // Read the Y, U, and V bytes
            byte[] yBytes = new byte[yBuffer.remaining()];
            byte[] uBytes = new byte[uBuffer.remaining()];
            byte[] vBytes = new byte[vBuffer.remaining()];

            yBuffer.get(yBytes);
            uBuffer.get(uBytes);
            vBuffer.get(vBytes);

            // Convert YUV to RGB
            for (int j = 0; j < height; j++) {
                for (int i = 0; i < width; i++) {
                    int yIndex = j * width + i;
                    int uIndex = (j >> 1) * (width >> 1) + (i >> 1);
                    int vIndex = (j >> 1) * (width >> 1) + (i >> 1);

                    int y = (yBytes[yIndex] & 0xff);
                    int u = (uBytes[uIndex] & 0xff) - 128;
                    int v = (vBytes[vIndex] & 0xff) - 128;

                    // YUV to RGB conversion formula
                    int r = (int) (y + 1.402 * v);
                    int g = (int) (y - 0.344136 * u - 0.714136 * v);
                    int b = (int) (y + 1.772 * u);

                    r = Math.min(255, Math.max(0, r));
                    g = Math.min(255, Math.max(0, g));
                    b = Math.min(255, Math.max(0, b));

                    pixels[yIndex] = 0xff000000 | (r << 16) | (g << 8) | b; // ARGB
                }
            }

            // Set pixels to Bitmap
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);

            // Save the Bitmap to a file


            // Recycle the bitmap if needed
            bitmap.recycle();
            return bitmap;
        }

        // Close the ImageProxy
        //imageProxy.close();
        return null;
    }

    private void saveBitmapToFile(Bitmap bitmap) {
        // Define the file path
        String directoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/SavedImages/";
        File directory = new File(directoryPath);

        // Create the directory if it doesn't exist
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Log.e("SaveImage", "Failed to create directory: " + directoryPath);
                return;
            }
        }

        // Create a unique file name for the image
        String fileName = "image_" + System.currentTimeMillis() + ".jpg";
        File imageFile = new File(directory, fileName);

        // Save the bitmap to the file
        try (FileOutputStream out = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            Log.d("SaveImage", "Image saved: " + imageFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e("SaveImage", "Error saving image", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        try {
            model.getInterpreter().close();
        } catch (Exception e) {
        }
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    /**
     * Get the angle by which an image must be rotated given the device's current
     * orientation.
     */
    private int getRotationCompensation(String cameraId, Activity activity, boolean isFrontFacing)
            throws CameraAccessException {
        // Get the device's current rotation relative to its "native" orientation.
        // Then, from the ORIENTATIONS table, look up the angle the image must be
        // rotated to compensate for the device's rotation.
        int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int rotationCompensation = ORIENTATIONS.get(deviceRotation);

        // Get the device's sensor orientation.
        CameraManager cameraManager = (CameraManager) activity.getSystemService(CAMERA_SERVICE);
        int sensorOrientation = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION);

        if (isFrontFacing) {
            rotationCompensation = (sensorOrientation + rotationCompensation) % 360;
        } else { // back-facing
            rotationCompensation = (sensorOrientation - rotationCompensation + 360) % 360;
        }
        return rotationCompensation;
    }


    private String getCameraId(boolean isFrontFacing, Context context) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        String[] cameraIds = null;
        String cameraId = null;

        try {
            cameraIds = cameraManager.getCameraIdList();
            for (String id : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if ((isFrontFacing && facing == CameraCharacteristics.LENS_FACING_FRONT) ||
                        (!isFrontFacing && facing == CameraCharacteristics.LENS_FACING_BACK)) {
                    cameraId = id;
                    break; // Found the camera ID we are looking for
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return cameraId; // Returns the camera ID for the requested orientation
    }


    public float[] runModel(List<Face> faces, Bitmap cameraFrameBitmap) {
        Log.d("run Model", "entered " + faces);

        //t1 = System.currentTimeMillis();
        ArrayList<Prediction> predictions = new ArrayList<>();
        if (faces.isEmpty()) {
            Log.d("no faces detected", "no faces detected");
            // textToSpeech.speak("no faces detected", TextToSpeech.QUEUE_FLUSH, null);

        } else if (faces.size() > 1) {
            Log.d("more than one face detected", "more than one face detected");
        } else {
            for (Face face : faces) {
                try {
                    RectF boundingBox = new RectF(face.getBoundingBox());
                    // Validate bounding box
                    if (boundingBox.left < 0 || boundingBox.top < 0 ||
                            boundingBox.right > cameraFrameBitmap.getWidth() ||
                            boundingBox.bottom > cameraFrameBitmap.getHeight()) {
                        Log.d("Modelaaa", "Invalid bounding box: " + boundingBox);
                        continue; // Skip this face
                    }
                    Log.d("entered the faces for loop", "entered" + new RectF(face.getBoundingBox()));
                    Bitmap croppedBitmap = BitmapUtils.cropRectFromBitmap(cameraFrameBitmap, new RectF(face.getBoundingBox()));
                    Log.d("cropping", "is ok");
                    float[] currentFaceEmbeddings = model.getFaceEmbedding(croppedBitmap);

                    return currentFaceEmbeddings;

//                String maskLabel = "";
//                if (isMaskDetectionOn) {
//                    maskLabel = maskDetectionModel.detectMask(croppedBitmap);
//                    Log.d("mask label","Mask Label");
//                }


                } catch (Exception e) {
                    Log.d("Modelaaa", "Exceptionaaaa in FrameAnalyser : " + e.getMessage());
                    //textToSpeech.speak("Unknown", TextToSpeech.QUEUE_FLUSH, null);
                }
            }
        }
        // Log.e("Performance", "Inference time -> " + (System.currentTimeMillis() - t1));
        return null;
    }

    private float L2Norm(float[] x1, float[] x2) {
        float sum = 0;
        for (int i = 0; i < x1.length; i++) {
            sum += Math.pow(x1[i] - x2[i], 2);
        }
        return (float) Math.sqrt(sum);
    }

    private float cosineSimilarity(float[] x1, float[] x2) {
        float dot = 0, mag1 = 0, mag2 = 0;
        for (int i = 0; i < x1.length; i++) {
            dot += x1[i] * x2[i];
            mag1 += x1[i] * x1[i];
            mag2 += x2[i] * x2[i];
        }
        return dot / ((float) Math.sqrt(mag1) * (float) Math.sqrt(mag2));
    }

    public static float[] bitmapToFloatArray(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float[] floatArray = new float[width * height * 3]; // Assuming RGB image with 3 channels

        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = bitmap.getPixel(x, y);
                float r = (float) ((color >> 16) & 0xFF) / 255.0f;
                float g = (float) ((color >> 8) & 0xFF) / 255.0f;
                float b = (float) (color & 0xFF) / 255.0f;
                floatArray[index++] = r;
                floatArray[index++] = g;
                floatArray[index++] = b;
            }
        }
        return floatArray;
    }

    /*public CompletableFuture<List<Face>> processImageAndReturnFaces(InputImage image) {
        CompletableFuture<List<Face>> future = new CompletableFuture<>();
        FaceDetection.getClient(realTimeOpts).process(image)
                .addOnSuccessListener(future::complete)

                .addOnFailureListener(future::completeExceptionally);

        return future;
    }*/

    private double average(ArrayList<Float> list) {
        return list.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
    }


    /**
     * 人脸比对
     */
    private void faceCompare(Bitmap bitmapCrop1, Bitmap bitmapCrop2, AchalaSecureCallback achalaSecureCallback) {
//        if (bitmapCrop1 == null || bitmapCrop2 == null) {
//            Toast.makeText(this, "请先检测人脸", Toast.LENGTH_LONG).show();
//            return;
//        }
//
//        long start = System.currentTimeMillis();
//        float same = mfn.compare(bitmapCrop1, bitmapCrop2); // 就这一句有用代码，其他都是UI
//        long end = System.currentTimeMillis();
//
//        Toast.makeText(this, ""+same, Toast.LENGTH_SHORT).show();
//        String text = "人脸比对结果：" + same;
//        if (same > MobileFaceNet.THRESHOLD) {
//            text = text + "，" + "True";
//            achalaSecureCallback.onCompareSuccess(userGid, String.valueOf(same));
//        } else {
//            text = text + "，" + "False";
//            //saveFailedBitmap(bitmapCrop2);
//            achalaSecureCallback.onCompareFailed("Unknown");
//            //resultTextView.setTextColor(getResources().getColor(android.R.color.holo_red_light));
//        }
//        Log.d("TAG", "faceCompare_mfn: "+same);
//
//        text = text + "，耗时" + (end - start);
//       // resultTextView.setText(text);
//        //resultTextView2.setText("");
    }
//    public Bitmap cropFaces(Bitmap bitmap, Face face) {
//        // Get the bounding box of the face
//        Rect boundingBox = face.getBoundingBox();
//
//        // Ensure the bounding box is within the bounds of the image
//        int left = Math.max(0, boundingBox.left);       // Ensure 'left' is at least 0
//        int top = Math.max(0, boundingBox.top);         // Ensure 'top' is at least 0
//        int right = Math.min(bitmap.getWidth(), boundingBox.right); // Ensure 'right' doesn't exceed the image width
//        int bottom = Math.min(bitmap.getHeight(), boundingBox.bottom); // Ensure 'bottom' doesn't exceed the image height
//
//        // Ensure valid cropping dimensions (width and height must be positive)
//        if (right > left && bottom > top) {
//            // Crop the face from the original image
//            return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
//        } else {
//            // Return the original image if the crop dimensions are invalid (e.g., zero or negative)
//            Log.e("FaceCropError", "Invalid crop dimensions. Returning original image.");
//            return bitmap;
//        }
//    }
//public Bitmap cropFaces(Bitmap bitmap, Face face) {
//    // Get the bounding box of the face
//    Rect boundingBox = face.getBoundingBox();
//
//    int left = Math.max(0, boundingBox.left);
//    int top = Math.max(0, boundingBox.top);
//    int right = Math.min(bitmap.getWidth(), boundingBox.right);
//    int bottom = Math.min(bitmap.getHeight(), boundingBox.bottom);
//
//    if (right > left && bottom > top) {
//        // Crop
//        Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
//
//        // Optional: scale down resolution
//        int reducedWidth = cropped.getWidth() / 2;
//        int reducedHeight = cropped.getHeight() / 2;
//        Bitmap scaled = Bitmap.createScaledBitmap(cropped, reducedWidth, reducedHeight, true);
//        cropped.recycle();
//
//        // Now compress to target size
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        int quality = 100;
//        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos);
//
//        while (baos.toByteArray().length / 1024 > 90 && quality > 10) {
//            baos.reset();
//            quality -= 5; // reduce quality step by step
//            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos);
//        }
//
//        // Convert back to Bitmap
//        byte[] compressedBytes = baos.toByteArray();
//        Bitmap finalBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.length);
//
//        scaled.recycle();
//        return finalBitmap;
//    } else {
//        Log.e("FaceCropError", "Invalid crop dimensions. Returning original image.");
//        return bitmap;
//    }
//}
    public Bitmap reduceImageSize(Bitmap bitmap) {
        if (bitmap == null) return null;

        // Step 1: Scale down resolution if needed (e.g., half size)
        int reducedWidth = bitmap.getWidth() / 2;
        int reducedHeight = bitmap.getHeight() / 2;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, reducedWidth, reducedHeight, true);

        // Step 2: Compress iteratively until size < 90 KB
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int quality = 100;
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos);

        while (baos.toByteArray().length / 1024 > 500 && quality > 10) {
            baos.reset();
            quality -= 5;
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        }

        // Step 3: Convert back to Bitmap
        byte[] compressedBytes = baos.toByteArray();
        Bitmap finalBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.length);

        // Clean up
        scaled.recycle();

        return finalBitmap;
    }




    private void startBlinking(TextView view) {
        view.setVisibility(View.VISIBLE);
        view.setTextColor(Color.parseColor("#174EA6"));
        animator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f, 1f);
        animator.setDuration(1000); // Duration for one cycle of blink (1 second)
        animator.setRepeatCount(ObjectAnimator.INFINITE); // Repeat indefinitely
        animator.setRepeatMode(ObjectAnimator.REVERSE); // Reverse the animation
        animator.start();
    }

    private void stopBlinking(TextView view) {
        view.setVisibility(View.GONE);

        view.setTextColor(Color.parseColor("#174EA6"));
        animator.cancel();
        animator = null;
    }



    // Class-level variables for stability tracking
    private Rect previousBoundingBox = null;
    private int stableFrameCount = 0;
    private static final int STABILITY_THRESHOLD = 1; // Number of continuous stable frames required
    private static final float MOVEMENT_THRESHOLD = 5f; // Movement threshold in pixels

    private boolean checkImageStability(Face face) {
        Rect currentBoundingBox = face.getBoundingBox();

        if (previousBoundingBox != null) {
            float currCenterX = (currentBoundingBox.left + currentBoundingBox.right) / 2.0f;
            float currCenterY = (currentBoundingBox.top + currentBoundingBox.bottom) / 2.0f;
            float prevCenterX = (previousBoundingBox.left + previousBoundingBox.right) / 2.0f;
            float prevCenterY = (previousBoundingBox.top + previousBoundingBox.bottom) / 2.0f;

            float deltaX = Math.abs(currCenterX - prevCenterX);
            float deltaY = Math.abs(currCenterY - prevCenterY);

            float width = currentBoundingBox.width();
            float height = currentBoundingBox.height();

            // ❗ Make tolerance tighter (stricter)
            float toleranceX = width * 0.05f;  // was 0.1f
            float toleranceY = height * 0.05f;

            if (deltaX < toleranceX && deltaY < toleranceY) {
                stableFrameCount++;
                Log.d("Stability Check", "Stable Frame Count: " + stableFrameCount);

                // ❗ Make threshold harder
                if (stableFrameCount >= STABILITY_THRESHOLD + 2) {  // e.g., increase from 5 to 10
                    Log.d("Stability Check", "Face is stable.");
                    return true;
                }
            } else {
                // ❗ Harsher penalty: reset
                stableFrameCount = 0;
                Log.d("Stability Check", "Unstable frame, resetting stable count.");
            }
        }

        previousBoundingBox = currentBoundingBox;
        return false;
    }


    public boolean isGpuDelegateSupported(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
        boolean isSupported = configurationInfo.reqGlEsVersion >= 0x30001; // Check for OpenGL ES 3.1

        if (isSupported) {
            Log.d("TFLite", "GPU Delegate is supported (OpenGL ES 3.1+ detected).");
        } else {
            Log.d("TFLite", "GPU Delegate is not supported (requires OpenGL ES 3.1+).");
        }
        return isSupported;
    }

    // Add this method to handle system bars visibility
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 and above
            getWindow().setDecorFitsSystemWindows(false);  // Allow content to extend into the system bars area
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                // Hide both status bar and navigation bar for full-screen mode
                controller.hide(WindowInsets.Type.navigationBars());
                controller.show(WindowInsets.Type.statusBars());
                getWindow().setStatusBarColor(Color.TRANSPARENT);
            }
        } else {
            // For Android 10 and below
            View decorView = getWindow().getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(flags);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setStatusBarColor(Color.TRANSPARENT);
                getWindow().setNavigationBarColor(Color.TRANSPARENT);
            }
        }
    }


    private boolean isFaceCenterInsideCircle(Rect faceRect, View previewView, View faceCircleOverlay) {
        if (faceRect == null) return false;
        int faceCenterX = faceRect.centerX();
        int faceCenterY = faceRect.centerY();

        // Get position of faceCircleOverlay relative to previewView
        int[] previewPos = new int[2];
        int[] overlayPos = new int[2];

        previewView.getLocationOnScreen(previewPos);
        faceCircleOverlay.getLocationOnScreen(overlayPos);

        int overlayCenterX = overlayPos[0] + faceCircleOverlay.getWidth() / 2;
        int overlayCenterY = overlayPos[1] + faceCircleOverlay.getHeight() / 2;

        int radius = faceCircleOverlay.getWidth() / 2;
        Log.d("FaceCheck", "Face center: (" + faceCenterX + ", " + faceCenterY + ")");
        Log.d("FaceCheck", "Overlay center: (" + overlayCenterX + ", " + overlayCenterY + ")");

        // Now map face center from image coordinates to screen coordinates, or vice versa
        // But if you're using CameraX + PreviewView, a better approach is using TransformationInfo

        double distance = Math.sqrt(Math.pow(faceCenterX - overlayCenterX, 2) + Math.pow(faceCenterY - overlayCenterY, 2));
        Log.d("FaceCheck", "Distance: " + distance + " Radius: " + radius);
        return distance <= radius;
    }
    private void startStableFrameCollection() {
        isCollectingStableFrames = true;
        collectedStableFrameCount = 0;
        qualityCheckCount = 0;
        previousBoundingBox = null;
        stableBitmaps.clear();
        stableFaces.clear();
    }

    private boolean runQualityChecks() {
        boolean spoofDetected = false;

        for (int i = 0; i < stableBitmaps.size(); i++) {
            Bitmap bitmap = stableBitmaps.get(i);
            Face face = stableFaces.get(i);

            boolean passed = checkQualityWithModel(bitmap, face);

            if (!passed) {
                spoofDetected = true;
                break;
            }
        }

        if (spoofDetected) {
            Log.d("SpoofResult", "Spoof detected from one or more frames.");
            // TODO: Handle spoofed case (mark transaction, notify UI, etc.)
        } else {
            Log.d("SpoofResult", "No spoof detected in all 20 frames.");
            // TODO: Proceed with face comparison or next step
        }

        isCollectingStableFrames = false;
        collectedStableFrameCount = 0;
        return spoofDetected;
    }


    public static Bitmap applyWDRIfNeeded(Bitmap src) {
        int width = src.getWidth();
        int height = src.getHeight();

        long totalBrightness = 0;
        int pixelCount = width * height;

        int brightPixelCount = 0;   // count very bright pixels
        int thresholdBright = 220;  // pixels brighter than this are "backlight"

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = src.getPixel(x, y);
                int R = Color.red(pixel);
                int G = Color.green(pixel);
                int B = Color.blue(pixel);

                int brightness = (int)(0.299 * R + 0.587 * G + 0.114 * B);
                totalBrightness += brightness;

                if (brightness > thresholdBright) {
                    brightPixelCount++;
                }
            }
        }

        int avgBrightness = (int)(totalBrightness / pixelCount);
        double brightRatio = (brightPixelCount * 100.0) / pixelCount;

        // Condition to apply WDR:
        // 1. Image is too dark
        // OR
        // 2. Large portion of image is very bright (backlight)
        if (avgBrightness > 100 && brightRatio < 5.0) {
            // Not dark and no strong backlight → return original
            return src;
        }

        // Apply WDR (tone mapping)
        Bitmap result = Bitmap.createBitmap(width, height, src.getConfig());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelColor = src.getPixel(x, y);

                int A = Color.alpha(pixelColor);
                int R = toneMap(Color.red(pixelColor));
                int G = toneMap(Color.green(pixelColor));
                int B = toneMap(Color.blue(pixelColor));

                result.setPixel(x, y, Color.argb(A, R, G, B));
            }
        }

        return result;
    }



    // Simple logarithmic tone mapping function
    private static int toneMap(int value) {
        double normalized = value / 255.0;
        double mapped = Math.log(1 + 9 * normalized) / Math.log(10); // compress highlights
        return (int) (mapped * 255);
    }

    public String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream); // You can use JPEG too
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    private Interpreter createInterpreter(Context context) throws IOException {
        // Load the model file
        MappedByteBuffer tfliteModel = loadModelFile(context);
        // Initialize and return the TensorFlow Lite interpreter
        return new Interpreter(tfliteModel);
    }
    // Load the TFLite model from assets
    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetManager assetManager = context.getAssets();
        AssetFileDescriptor fileDescriptor = assetManager.openFd(TF_OD_API_MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // Method to detect if a mask is present (true/false)
    // Method to detect if a mask is present (true/false)
//    public boolean isMaskDetected(Bitmap inputBitmap, RectF boundingBox) {
//        if (maskDetector == null || inputBitmap == null || boundingBox == null) {
//            return false; // Model or input not valid
//        }
//
//        // Ensure bounding box fits within bitmap dimensions
//        float left = Math.max(0, boundingBox.left);
//        float top = Math.max(0, boundingBox.top);
//        float right = Math.min(inputBitmap.getWidth(), boundingBox.right);
//        float bottom = Math.min(inputBitmap.getHeight(), boundingBox.bottom);
//
//        // Check if bounding box is valid
//        if (right <= left || bottom <= top) {
//            return false; // Invalid bounding box
//        }
//
//        // Scale and crop the bitmap to the model input size
//        float sx = ((float) TF_OD_API_INPUT_SIZE) / (right - left);
//        float sy = ((float) TF_OD_API_INPUT_SIZE) / (bottom - top);
//        Matrix matrix = new Matrix();
//        matrix.postTranslate(-left, -top);
//        matrix.postScale(sx, sy);
//
//        // Create a scaled bitmap for the model
//        Bitmap faceBmp = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Bitmap.Config.ARGB_8888);
//        faceBmp = Bitmap.createBitmap(inputBitmap, 0, 0, inputBitmap.getWidth(), inputBitmap.getHeight(), matrix, true);
//
//        // Prepare input buffer
//        ByteBuffer inputBuffer = convertBitmapToByteBuffer(faceBmp);
//
//        // Prepare output buffer (assuming model outputs two classes: mask, no_mask)
//        float[][] output = new float[1][2]; // Adjust size based on your model's output
//
//        // Run inference
//        maskDetector.run(inputBuffer, output);
//
//        // Check if mask is detected (index 0 = mask, confidence >= 0.6)
//        float maskConfidence = output[0][0];
//        return maskConfidence >= MINIMUM_CONFIDENCE;
//    }

    // Convert bitmap to ByteBuffer for model input
    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        // Resize first
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, true);

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * TF_OD_API_INPUT_SIZE * TF_OD_API_INPUT_SIZE * 3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[TF_OD_API_INPUT_SIZE * TF_OD_API_INPUT_SIZE];
        resized.getPixels(pixels, 0, TF_OD_API_INPUT_SIZE, 0, 0, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE);

        for (int pixel : pixels) {
            // Normalize pixel values to [0, 1]
            byteBuffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f); // Red
            byteBuffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);  // Green
            byteBuffer.putFloat((pixel & 0xFF) / 255.0f);        // Blue
        }
        return byteBuffer;
    }



}
