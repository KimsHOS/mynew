package com.mynew.secure.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;


public class Comparison {

    private Context context = null;
    private FaceNetModel model;
//    private MaskDetectionModel maskDetectionModel;
    private FaceDetectorOptions realTimeOpts;
    private ArrayList<Pair<String, float[]>> faceList;
    private HashMap<String, ArrayList<Float>> nameScoreHashmap;
    private float[] subject;
    private long t1;
    private boolean isProcessing = false;
//    private final boolean isMaskDetectionOn = true;
    private final String metricToBeUsed = "cosine";
    private final String SERIALIZED_DATA_FILENAME = "image_data";
    private boolean SHARED_PREF_IS_DATA_STORED_KEY = false;
    private  FileReaderLocal fileReader;
    private TextToSpeech textToSpeech;
    AchalaSecureCallback achalaSecureCallback;
    public Comparison(Context context, Bitmap bitmap, ArrayList<Pair<String, float[]>> faceList1, AchalaSecureCallback achalaSecureCallback, FaceNetModel model) throws IOException {
       try {

        this.context = context;
        this.achalaSecureCallback = achalaSecureCallback;
        this.model = model;
//        maskDetectionModel = new MaskDetectionModel(context);
        faceList = new ArrayList<>();
        nameScoreHashmap = new HashMap<>();
        subject = new float[this.model.getEmbeddingDimension()];
        realTimeOpts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build();
        fileReader = new FileReaderLocal(this.model);
        textToSpeech = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int langResult = textToSpeech.setLanguage(Locale.US);
                    if (langResult == TextToSpeech.LANG_MISSING_DATA
                            || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TextToSpeech", "Language is not supported or missing data.");
                    }
                } else {
                    Log.e("TextToSpeech", "Initialization failed.");
                }
            }
        });
        faceList=faceList1;
        Log.d("facelist@camparison","No"+faceList);
           InputImage inputImage = null;
        try {
            inputImage  = InputImage.fromBitmap(bitmap, 0);
        }catch (Exception e){

        }

        Log.d("Input Image", "see " + inputImage);
        FaceDetector detector = FaceDetection.getClient(realTimeOpts);
        Task<List<Face>> result =
                detector.process(inputImage)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<Face>>() {
                                    @Override
                                    public void onSuccess(List<Face> faces) {
                                        Log.d("faces", "see " + faces);
                                        runModel(faces, bitmap);
                                    }
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        textToSpeech.speak("Faces should be clear", TextToSpeech.QUEUE_FLUSH, null);

                                        // Task failed with an exception
                                        // ...
                                        Log.e("Face Detection", "Detection failed: " + e.getMessage());
                                    }
                                });

       }catch (Exception e){
           Log.d("TAG", "ComparisonBitmapError: " + e.getMessage());
       }
    }


    public void runModel(List<Face> faces, Bitmap cameraFrameBitmap) {
        Log.d("run Model", "entered " + faces);

        t1 = System.currentTimeMillis();
        ArrayList<Prediction> predictions = new ArrayList<>();
        if(faces.isEmpty())
        {
            Log.d("no faces detected","no faces detected");
            //textToSpeech.speak("no faces detected", TextToSpeech.QUEUE_FLUSH, null);
            achalaSecureCallback.onCompareFailed("no faces detected");

        }
        for (Face face : faces) {
            try {
                Log.d("entered the faces for loop", "entered"+new RectF(face.getBoundingBox()));

                Bitmap croppedBitmap = BitmapUtils.cropRectFromBitmap(cameraFrameBitmap, new RectF(face.getBoundingBox()));
                Log.d("cropping","is ok");
                subject = model.getFaceEmbedding(croppedBitmap);

//                String maskLabel = "";
//                if (isMaskDetectionOn) {
//                    maskLabel = maskDetectionModel.detectMask(croppedBitmap);
//                    Log.d("mask label","Mask Label");
//                }

                //if( maskLabel == maskDetectionModel.NO_MASK  )
                if (true) {  // Placeholder for mask detection logic
                    Log.d("comparison", "started " + faceList);
                    for (int i = 0; i < faceList.size(); i++) {
                        Log.d("face", "name " + faceList.get(i));
                        if (!nameScoreHashmap.containsKey(faceList.get(i).first)) {
                            ArrayList<Float> p = new ArrayList<>();
                            if (metricToBeUsed.equals("cosine")) {
                                p.add(cosineSimilarity(subject, faceList.get(i).second));
                            } else {
                                p.add(L2Norm(subject, faceList.get(i).second));
                            }
                            nameScoreHashmap.put(faceList.get(i).first, p);
                        } else {
                            if (metricToBeUsed.equals("cosine")) {
                                nameScoreHashmap.get(faceList.get(i).first).add(cosineSimilarity(subject, faceList.get(i).second));
                            } else {
                                nameScoreHashmap.get(faceList.get(i).first).add(L2Norm(subject, faceList.get(i).second));
                            }
                        }
                    }

                    List<Double> avgScores = new ArrayList<>();
                    for (ArrayList<Float> scores : nameScoreHashmap.values()) {
                        avgScores.add(average(scores));
                        //Toast.makeText(((CameraActivity)context), "score : "+avgScores, Toast.LENGTH_LONG).show();
                        Log.d("avgScores","no:"+avgScores);
                    }

                    String[] names = nameScoreHashmap.keySet().toArray(new String[0]);
                    nameScoreHashmap.clear();

                    String bestScoreUserName;
                    if (metricToBeUsed.equals("cosine")) {
                        bestScoreUserName = (avgScores.stream().max(Double::compare).orElse(0.0) > model.getModel().getCosineThreshold())
                                ? names[avgScores.indexOf(avgScores.stream().max(Double::compare).orElse(0.0))]
                                : "Unknown";
                        Log.d("cosine","method");
                        if(avgScores.get(0)<0.8){
                            bestScoreUserName = "Unknown";
                        }
                    } else {
                        bestScoreUserName = (avgScores.stream().min(Double::compare).orElse(0.0) < model.getModel().getL2Threshold())
                                ? names[avgScores.indexOf(avgScores.stream().min(Double::compare).orElse(0.0))]
                                : "Unknown";
                        Log.d("L2 norm","method"+avgScores.stream().min(Double::compare));
                    }

                    predictions.add(new Prediction(face.getBoundingBox(), bestScoreUserName));
//                    Log.d("PersonIdentification", "val " + bestScoreUserName);
//                    textToSpeech.speak(bestScoreUserName, TextToSpeech.QUEUE_FLUSH, null);
//                    boolean speakingEnd = textToSpeech.isSpeaking();
//                    do {
//                        speakingEnd = textToSpeech.isSpeaking();
//                    } while (speakingEnd);
                    if(bestScoreUserName.equalsIgnoreCase("unknown")){
                        achalaSecureCallback.onCompareFailed("Unknown");
                    }else{
                        achalaSecureCallback.onCompareSuccess(bestScoreUserName, String.valueOf(avgScores));
                    }

                }
            } catch (Exception e) {
                Log.d("Model", "Exception in FrameAnalyser : " + e.getMessage());
                //textToSpeech.speak("Unknown", TextToSpeech.QUEUE_FLUSH, null);
                achalaSecureCallback.onCompareFailed("Unknown");
            }
        }
        Log.e("Performance", "Inference time -> " + (System.currentTimeMillis() - t1));
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
    public void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
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

    public CompletableFuture<List<Face>> processImageAndReturnFaces(InputImage image) {
        CompletableFuture<List<Face>> future = new CompletableFuture<>();
        FaceDetection.getClient(realTimeOpts).process(image)
                .addOnSuccessListener(future::complete)

                .addOnFailureListener(future::completeExceptionally);

        return future;
    }

    private double average(ArrayList<Float> list) {
        return list.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
    }
}
