package com.mynew.secure.activity;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
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
import com.mynew.secure.mobilefacenet.MobileFaceNet;
import com.mynew.secure.utils.AchalaActions;
import com.mynew.secure.utils.AchalaSdkConfigurations;
import com.mynew.secure.utils.AchalaSecureCallback;
import com.mynew.secure.utils.AchalaSecureResultModel;
import com.mynew.secure.utils.BitmapUtils;
import com.mynew.secure.utils.Comparison;
import com.mynew.secure.utils.FaceNetModel;
import com.mynew.secure.utils.FileChecker;
import com.mynew.secure.utils.ModelInfo;
import com.mynew.secure.utils.Models;
import com.mynew.secure.utils.Prediction;
import com.mynew.secure.utils.FaceOverlayView;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private static final int CLOSE_FACE_THRESHOLD = 200;
    private static final float YAW_THRESHOLD = 20.0f;
    private static final float PITCH_THRESHOLD = 15.0f;
    private static final float ROLL_THRESHOLD = 10.0f;

    private PreviewView previewView;
    private FaceOverlayView faceOverlayView;
    private ExecutorService cameraExecutor;
    private int count = 0;
    private FaceNetModel model;
    private float[] subject;
    private ArrayList<Pair<String, float[]>> facesList = new ArrayList<>();
    private ProgressDialog progressDialog;

    ArrayList<String> checks = new ArrayList<>(Arrays.asList(AchalaActions.Open_Eyes));
    HashMap<String, Boolean> detectionResults = new HashMap<>();
    private int currentIndex = 0;
    private TextView instructionToUser, liveDetection;
    private String userGid = "Verify_User";
    private Bitmap imageFromThePath;
    private boolean isRegistration;
    private Bitmap originalBitmap;
    private FaceAntiSpoofing fas;
    private MobileFaceNet mfn;
    private ImageButton closeCamera;
    private ObjectAnimator animator;
    private FaceDetectorOptions realTimeOpts;
    private AchalaSecureResultModel achalaSecureResultModel = new AchalaSecureResultModel();
    private AchalaSdkConfigurations achalaSdkConfigurations = new AchalaSdkConfigurations();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_lib);

        hideSystemBars();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        if (getIntent().getExtras() != null && getIntent().getExtras().size() > 0) {
            userGid = getIntent().getStringExtra("userGid");
            isRegistration = getIntent().getBooleanExtra("isRegistration", false);
            achalaSdkConfigurations = new Gson().fromJson(getIntent().getStringExtra("configurations"), AchalaSdkConfigurations.class);
            if(achalaSdkConfigurations != null && achalaSdkConfigurations.getActions().size() > 0) {
                checks.clear();
                checks.addAll(achalaSdkConfigurations.getActions());
            }
        }

        initializeViews();
        setupCameraAndModel();
    }

    private void initializeViews() {
        previewView = findViewById(R.id.preview_view);
        faceOverlayView = findViewById(R.id.face_overlay_view);
        instructionToUser = findViewById(R.id.to_user);
        liveDetection = findViewById(R.id.live_detection);
        closeCamera = findViewById(R.id.close_camera);

        previewView.setScaleX(-1);
        cameraExecutor = Executors.newSingleThreadExecutor();

        closeCamera.setOnClickListener(v -> finish());
    }

    private void setupCameraAndModel() {
        if(!isRegistration) {
            if (achalaSdkConfigurations != null && !achalaSdkConfigurations.getVerifyImageURL().isEmpty()) {
                imageFromThePath = new FileChecker().getBitmapFromURL(achalaSdkConfigurations.getVerifyImageURL());
            } else if (achalaSdkConfigurations != null && achalaSdkConfigurations.getVerifyImageBitmap() != null) {
                originalBitmap = achalaSdkConfigurations.getVerifyImageBitmap();
                Bitmap copiedBitmap = originalBitmap.copy(originalBitmap.getConfig(), true);
                imageFromThePath = copiedBitmap;
            }
        }

        initAntiSpoofing();

        try {
            ModelInfo modelInfo = Models.FACENET_512;
            model = new FaceNetModel(CameraActivity.this, modelInfo, false, true);
            Log.d("FaceNetModel", "Model initialized successfully");
        } catch (Exception e) {
            Log.e("FaceNetModel", "Model initialization failed", e);
        }

        // Initialize detection results
        for (String check : checks) {
            detectionResults.put(check, false);
        }
        currentIndex = 0;

        runOnUiThread(this::startCamera);
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
            mfn = new MobileFaceNet(getAssets());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Camera initialization error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .setTargetResolution(new Size(1280, 720))
                .build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        // Inside bindPreview method
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720)) // Keep as is if other methods fail
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::processImage);
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    public static Bitmap imageProxyToBitmap(ImageProxy image) {
        Image img = image.getImage();
        if (img == null) return null;

        byte[] nv21 = yuv420ToNv21(img);
        return rotateBitmap(YuvImageToBitmap(nv21, img.getWidth(), img.getHeight()),
                image.getImageInfo().getRotationDegrees());
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

            InputImage inputImage = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .enableTracking()
                    .build();

            originalBitmap = imageProxyToBitmap(imageProxy);

            FaceDetector detector = FaceDetection.getClient(options);
            Task<List<Face>> result = detector.process(inputImage)
                    .addOnSuccessListener(faces -> {
                        processFaceDetectionResults(faces,imageProxy);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("Face Detection", "Face detection failed", e);
                        runOnUiThread(() -> {
                            faceOverlayView.updateFacePosition(null, FaceOverlayView.FaceStatus.NO_FACE);
                        });
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        } catch (Exception e) {
            imageProxy.close();
            Log.e("CameraX", "Error processing image", e);
        }
    }

    private void processFaceDetectionResults(List<Face> faces,ImageProxy imageProxy) {
        runOnUiThread(() -> {
            if (faces.isEmpty()) {
                faceOverlayView.updateFacePosition(null, FaceOverlayView.FaceStatus.NO_FACE);
                instructionToUser.setText("Please look into the camera");
            } else if (faces.size() > 1) {
                faceOverlayView.updateFacePosition(null, FaceOverlayView.FaceStatus.MULTIPLE_FACES);
                instructionToUser.setText("Multiple faces detected. Please ensure only one face is visible");
            } else {
                Face face = faces.get(0);
                Rect faceRect = face.getBoundingBox();

                // Convert face rect to screen coordinates for overlay
                RectF screenFaceRect = convertToScreenCoordinates(faceRect, imageProxy); // Pass imageProxy

                String distance = getFaceDistanceCategory(faceRect);
                switch (distance) {
                    case "medium":
                        instructionToUser.setText("Please don't move");
                        if (isFaceLookingStraight(face) && faceOverlayView.isFaceInGuide(screenFaceRect)) {
                            faceOverlayView.updateFacePosition(screenFaceRect, FaceOverlayView.FaceStatus.GOOD_POSITION);

                            if (currentIndex < checks.size()) {
                                handleLivenessChecks(faces);
                            } else {
                                if (checkQualityWithModel(cropFaces(originalBitmap, face))) {
                                    if (detectEyesOpen(face)) {
                                        if (checkImageStability(face) || !isRegistration) {
                                            compareFaces(faces, originalBitmap);
                                        }
                                    } else {
                                        instructionToUser.setText("Please keep your eyes open");
                                    }
                                }
                            }
                        } else {
                            faceOverlayView.updateFacePosition(screenFaceRect, FaceOverlayView.FaceStatus.NOT_STRAIGHT);
                            instructionToUser.setText("Please look straight into the camera");
                        }
                        break;
                    case "close":
                        faceOverlayView.updateFacePosition(screenFaceRect, FaceOverlayView.FaceStatus.TOO_CLOSE);
                        instructionToUser.setText("Please move away from the camera");
                        break;
                    case "far":
                        faceOverlayView.updateFacePosition(screenFaceRect, FaceOverlayView.FaceStatus.TOO_FAR);
                        instructionToUser.setText("Please move closer to the camera");
                        break;
                }
            }
        });
    }

    // Inside CameraActivity.java
    // Inside CameraActivity.java
    // Inside CameraActivity.java
    // Inside CameraActivity.java
    // Inside CameraActivity.java
// Update the method signature from:
// private RectF convertToScreenCoordinates(Rect faceRect) {
// To:
    @OptIn(markerClass = ExperimentalGetImage.class)
    private RectF convertToScreenCoordinates(Rect faceRect, ImageProxy imageProxy) { // Add ImageProxy parameter

        // Get preview view dimensions (target coordinate system for overlay)
        int previewWidth = previewView.getWidth();
        int previewHeight = previewView.getHeight();

        // Determine the source image dimensions for accurate scaling
        int sourceWidth, sourceHeight;
        if (imageProxy != null && imageProxy.getImage() != null) {
            // Use actual image dimensions from ImageProxy
            sourceWidth = imageProxy.getImage().getWidth();
            sourceHeight = imageProxy.getImage().getHeight();
            Log.d("CameraActivity_DEBUG", "Using ImageProxy dimensions: W=" + sourceWidth + ", H=" + sourceHeight);
        } else {
            // Fallback: Use the target resolution set in ImageAnalysis.Builder
            // Note: This might not be perfectly accurate if CameraX chose a different resolution internally
            sourceWidth = 1280; // Match setTargetResolution
            sourceHeight = 720; // Match setTargetResolution
            Log.d("CameraActivity_DEBUG", "Using fallback dimensions: W=" + sourceWidth + ", H=" + sourceHeight);
        }

        Log.d("CameraActivity_DEBUG", "PreviewView Dimensions: W=" + previewWidth + ", H=" + previewHeight);
        Log.d("CameraActivity_DEBUG", "Source Image Dimensions: W=" + sourceWidth + ", H=" + sourceHeight);
        Log.d("CameraActivity_DEBUG", "Input faceRect (ML Kit coords): " + faceRect.toString());

        // Check if dimensions are valid
        if (previewWidth <= 0 || previewHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            Log.w("CameraActivity_DEBUG", "Invalid dimensions. Returning empty RectF.");
            return new RectF();
        }

        // Calculate scale factors based on actual source and target dimensions
        float scaleX = (float) previewWidth / sourceWidth;
        float scaleY = (float) previewHeight / sourceHeight;

        Log.d("CameraActivity_DEBUG", "Scale Factors: X=" + scaleX + ", Y=" + scaleY);

        // Scale the coordinates to preview view dimensions
        float scaledLeft = faceRect.left * scaleX;
        float scaledTop = faceRect.top * scaleY;
        float scaledRight = faceRect.right * scaleX;
        float scaledBottom = faceRect.bottom * scaleY;

        // Apply mirroring for front-facing camera (previewView.setScaleX(-1))
        // In mirrored space: left becomes (previewWidth - original_right), right becomes (previewWidth - original_left)
        float leftMirrored = previewWidth - scaledRight;
        float topMirrored = scaledTop;
        float rightMirrored = previewWidth - scaledLeft;
        float bottomMirrored = scaledBottom;

        // Ensure correct rectangle order (left < right, top < bottom) - technically redundant for valid input
        // but kept for robustness.
        float leftFinal = Math.min(leftMirrored, rightMirrored);
        float rightFinal = Math.max(leftMirrored, rightMirrored);
        float topFinal = Math.min(topMirrored, bottomMirrored);
        float bottomFinal = Math.max(topMirrored, bottomMirrored);

        RectF screenRectF = new RectF(leftFinal, topFinal, rightFinal, bottomFinal);

        // Additional check to ensure coordinates are reasonable (helpful for debugging)
        if (screenRectF.left < 0 || screenRectF.right > previewWidth ||
                screenRectF.top < 0 || screenRectF.bottom > previewHeight) {
            Log.w("CameraActivity_DEBUG", "Converted rect extends beyond preview bounds: " + screenRectF.toString());
            // Depending on requirements, you might clamp values or return an empty rect.
            // For now, let's log it and proceed.
        }

        Log.d("CameraActivity_DEBUG", "Converted screenFaceRect (Mirrored Overlay coords): " + screenRectF.toString());

        return screenRectF;
    }
    private void handleLivenessChecks(List<Face> faces) {
        String check = checks.get(currentIndex);
        boolean gestureDetected = false;

        for (Face face : faces) {
            switch (check) {
                case "smile":
                    detectSmile(face);
                    gestureDetected = detectionResults.get("smile");
                    if (!gestureDetected && !instructionToUser.getText().toString().equalsIgnoreCase("Please smile into the camera")) {
                        instructionToUser.setText("Please smile into the camera");
                    }
                    break;
                case "blink":
                    detectBlink(face);
                    gestureDetected = detectionResults.get("blink");
                    if (!gestureDetected && !instructionToUser.getText().toString().equalsIgnoreCase("Please blink your eyes")) {
                        instructionToUser.setText("Please blink your eyes");
                    }
                    break;
                case "open_eyes":
                    detectEyesOpen(face);
                    gestureDetected = detectionResults.get("open_eyes");
                    if (!gestureDetected) {
                        instructionToUser.setText("Please keep your eyes open");
                    }
                    break;
            }
        }

        if (gestureDetected) {
            currentIndex++;
            if (currentIndex < checks.size()) {
                // Move to next check
                instructionToUser.setText("");
            } else {
                instructionToUser.setText("Perfect! Processing...");
            }
        }
    }

    // ... (Rest of the existing methods remain the same)
    // detectSmile, detectBlink, detectEyesOpen, getFaceDistanceCategory, isFaceLookingStraight, etc.

    private void detectSmile(Face face) {
        float smileProb = face.getSmilingProbability();
        if (smileProb != -1.0f && smileProb > 0.8) {
            Log.d("Smile Detection", "Face is smiling with probability: " + smileProb);
            detectionResults.put("smile", true);
        }
    }

    private void detectBlink(Face face) {
        float leftEyeProb = face.getLeftEyeOpenProbability();
        float rightEyeProb = face.getRightEyeOpenProbability();

        if (leftEyeProb != -1.0f && rightEyeProb != -1.0f &&
                leftEyeProb < 0.5 && rightEyeProb < 0.5) {
            Log.d("Blink Detection", "Face is blinking.");
            detectionResults.put("blink", true);
        }
    }

    private boolean detectEyesOpen(Face face) {
        float leftEyeProb = face.getLeftEyeOpenProbability();
        float rightEyeProb = face.getRightEyeOpenProbability();

        if (leftEyeProb != -1.0f && rightEyeProb != -1.0f &&
                leftEyeProb >= 0.95 && rightEyeProb >= 0.95) {
            detectionResults.put("open_eyes", true);
            return true;
        }
        return false;
    }

    public static String getFaceDistanceCategory(Rect bounds) {
        int width = bounds.width();
        if (width < 400) {
            return "far";
        } else if (width <= 900) {
            return "medium";
        } else {
            return "close";
        }
    }


    public static boolean isFaceLookingStraight(Face face) {
        float yaw = face.getHeadEulerAngleY();
        float pitch = face.getHeadEulerAngleX();
        float roll = face.getHeadEulerAngleZ();

        return Math.abs(yaw) < YAW_THRESHOLD &&
                Math.abs(pitch) < PITCH_THRESHOLD &&
                Math.abs(roll) < ROLL_THRESHOLD;
    }

    // Include all other existing methods like checkQualityWithModel, compareFaces, etc.
    // ... (keeping them as they were in the original code)

    private boolean checkQualityWithModel(Bitmap bitmaps) {
        if (bitmaps == null) return false;

        int laplace1 = fas.laplacian(bitmaps);
        if (laplace1 < FaceAntiSpoofing.LAPLACIAN_THRESHOLD) {
            instructionToUser.setText("Image quality too low");
            return false;
        } else {
            float score1 = fas.antiSpoofing(bitmaps);
            return score1 < FaceAntiSpoofing.THRESHOLD;
        }
    }

    public Bitmap cropFaces(Bitmap bitmap, Face face) {
        Rect boundingBox = face.getBoundingBox();
        int left = Math.max(0, boundingBox.left);
        int top = Math.max(0, boundingBox.top);
        int right = Math.min(bitmap.getWidth(), boundingBox.right);
        int bottom = Math.min(bitmap.getHeight(), boundingBox.bottom);

        if (right > left && bottom > top) {
            return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
        } else {
            Log.e("FaceCropError", "Invalid crop dimensions. Returning original image.");
            return bitmap;
        }
    }

    // Class-level variables for stability tracking
    private Rect previousBoundingBox = null;
    private int stableFrameCount = 0;
    private static final int STABILITY_THRESHOLD = 1;
    private static final float MOVEMENT_THRESHOLD = 30f;

    private boolean checkImageStability(Face face) {
        Rect currentBoundingBox = face.getBoundingBox();
        if (previousBoundingBox != null) {
            float deltaX = Math.abs(currentBoundingBox.left - previousBoundingBox.left);
            float deltaY = Math.abs(currentBoundingBox.top - previousBoundingBox.top);

            if (deltaX < MOVEMENT_THRESHOLD && deltaY < MOVEMENT_THRESHOLD) {
                stableFrameCount++;
                if (stableFrameCount == STABILITY_THRESHOLD) {
                    return true;
                }
            } else {
                stableFrameCount = 0;
            }
        }
        previousBoundingBox = currentBoundingBox;
        return false;
    }

    private void compareFaces(List<Face> faces, Bitmap originalBitmap) {
        try {
            AchalaSecureCallback achalaSecureCallback = new AchalaSecureCallback() {
                @Override
                public void onCompareSuccess(String result, String score) {
                    hideProgress();
                    achalaSecureResultModel.setScore(score);
                    achalaSecureResultModel.setBitmapResult(originalBitmap);
                    achalaSecureResultModel.setStatus("SUCCESS");
                    achalaSecureResultModel.setMessage(isRegistration ?
                            "Registered Successfully" : "Authentication Successful");
                    finishCameraLauncher(result);
                }

                @Override
                public void onCompareFailed(String e) {
                    hideProgress();
                    achalaSecureResultModel.setScore("0.0");
                    achalaSecureResultModel.setBitmapResult(null);
                    achalaSecureResultModel.setStatus("FAILED");
                    achalaSecureResultModel.setMessage(isRegistration ?
                            "Registration Failed " + e : "Authentication Failed " + e);
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
                faceOverlayView.setVisibility(View.GONE);
                cameraExecutor.shutdown();
                facesList.add(new Pair<>(userGid, cameraPreview));

                if (isRegistration) {
                    new Comparison(this, originalBitmap, facesList, achalaSecureCallback, model);
                } else {
                    new Comparison(this, imageFromThePath, facesList, achalaSecureCallback, model);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public float[] runModel(List<Face> faces, Bitmap cameraFrameBitmap) {
        if (faces.isEmpty()) return null;

        for (Face face : faces) {
            try {
                RectF boundingBox = new RectF(face.getBoundingBox());
                if (boundingBox.left < 0 || boundingBox.top < 0 ||
                        boundingBox.right > cameraFrameBitmap.getWidth() ||
                        boundingBox.bottom > cameraFrameBitmap.getHeight()) {
                    continue;
                }

                Bitmap croppedBitmap = BitmapUtils.cropRectFromBitmap(
                        cameraFrameBitmap, new RectF(face.getBoundingBox())
                );
                return model.getFaceEmbedding(croppedBitmap);
            } catch (Exception e) {
                Log.d("Model", "Exception in runModel: " + e.getMessage());
            }
        }
        return null;
    }

    private void finishCameraLauncher(String result) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("sdkResult", new Gson().toJson(achalaSecureResultModel));
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void showProgress(CameraActivity cameraActivity) {
        try {
            progressDialog = new ProgressDialog(cameraActivity);
            progressDialog.setMessage("Please wait...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        } catch (Exception e) {
            Log.e("Progress", "Error showing progress dialog", e);
        }
    }

    private void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.navigationBars());
                controller.show(WindowInsets.Type.statusBars());
                getWindow().setStatusBarColor(Color.TRANSPARENT);
            }
        } else {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        try {
            if (model != null) {
                model.getInterpreter().close();
            }
        } catch (Exception e) {
            Log.e("Cleanup", "Error closing model interpreter", e);
        }
    }
}