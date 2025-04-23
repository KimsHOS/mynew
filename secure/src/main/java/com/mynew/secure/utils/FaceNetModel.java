package com.mynew.secure.utils;

import static java.lang.Math.max;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class FaceNetModel {

    private final int imgSize;
    public final int embeddingDim;
    private ModelInfo actModel;
    private Interpreter interpreter;
    private final ImageProcessor imageTensorProcessor;

    public FaceNetModel(Context context, ModelInfo model, boolean useGpu, boolean useXNNPack) {
        this.imgSize = model.getInputDims();
        actModel = model;
        this.embeddingDim = model.getOutputDims();

        this.imageTensorProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(imgSize, imgSize, ResizeOp.ResizeMethod.BILINEAR))
                .add(new StandardizeOp())
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

        try {
            this.interpreter = new Interpreter(
                    FileUtil.loadMappedFile(context, model.getAssetsFilename()),
                    interpreterOptions
            );
            Log.d("FaceNetModel", "Model loaded successfully: " + model.getName());
        } catch (Exception e) {
            Log.e("FaceNetModel", "Failed to load interpreter: " + e.getMessage(), e);
            throw new RuntimeException("Interpreter initialization failed", e);
        }
    }

    public float[] getFaceEmbedding(Bitmap image) {
        TensorBuffer tensorBuffer = TensorBuffer.createFixedSize(
                new int[]{1, imgSize, imgSize, 3}, DataType.FLOAT32);
        ByteBuffer byteBuffer = convertBitmapToBuffer(image);
        byteBuffer.order(ByteOrder.nativeOrder());
        tensorBuffer.loadBuffer(byteBuffer);

        // Log input tensor stats for debugging
        logTensorStats("Input Tensor", tensorBuffer.getFloatArray());

        TensorBuffer result = runFaceNet(tensorBuffer);
        float[] embeddings = result.getFloatArray();

        // Log output embeddings for debugging
        logTensorStats("Output Embeddings", embeddings);

        return embeddings;
    }

    private TensorBuffer runFaceNet(TensorBuffer inputs) {
        long t1 = System.currentTimeMillis();
        TensorBuffer output = TensorBufferFloat.createFixedSize(
                new int[]{1, embeddingDim}, DataType.FLOAT32);
        interpreter.run(inputs.getBuffer(), output.getBuffer().rewind());
        Log.i("FaceNetModel", "Inference Speed in ms: " + (System.currentTimeMillis() - t1));
        return output;
    }

    private ByteBuffer convertBitmapToBuffer(Bitmap image) {
        TensorImage tensorImage = imageTensorProcessor.process(TensorImage.fromBitmap(image));
        return tensorImage.getBuffer();
    }

    public class StandardizeOp implements TensorOperator {
        @Override
        public TensorBuffer apply(TensorBuffer input) {
            float[] pixels = input.getFloatArray();
            float mean = calculateMean(pixels);
            float std = calculateStd(pixels, mean);
            std = max(std, 1f / (float) sqrt(pixels.length));

            float[] standardized = new float[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                standardized[i] = (pixels[i] - mean) / std;
            }

            TensorBuffer output = TensorBufferFloat.createFixedSize(input.getShape(), DataType.FLOAT32);
            output.loadArray(standardized);

            // Log standardization stats
            logTensorStats("Standardized Tensor", standardized);

            return output;
        }

        private float calculateMean(float[] pixels) {
            float sum = 0;
            for (float pixel : pixels) {
                sum += pixel;
            }
            return sum / pixels.length;
        }

        private float calculateStd(float[] pixels, float mean) {
            float sum = 0;
            for (float pixel : pixels) {
                sum += pow(pixel - mean, 2);
            }
            return (float) sqrt(sum / pixels.length);
        }
    }

    public int getEmbeddingDimension() {
        return interpreter.getOutputTensor(0).shape()[1];
    }

    public ModelInfo getModel() {
        return actModel;
    }

    public Interpreter getInterpreter() {
        return interpreter;
    }

    public void setInterpreter(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private void logTensorStats(String tag, float[] array) {
        float mean = calculateMean(array);
        float std = calculateStd(array, mean);
        Log.d("FaceNetModel", String.format("%s: Mean=%.4f, Std=%.4f, First few values=%s",
                tag, mean, std, Arrays.toString(Arrays.copyOf(array, Math.min(5, array.length)))));
    }

    private float calculateMean(float[] array) {
        float sum = 0;
        for (float value : array) {
            sum += value;
        }
        return sum / array.length;
    }

    private float calculateStd(float[] array, float mean) {
        float sum = 0;
        for (float value : array) {
            sum += pow(value - mean, 2);
        }
        return (float) sqrt(sum / array.length);
    }
}