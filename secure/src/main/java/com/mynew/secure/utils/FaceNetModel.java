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

// Utility class for FaceNet model
public class FaceNetModel {

    // Input image size for FaceNet model.
    private final int imgSize;

    // Output embedding size
    public final int embeddingDim;
    private ModelInfo actModel;
    private Interpreter interpreter;
    private final ImageProcessor imageTensorProcessor;

    public FaceNetModel(Context context, ModelInfo model, boolean useGpu, boolean useXNNPack) throws InterruptedException {
        // Input image size for FaceNet model.
        this.imgSize = model.getInputDims();
        actModel=model;
        // Output embedding size
        this.embeddingDim = model.getOutputDims();

        // Configure ImageProcessor for resizing and standardization
        this.imageTensorProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(imgSize, imgSize, ResizeOp.ResizeMethod.BILINEAR))
                .add(new StandardizeOp())
                .build();

        // Initialize TFLiteInterpreter
        Interpreter.Options interpreterOptions = new Interpreter.Options();

        // Add the GPU Delegate if supported.
        if (useGpu) {
            CompatibilityList compatibilityList = new CompatibilityList();
            if (compatibilityList.isDelegateSupportedOnThisDevice()) {
                    interpreterOptions.addDelegate(new GpuDelegate(compatibilityList.getBestOptionsForThisDevice()));
            }
        } else {
            // Number of threads for computation
            interpreterOptions.setNumThreads(4);
        }

        interpreterOptions.setUseXNNPACK(useXNNPack);
        interpreterOptions.setUseNNAPI(true);
        //  ExecutorService executor = Executors.newSingleThreadExecutor();
        //  executor.submit(() -> {
        try {
            this.interpreter = new Interpreter(FileUtil.loadMappedFile(context, model.getAssetsFilename()), interpreterOptions);
        } catch (Exception e) {
            Log.d("interpreter ", "interpreteraaaa: " + e.getMessage());
            e.printStackTrace();
        }
        //  });
        Log.d("Using model", "modelaa: " + model.getName());
    }

    // Gets a face embedding using FaceNet.
    public float[] getFaceEmbedding(Bitmap image) {
        TensorBuffer tensorBuffer = TensorBuffer.createFixedSize(new int[]{1, imgSize, imgSize, 3}, DataType.FLOAT32);
        ByteBuffer byteBuffer = convertBitmapToBuffer(image);
        byteBuffer.order(ByteOrder.nativeOrder());
        tensorBuffer.loadBuffer(byteBuffer);
        TensorBuffer result = runFaceNet(tensorBuffer);
        return result.getFloatArray();
    }

    // Run the FaceNet model.
    private TensorBuffer runFaceNet(TensorBuffer inputs) {
        long t1 = System.currentTimeMillis();
        TensorBuffer output = TensorBufferFloat.createFixedSize(new int[]{1, embeddingDim}, DataType.FLOAT32);
        interpreter.run(inputs.getBuffer(), output.getBuffer().rewind());
        Log.i("Performance", "Inference Speed in ms: " + (System.currentTimeMillis() - t1));
        return output;
    }

    // Resize the given bitmap and convert it to a ByteBuffer
    private ByteBuffer convertBitmapToBuffer(Bitmap image) {
        return imageTensorProcessor.process(TensorImage.fromBitmap(image)).getBuffer();
    }

    // Op to perform standardization
    // x' = ( x - mean ) / std_dev
    public class StandardizeOp implements TensorOperator {

        @Override
        public TensorBuffer apply(TensorBuffer input) {
            float[] pixels = input.getFloatArray();

            // Calculate mean
            float mean = calculateMean(pixels);

            // Calculate standard deviation
            float std = calculateStd(pixels, mean);
            std = max(std, 1f / (float) sqrt(pixels.length));

            // Standardize the values
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = (pixels[i] - mean) / std;
            }

            TensorBuffer output = TensorBufferFloat.createFixedSize(input.getShape(), DataType.FLOAT32);
            output.loadArray(pixels);
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

    // Method to get the dimension of the embeddings
    public int getEmbeddingDimension() {
        // Assuming the output tensor contains embeddings of a fixed size
        return interpreter.getOutputTensor(0).shape()[1];  // Assuming the embedding dimension is at index 1
    }

    // Method to get the loaded TFLite model interpreter
    public ModelInfo getModel() {

        return actModel;
    }

    public Interpreter getInterpreter() {
        return interpreter;
    }

    public void setInterpreter(Interpreter interpreter) {
        this.interpreter = interpreter;
    }
}
