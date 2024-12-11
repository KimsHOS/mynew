package com.example.achalasecure.utils;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FileReaderLocal {

    private final FaceNetModel faceNetModel;
    private final FaceDetectorOptions realTimeOpts;
    private final com.google.mlkit.vision.face.FaceDetector detector;
    private final ArrayList<Pair<String, Bitmap>> data = new ArrayList<>();
    private final ArrayList<Pair<String, float[]>> imageData = new ArrayList<>();
    private ProcessCallback callback;
    private int numImagesWithNoFaces = 0;
    private int imageCounter = 0;
    private int numImages = 0;

    public FileReaderLocal(FaceNetModel faceNetModel) {
        this.faceNetModel = faceNetModel;
        realTimeOpts = new FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build();
        detector = FaceDetection.getClient(realTimeOpts);
    }

    public void run(ArrayList<Pair<String, Bitmap>> data, ProcessCallback callback) {
        this.data.clear();
        this.data.addAll(data);
        this.callback = callback;
        this.numImages = data.size();
        Log.d("no of images","no:"+numImages);
        Task<List<Face>> future= scanImage(data.get(imageCounter).first, data.get(imageCounter).second);
        Log.d("result face","result:"+future);
    }

    public interface ProcessCallback {
        void onProcessCompleted(ArrayList<Pair<String, float[]>> data, int numImagesWithNoFaces) throws IOException;
    }

    private Task<List<Face>> scanImage(String name, Bitmap image) {
        CompletableFuture<float[]> future;
        Log.d("scanImage","entered");
        InputImage inputImage = InputImage.fromBitmap(image, 0);
//        InputImage inputImage = InputImage.fromByteArray(
//                BitmapUtils.bitmapToNV21ByteArray(image),
//                image.getWidth(),
//                image.getHeight(),
//                0,
//                InputImage.IMAGE_FORMAT_NV21
//        );
        Log.d("Imput image in facelist","converted");
        Task<List<Face>> result =
        detector.process(inputImage)
            .addOnSuccessListener(faces -> {
            if (!faces.isEmpty()) {
                Log.d("faces in Facelist","not empty");
                float[]    embedding=getEmbedding(image,new RectF(faces.get(0).getBoundingBox()));
                imageData.add(new Pair<>(name, embedding));
                if (imageCounter + 1 != numImages) {
                    imageCounter++;
                    scanImage(data.get(imageCounter).first, data.get(imageCounter).second);
                } else {
                    try {
                        callback.onProcessCompleted(imageData, numImagesWithNoFaces);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    reset();
                }
            } else {
                numImagesWithNoFaces++;
                Log.d("No face Detected","No:"+numImagesWithNoFaces);
                if (imageCounter + 1 != numImages) {
                    imageCounter++;
                    scanImage(data.get(imageCounter).first, data.get(imageCounter).second);
                } else {
                    try {
                        callback.onProcessCompleted(imageData, numImagesWithNoFaces);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    reset();
                }
            }
        })
        .addOnFailureListener(
            new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    // Task failed with an exception
                    // ...
                    Log.d("Face Detection@facelist", "Detection failed: " + e.getMessage());
                }
            });
        return result;
    }

    private  float[] getEmbedding(Bitmap image, RectF bbox) {

        Log.d("get embedding","get embedding");
        return faceNetModel.getFaceEmbedding(
            BitmapUtils.cropRectFromBitmap(image, bbox));

    }

    private void reset() {
        imageCounter = 0;
        numImages = 0;
        numImagesWithNoFaces = 0;
        data.clear();
        imageData.clear();
    }
}
