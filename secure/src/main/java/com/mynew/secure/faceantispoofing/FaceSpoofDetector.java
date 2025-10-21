package com.mynew.secure.faceantispoofing;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.Log;



import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * Utility class for interacting with FaceSpoofDetector
 *
 * - It uses the MiniFASNet model from https://github.com/minivision-ai/Silent-Face-Anti-Spoofing
 * - The preprocessing methods are derived from
 *   https://github.com/serengil/deepface/blob/master/deepface/models/spoofing/FasNet.py
 * - The model weights are in the PyTorch format. To convert them to the TFLite format,
 *   check the notebook linked in the README of the project
 * - An instance of this class is injected in ImageVectorUseCase
 */
public class FaceSpoofDetector {

    public static class FaceSpoofResult {
        private final boolean isSpoof;
        private final float score;
        private final long timeMillis;

        public FaceSpoofResult(boolean isSpoof, float score, long timeMillis) {
            this.isSpoof = isSpoof;
            this.score = score;
            this.timeMillis = timeMillis;
        }

        public boolean isSpoof() {
            return isSpoof;
        }

        public float getScore() {
            return score;
        }

        public long getTimeMillis() {
            return timeMillis;
        }
    }

    private static final float SCALE_1 = 2.7f;
    private static final float SCALE_2 = 4.0f;
    private static final int INPUT_IMAGE_DIM = 80;
    private static final int OUTPUT_DIM = 3;

    private Interpreter firstModelInterpreter;
    private Interpreter secondModelInterpreter;
    private final ImageProcessor imageTensorProcessor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FaceSpoofDetector(Context context, boolean useGpu, boolean useXNNPack, boolean useNNAPI, AssetManager assets) {
        // Initialize ImageProcessor
        imageTensorProcessor = new ImageProcessor.Builder()
                .add(new CastOp(DataType.FLOAT32))
                .build();

        Interpreter.Options interpreterOptions = new Interpreter.Options();
        interpreterOptions.setNumThreads(4); // Default to CPU with 4 threads
        interpreterOptions.setUseXNNPACK(useXNNPack);

        // Try GPU delegate if enabled and supported
        if (useGpu) {
            CompatibilityList compatibilityList = new CompatibilityList();
            if (compatibilityList.isDelegateSupportedOnThisDevice()) {
                interpreterOptions.addDelegate(new GpuDelegate(compatibilityList.getBestOptionsForThisDevice()));
                Log.d("FaceNetModel", "Using GPU delegate");
            } else {
                Log.w("FaceNetModel", "GPU delegate not supported, falling back to CPU");
            }
        }

        // Disable NNAPI to avoid Android 11 issues
        interpreterOptions.setUseNNAPI(false);
        // Initialize Interpreters
        try {
            firstModelInterpreter = new Interpreter(
                    FileUtil.loadMappedFile(context, "spoofModelScale1.tflite"),
                    interpreterOptions
            );
            secondModelInterpreter = new Interpreter(
                    FileUtil.loadMappedFile(context, "spoofModelScale2.tflite"),
                    interpreterOptions
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize interpreters", e);
        }
    }

//    public FaceSpoofResult detectSpoof(Bitmap frameImage, Rect faceRect) {
//
//            long startTime = System.currentTimeMillis();
//
//            // Crop images and perform RGB -> BGR conversion
//            Bitmap croppedImage1 = crop(frameImage, faceRect, SCALE_1, INPUT_IMAGE_DIM, INPUT_IMAGE_DIM);
//            for (int i = 0; i < croppedImage1.getWidth(); i++) {
//                for (int j = 0; j < croppedImage1.getHeight(); j++) {
//                    int pixel = croppedImage1.getPixel(i, j);
//                    croppedImage1.setPixel(i, j, Color.rgb(
//                            Color.blue(pixel),
//                            Color.green(pixel),
//                            Color.red(pixel)
//                    ));
//                }
//            }
//
//            Bitmap croppedImage2 = crop(frameImage, faceRect, SCALE_2, INPUT_IMAGE_DIM, INPUT_IMAGE_DIM);
//            for (int i = 0; i < croppedImage2.getWidth(); i++) {
//                for (int j = 0; j < croppedImage2.getHeight(); j++) {
//                    int pixel = croppedImage2.getPixel(i, j);
//                    croppedImage2.setPixel(i, j, Color.rgb(
//                            Color.blue(pixel),
//                            Color.green(pixel),
//                            Color.red(pixel)
//                    ));
//                }
//            }
//
//            // Process images for model input
//            ByteBuffer input1 = imageTensorProcessor.process(TensorImage.fromBitmap(croppedImage1)).getBuffer();
//            ByteBuffer input2 = imageTensorProcessor.process(TensorImage.fromBitmap(croppedImage2)).getBuffer();
//            float[][] output1 = new float[1][OUTPUT_DIM];
//            float[][] output2 = new float[1][OUTPUT_DIM];
//
//            // Run inference
//            firstModelInterpreter.run(input1, output1);
//            secondModelInterpreter.run(input2, output2);
//
//            long timeMillis = System.currentTimeMillis() - startTime;
//
//            // Process outputs
//            float[] softmaxOutput1 = softMax(output1[0]);
//            float[] softmaxOutput2 = softMax(output2[0]);
//            float[] output = new float[OUTPUT_DIM];
//            for (int i = 0; i < OUTPUT_DIM; i++) {
//                output[i] = softmaxOutput1[i] + softmaxOutput2[i];
//            }
//
//            // Find max index and compute score
//            int maxIndex = 0;
//            float maxValue = output[0];
//            for (int i = 1; i < output.length; i++) {
//                if (output[i] > maxValue) {
//                    maxValue = output[i];
//                    maxIndex = i;
//                }
//            }
//
//            boolean isSpoof = maxIndex != 1;
//            float score = maxValue / 2f;
//
//            return new FaceSpoofResult(isSpoof, score, timeMillis);
//    }

    public FaceSpoofResult detectSpoof(Bitmap frameImage, Rect faceRect) {
        long startTime = System.currentTimeMillis();

        // Crop with scaling
        Bitmap croppedImage1 = crop(frameImage, faceRect, SCALE_1, INPUT_IMAGE_DIM, INPUT_IMAGE_DIM);
        Bitmap croppedImage2 = crop(frameImage, faceRect, SCALE_2, INPUT_IMAGE_DIM, INPUT_IMAGE_DIM);

        // RGB -> BGR conversion
        for (int i = 0; i < croppedImage1.getWidth(); i++) {
            for (int j = 0; j < croppedImage1.getHeight(); j++) {
                int pixel = croppedImage1.getPixel(i, j);
                croppedImage1.setPixel(i, j, Color.rgb(
                        Color.blue(pixel),
                        Color.green(pixel),
                        Color.red(pixel)
                ));
            }
        }
        for (int i = 0; i < croppedImage2.getWidth(); i++) {
            for (int j = 0; j < croppedImage2.getHeight(); j++) {
                int pixel = croppedImage2.getPixel(i, j);
                croppedImage2.setPixel(i, j, Color.rgb(
                        Color.blue(pixel),
                        Color.green(pixel),
                        Color.red(pixel)
                ));
            }
        }

        // Convert to model inputs
        ByteBuffer input1 = imageTensorProcessor.process(TensorImage.fromBitmap(croppedImage1)).getBuffer();
        ByteBuffer input2 = imageTensorProcessor.process(TensorImage.fromBitmap(croppedImage2)).getBuffer();

        float[][] output1 = new float[1][OUTPUT_DIM];
        float[][] output2 = new float[1][OUTPUT_DIM];

        // Run inference
        firstModelInterpreter.run(input1, output1);
        secondModelInterpreter.run(input2, output2);

        long timeMillis = System.currentTimeMillis() - startTime;

        // Apply softmax
        float[] softmaxOutput1 = softMax(output1[0]);
        float[] softmaxOutput2 = softMax(output2[0]);

        // Combine outputs (sum, not average)
        float[] output = new float[OUTPUT_DIM];
        for (int i = 0; i < OUTPUT_DIM; i++) {
            output[i] = (softmaxOutput1[i] + softmaxOutput2[i]);
        }

        // Get label
        int label = 0;
        float maxValue = output[0];
        for (int i = 1; i < output.length; i++) {
            if (output[i] > maxValue) {
                maxValue = output[i];
                label = i;
            }
        }

        // Match Kotlin logic
        boolean isSpoof = label != 1;
        float score = output[label] / 2f;

        return new FaceSpoofResult(isSpoof, score, timeMillis);
    }


    private boolean isFaceAtProperDistance(Rect faceBox, int imageWidth) {
        float faceWidthRatio = (float) faceBox.width() / imageWidth;
        Log.d("DistanceCheck", "Face Width Ratio: " + faceWidthRatio);
        Log.d("DistanceCheck", "Face Rect: left=" + faceBox.left + ", top=" + faceBox.top +
                ", width=" + faceBox.width() + ", imageWidth=" + imageWidth);
        return faceWidthRatio >= 0.3f && faceWidthRatio <= 0.6f; // Approximate range for ~20cm
    }

    private float[] softMax(float[] x) {
        float[] exp = new float[x.length];
        float expSum = 0;
        for (int i = 0; i < x.length; i++) {
            exp[i] = (float) Math.exp(x[i]);
            expSum += exp[i];
        }
        for (int i = 0; i < exp.length; i++) {
            exp[i] /= expSum;
        }
        return exp;
    }

    private Bitmap crop(Bitmap origImage, Rect bbox, float bboxScale, int targetWidth, int targetHeight) {
        int srcWidth = origImage.getWidth();
        int srcHeight = origImage.getHeight();
        Rect scaledBox = getScaledBox(srcWidth, srcHeight, bbox, bboxScale);
        Bitmap croppedBitmap = Bitmap.createBitmap(
                origImage,
                scaledBox.left,
                scaledBox.top,
                scaledBox.width(),
                scaledBox.height()
        );
        return Bitmap.createScaledBitmap(croppedBitmap, targetWidth, targetHeight, true);
    }

    private Rect getScaledBox(int srcWidth, int srcHeight, Rect box, float bboxScale) {
        int x = box.left;
        int y = box.top;
        int w = box.width();
        int h = box.height();
        float scale = Math.min(Math.min((srcHeight - 1f) / h, (srcWidth - 1f) / w), bboxScale);
        float newWidth = w * scale;
        float newHeight = h * scale;
        float centerX = w / 2f + x;
        float centerY = h / 2f + y;
        float topLeftX = centerX - newWidth / 2;
        float topLeftY = centerY - newHeight / 2;
        float bottomRightX = centerX + newWidth / 2;
        float bottomRightY = centerY + newHeight / 2;

        if (topLeftX < 0) {
            bottomRightX -= topLeftX;
            topLeftX = 0;
        }
        if (topLeftY < 0) {
            bottomRightY -= topLeftY;
            topLeftY = 0;
        }
        if (bottomRightX > srcWidth - 1) {
            topLeftX -= (bottomRightX - (srcWidth - 1));
            bottomRightX = srcWidth - 1;
        }
        if (bottomRightY > srcHeight - 1) {
            topLeftY -= (bottomRightY - (srcHeight - 1));
            bottomRightY = srcHeight - 1;
        }

        return new Rect(
                Math.round(topLeftX),
                Math.round(topLeftY),
                Math.round(bottomRightX),
                Math.round(bottomRightY)
        );
    }
}