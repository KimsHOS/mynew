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

    private static final int CLOSE_FACE_THRESHOLD = 200; // Adjust as needed
    private static final float YAW_THRESHOLD = 20.0f;
    private static final float PITCH_THRESHOLD = 15.0f;
    private static final float ROLL_THRESHOLD = 10.0f;

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private int count = 0;
    private FaceNetModel model;
    private float[] subject;
    private ArrayList<Pair<String, float[]>> facesList = new ArrayList<>();
    private ProgressDialog progressDialog;

    ArrayList<String> checks = new ArrayList<>(Arrays.asList(AchalaActions.Open_Eyes));
//    "smile", "blink",
    HashMap<String, Boolean> detectionResults = new HashMap<>();
    private int currentIndex = 0;
    private TextView instructionToUser,instructionToUser1, liveDetection, spoofingDetected;
    private String userGid = "Verify_User";
    private Bitmap imageFromThePath;
    private boolean isRegistration;
    private Bitmap originalBitmap;
    private FaceAntiSpoofing fas;
    private MobileFaceNet mfn;
    private ImageView faceMaskImage;
    private ImageButton closeCamera;
    private ObjectAnimator animator;
    private FaceDetectorOptions realTimeOpts;
    private AchalaSecureResultModel achalaSecureResultModel = new AchalaSecureResultModel();
    private AchalaSdkConfigurations achalaSdkConfigurations = new AchalaSdkConfigurations();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_lib);
        getWindow().setStatusBarColor(Color.parseColor("#FFFFFF"));

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
        instructionToUser1 = findViewById(R.id.to_user1);
        liveDetection = findViewById(R.id.live_detection);
        spoofingDetected = findViewById(R.id.spoofing_detected);
        faceMaskImage = findViewById(R.id.face_mask_image);
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

        try {
            ModelInfo modelInfo = Models.FACENET;
            model = new FaceNetModel(CameraActivity.this, modelInfo, false, true);
            Log.d("FaceNetModel", "Model initialized successfully");
        } catch (Exception e) {
            Log.e("FaceNetModel", "Model initialization failed", e);
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

        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280 , 720))
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
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
            //objectDetection(originalBitmap);
            FaceDetector detector = FaceDetection.getClient(options);
            Task<List<Face>> result = detector.process(inputImage)
                    .addOnSuccessListener(faces -> {
                        String distance;
                        if (faces.isEmpty()) {
                            instructionToUser.setText("Please look in to the camera");
                        } else if (faces.size() > 1) {
                            instructionToUser.setText("more than one face found");
                        } else {
                            switch (getFaceDistanceCategory(faces.get(0).getBoundingBox())){
                                case "medium":
                                    if (isFaceLookingStraight(faces.get(0)) || true) {
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
                                            if (checkQualityWithModel(cropFaces(originalBitmap, faces.get(0)))){
                                                if (detectEyesOpen(faces.get(0))){
                                                    //if(isFaceInProperLighting(convertBitmapToMat(originalBitmap),instructionToUser1))
                                                    if(checkImageStability(faces.get(0)) || !isRegistration)
                                                        compareFaces(faces, originalBitmap);
                                                } else
                                                    instructionToUser.setText("Please look in to the camera");
                                            } else {

                                                // need to implement the to terminate the camera if the quality is bad after 5 seconds
                                                //while waiting for 5 seconds to detect the face again if quality is good application will process the image

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

    private boolean checkQualityWithModel(Bitmap bitmaps) {

        //        if (bitmapCrop1 == null || bitmapCrop2 == null) {
//            Toast.makeText(this, "请先检测人脸", Toast.LENGTH_LONG).show();
//            return;
//        }

        // 活体检测前先判断图片清晰度
        int laplace1 = fas.laplacian(bitmaps);

        String text = "Quality detection：" + laplace1;
        if (laplace1 < FaceAntiSpoofing.LAPLACIAN_THRESHOLD) {
            text = text + "，" + "False";
            instructionToUser.setText("Quality detection failed");
            return false;
        } else {
            instructionToUser.setText("");
            long start = System.currentTimeMillis();

            // 活体检测
            float score1 = fas.antiSpoofing(bitmaps);

            long end = System.currentTimeMillis();

            text = " liveness detection：" + score1;
            Log.d("TAG", "checkQualityWithModel: "+text);
            if (score1 < FaceAntiSpoofing.THRESHOLD) {
                if (animator != null)
                    stopBlinking(spoofingDetected);
                text = text + "，" + "True";
                // resultTextView.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                return true;
            } else {
                text = text + "，" + "False";
                startBlinking(spoofingDetected);
                //resultTextView.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                return false;
            }
            // text = text + "。耗时" + (end - start);
        }
        //Log.d("TAG", "checkQualityWithModel: "+text);


    }

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
                    detectEyesOpen(face);
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

    private boolean detectEyesOpen(Face face) {
        float leftEyeProb = face.getLeftEyeOpenProbability();
        float rightEyeProb = face.getRightEyeOpenProbability();

        if (leftEyeProb != -1.0f && rightEyeProb != -1.0f) {
            if (leftEyeProb >= 0.95 && rightEyeProb >= 0.95) {
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

    public static String getFaceDistanceCategory(Rect bounds) {

        if (bounds.width() > 400 && bounds.width() < 1400) {
            Log.d("TAG", "getFaceDistanceCategory: " + "medium - " + bounds.width());
            return "medium";
        } else if(bounds.width() < 850){
            Log.d("TAG", "getFaceDistanceCategory: " + "far - " + bounds.width());
            return "far";
        }else {
            Log.d("TAG", "getFaceDistanceCategory: " + "close - " + bounds.width());
            return "close";
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

    public static boolean isFaceInProperLighting(Mat faceImage, TextView instructionToUser1) {
        // Check if the image is empty
        if (faceImage.empty()) {
            Log.d("TAG", "Input face image is empty.");
            return false; // or handle as needed
        }

        // Convert the image from BGR to HSV color space
        Mat hsvImage = new Mat();
        Imgproc.cvtColor(faceImage, hsvImage, Imgproc.COLOR_BGR2HSV);

        // Split the HSV image into its channels
        List<Mat> hsvChannels = new ArrayList<>();
        Core.split(hsvImage, hsvChannels);

        Mat valueChannel = hsvChannels.get(2); // V channel
        Mat saturationChannel = hsvChannels.get(1); // S channel

        // Calculate the histogram for the Value channel
        Mat valueHist = new Mat();
        int histSize = 256; // Size of histogram
        float[] range = {0, 256};
        Imgproc.calcHist(Arrays.asList(valueChannel), new MatOfInt(0), new Mat(), valueHist, new MatOfInt(histSize), new MatOfFloat(range));

        // Calculate the histogram for the Saturation channel
        Mat saturationHist = new Mat();
        Imgproc.calcHist(Arrays.asList(saturationChannel), new MatOfInt(0), new Mat(), saturationHist, new MatOfInt(histSize), new MatOfFloat(range));

        // Analyze histogram data to check lighting conditions
        double valueSum = Core.sumElems(valueHist).val[0];
        double saturationSum = Core.sumElems(saturationHist).val[0];

        // To avoid division by zero, check if the image has any non-zero pixels
        double totalPixels = faceImage.total();
        double nonZeroPixels = Core.countNonZero(valueChannel); // Count non-zero pixels

        // Avoid division by zero
        double avgBrightness = nonZeroPixels > 0 ? valueSum / nonZeroPixels : 0;
        double avgSaturation = saturationSum / totalPixels;

        // Debugging logs to check histogram values
        Log.d("TAG", "Value Histogram: " + Arrays.toString(valueHist.get(0, 0)));
        Log.d("TAG", "Saturation Histogram: " + Arrays.toString(saturationHist.get(0, 0)));
        Log.d("TAG", "Average Brightness: " + avgBrightness);
        Log.d("TAG", "Average Saturation: " + avgSaturation);

        // Update instructionToUser1 with histogram and average values
        instructionToUser1.setText("Value Histogram: " + Arrays.toString(valueHist.get(0, 0)) +
                "\nSaturation Histogram: " + Arrays.toString(saturationHist.get(0, 0)) +
                "\nAverage Brightness: " + avgBrightness +
                "\nAverage Saturation: " + avgSaturation);
        instructionToUser1.setVisibility(View.VISIBLE);

        // Determine if the lighting is proper
        return avgBrightness > BRIGHTNESS_THRESHOLD && avgSaturation > SATURATION_THRESHOLD;
    }



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
        if (bitmapCrop1 == null || bitmapCrop2 == null) {
            Toast.makeText(this, "请先检测人脸", Toast.LENGTH_LONG).show();
            return;
        }

        long start = System.currentTimeMillis();
        float same = mfn.compare(bitmapCrop1, bitmapCrop2); // 就这一句有用代码，其他都是UI
        long end = System.currentTimeMillis();

        Toast.makeText(this, ""+same, Toast.LENGTH_SHORT).show();
        String text = "人脸比对结果：" + same;
        if (same > MobileFaceNet.THRESHOLD) {
            text = text + "，" + "True";
            achalaSecureCallback.onCompareSuccess(userGid, String.valueOf(same));
        } else {
            text = text + "，" + "False";
            //saveFailedBitmap(bitmapCrop2);
            achalaSecureCallback.onCompareFailed("Unknown");
            //resultTextView.setTextColor(getResources().getColor(android.R.color.holo_red_light));
        }
        Log.d("TAG", "faceCompare_mfn: "+same);

        text = text + "，耗时" + (end - start);
       // resultTextView.setText(text);
        //resultTextView2.setText("");
    }
    public Bitmap cropFaces(Bitmap bitmap, Face face) {
        // Get the bounding box of the face
        Rect boundingBox = face.getBoundingBox();

        // Ensure the bounding box is within the bounds of the image
        int left = Math.max(0, boundingBox.left);       // Ensure 'left' is at least 0
        int top = Math.max(0, boundingBox.top);         // Ensure 'top' is at least 0
        int right = Math.min(bitmap.getWidth(), boundingBox.right); // Ensure 'right' doesn't exceed the image width
        int bottom = Math.min(bitmap.getHeight(), boundingBox.bottom); // Ensure 'bottom' doesn't exceed the image height

        // Ensure valid cropping dimensions (width and height must be positive)
        if (right > left && bottom > top) {
            // Crop the face from the original image
            return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
        } else {
            // Return the original image if the crop dimensions are invalid (e.g., zero or negative)
            Log.e("FaceCropError", "Invalid crop dimensions. Returning original image.");
            return bitmap;
        }
    }


    private void startBlinking(TextView view) {
        view.setVisibility(View.VISIBLE);
        faceMaskImage.setVisibility(View.VISIBLE);

        view.setTextColor(Color.parseColor("#174EA6"));
        animator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f, 1f);
        animator.setDuration(1000); // Duration for one cycle of blink (1 second)
        animator.setRepeatCount(ObjectAnimator.INFINITE); // Repeat indefinitely
        animator.setRepeatMode(ObjectAnimator.REVERSE); // Reverse the animation
        animator.start();
    }

    private void stopBlinking(TextView view) {
        view.setVisibility(View.GONE);
        faceMaskImage.setVisibility(View.GONE);
        view.setTextColor(Color.parseColor("#174EA6"));
        animator.cancel();
        animator = null;
    }



    // Class-level variables for stability tracking
    private Rect previousBoundingBox = null;
    private int stableFrameCount = 0;
    private static final int STABILITY_THRESHOLD = 1; // Number of continuous stable frames required
    private static final float MOVEMENT_THRESHOLD = 15f; // Movement threshold in pixels

    private boolean checkImageStability(Face face) {
        Rect currentBoundingBox = face.getBoundingBox();
        if (previousBoundingBox != null) {
            float deltaX = Math.abs(currentBoundingBox.left - previousBoundingBox.left);
            float deltaY = Math.abs(currentBoundingBox.top - previousBoundingBox.top);

            // Check if the movement is below the threshold
            if (deltaX < MOVEMENT_THRESHOLD && deltaY < MOVEMENT_THRESHOLD) {
                stableFrameCount++;
                if (stableFrameCount == STABILITY_THRESHOLD) {
                    Log.d("Stability Check", "Image is stable for 3 continuous frames.");
                    return true; // Stability maintained for 3 frames, return true
                }
            } else {
                stableFrameCount = 0; // Reset if significant movement is detected
                Log.d("Stability Check", "Image is not stable.");
            }
        }

        // Update the previous bounding box
        previousBoundingBox = currentBoundingBox;
        return false; // Stability not yet maintained for 3 frames
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
}
