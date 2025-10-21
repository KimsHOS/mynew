package com.mynew.secure.faceantispoofing;
import com.mynew.secure.utils.MyUtil;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FaceAntiSpoofing {
    private static final String MODEL_FILE = "FaceAntiSpoofing.tflite";
    public static final int INPUT_IMAGE_SIZE = 256;

    // FIXED: More reasonable thresholds
    public static final float THRESHOLD = 0.55f; // Increased from 0.2f - was too sensitive
    public static final int ROUTE_INDEX = 6;
    public static final int LAPLACE_THRESHOLD = 30; // Reduced from 50 - was too strict
    public static final int LAPLACIAN_THRESHOLD = 150; // Reduced from 1000 - was too strict

    private Interpreter interpreter;

    public FaceAntiSpoofing(AssetManager assetManager) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        interpreter = new Interpreter(MyUtil.loadModelFile(assetManager, MODEL_FILE), options);
    }

    /**
     * Anti-spoofing detection
     * @param bitmap input face bitmap
     * @return score (lower score = more likely real face)
     */
    public float antiSpoofing(Bitmap bitmap) {
        // Resize face to 256x256 as required by model
        Bitmap bitmapScale = Bitmap.createScaledBitmap(bitmap, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true);
        float[][][] img = normalizeImage(bitmapScale);
        float[][][][] input = new float[1][][][];
        input[0] = img;

        float[][] clss_pred = new float[1][8];
        float[][] leaf_node_mask = new float[1][8];

        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(interpreter.getOutputIndex("Identity"), clss_pred);
        outputs.put(interpreter.getOutputIndex("Identity_1"), leaf_node_mask);

        interpreter.runForMultipleInputsOutputs(new Object[]{input}, outputs);

        Log.i("FaceAntiSpoofing", "Class predictions: [" +
                clss_pred[0][0] + ", " + clss_pred[0][1] + ", " + clss_pred[0][2] + ", " +
                clss_pred[0][3] + ", " + clss_pred[0][4] + ", " + clss_pred[0][5] + ", " +
                clss_pred[0][6] + ", " + clss_pred[0][7] + "]");

        Log.i("FaceAntiSpoofing", "Leaf node mask: [" +
                leaf_node_mask[0][0] + ", " + leaf_node_mask[0][1] + ", " + leaf_node_mask[0][2] + ", " +
                leaf_node_mask[0][3] + ", " + leaf_node_mask[0][4] + ", " + leaf_node_mask[0][5] + ", " +
                leaf_node_mask[0][6] + ", " + leaf_node_mask[0][7] + "]");

        // FIXED: Use more robust scoring method
        float score = leaf_score_improved(clss_pred, leaf_node_mask);
        Log.i("FaceAntiSpoofing", "Final anti-spoofing score: " + score);

        return score;
    }

    // FIXED: Improved scoring method that's less sensitive
    private float leaf_score_improved(float[][] clss_pred, float[][] leaf_node_mask) {
        float score = 0;
        float totalWeight = 0;

        for (int i = 0; i < 8; i++) {
            float weight = leaf_node_mask[0][i];
            if (weight > 0.1f) { // Only consider significant weights
                score += clss_pred[0][i] * weight;
                totalWeight += weight;
            }
        }

        // Normalize by total weight to avoid bias
        if (totalWeight > 0) {
            score = score / totalWeight;
        }

        // Return absolute value and apply sigmoid for better distribution
        return (float) (1.0 / (1.0 + Math.exp(-Math.abs(score))));
    }

    // Original method kept for comparison
    private float leaf_score1(float[][] clss_pred, float[][] leaf_node_mask) {
        float score = 0;
        for (int i = 0; i < 8; i++) {
            score += Math.abs(clss_pred[0][i]) * leaf_node_mask[0][i];
        }
        return score;
    }

    private float leaf_score2(float[][] clss_pred) {
        return clss_pred[0][ROUTE_INDEX];
    }

    /**
     * Normalize image to [0, 1] range
     * @param bitmap input bitmap
     * @return normalized float array
     */
    public static float[][][] normalizeImage(Bitmap bitmap) {
        int h = bitmap.getHeight();
        int w = bitmap.getWidth();
        float[][][] floatValues = new float[h][w][3];
        float imageStd = 255.0f;

        int[] pixels = new int[h * w];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, w, h);

        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                final int val = pixels[i * w + j];
                float r = ((val >> 16) & 0xFF) / imageStd;
                float g = ((val >> 8) & 0xFF) / imageStd;
                float b = (val & 0xFF) / imageStd;
                floatValues[i][j] = new float[]{r, g, b};
            }
        }
        return floatValues;
    }

    /**
     * FIXED: Improved Laplacian sharpness calculation
     * @param bitmap input bitmap
     * @return sharpness score
     */
    public int laplacian(Bitmap bitmap) {
        Bitmap bitmapScale = Bitmap.createScaledBitmap(bitmap, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true);

        // FIXED: Use standard Laplacian kernel
        int[][] laplace = {
                {0, -1, 0},
                {-1, 4, -1},
                {0, -1, 0}
        };

        int size = laplace.length;
        int[][] img = MyUtil.convertGreyImg(bitmapScale);
        int height = img.length;
        int width = img[0].length;
        int score = 0;
        int totalPixels = 0;

        for (int x = 0; x < height - size + 1; x++) {
            for (int y = 0; y < width - size + 1; y++) {
                int result = 0;

                // Apply convolution
                for (int i = 0; i < size; i++) {
                    for (int j = 0; j < size; j++) {
                        result += (img[x + i][y + j] & 0xFF) * laplace[i][j];
                    }
                }

                // FIXED: Square the result for better edge detection
                score += (result * result);
                totalPixels++;
            }
        }

        // FIXED: Return variance-based score instead of simple threshold count
        return totalPixels > 0 ? score / totalPixels : 0;
    }

    /**
     * FIXED: More robust quality assessment
     * @param bitmap input bitmap
     * @return true if image quality is acceptable
     */
    public boolean isImageQualityGood(Bitmap bitmap) {
        int sharpnessScore = laplacian(bitmap);
        float brightness = calculateAverageBrightness(bitmap);

        Log.d("FaceAntiSpoofing", "Sharpness score: " + sharpnessScore + ", Brightness: " + brightness);

        // FIXED: More lenient quality checks
        boolean isSharp = sharpnessScore > LAPLACIAN_THRESHOLD;
        boolean isBrightnessOk = brightness > 30 && brightness < 220; // Avoid too dark/bright

        return isSharp && isBrightnessOk;
    }

    /**
     * Calculate average brightness of the image
     */
    private float calculateAverageBrightness(Bitmap bitmap) {
        long totalBrightness = 0;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int pixel : pixels) {
            int r = (pixel >> 16) & 0xff;
            int g = (pixel >> 8) & 0xff;
            int b = pixel & 0xff;
            // Use standard luminance formula
            int brightness = (int)(0.299 * r + 0.587 * g + 0.114 * b);
            totalBrightness += brightness;
        }

        return totalBrightness / (float) pixels.length;
    }

    /**
     * FIXED: Complete spoofing detection with proper logic
     * @param bitmap input face bitmap
     * @return true if face is real, false if spoofed
     */
    public boolean isRealFace(Bitmap bitmap) {
        // First check image quality
        if (!isImageQualityGood(bitmap)) {
            Log.d("FaceAntiSpoofing", "Image quality check failed");
            return false;
        }

        // Then check for spoofing
        float spoofScore = antiSpoofing(bitmap);
        boolean isReal = spoofScore < THRESHOLD; // Lower score means more likely real

        Log.d("FaceAntiSpoofing", "Spoof score: " + spoofScore + ", isReal: " + isReal);
        return isReal;
    }
}