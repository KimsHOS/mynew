package com.example.achalasecure.utils;

public class ModelInfo {

    private final String name;
    private final String assetsFilename;
    private final float cosineThreshold;
    private final float l2Threshold;
    private final int outputDims;
    private final int inputDims;

    public ModelInfo(String name, String assetsFilename, float cosineThreshold, float l2Threshold, int outputDims, int inputDims) {
        this.name = name;
        this.assetsFilename = assetsFilename;
        this.cosineThreshold = cosineThreshold;
        this.l2Threshold = l2Threshold;
        this.outputDims = outputDims;
        this.inputDims = inputDims;
    }

    // Getter methods for each field
    public String getName() {
        return name;
    }

    public String getAssetsFilename() {
        return assetsFilename;
    }

    public float getCosineThreshold() {
        return cosineThreshold;
    }

    public float getL2Threshold() {
        return l2Threshold;
    }

    public int getOutputDims() {
        return outputDims;
    }

    public int getInputDims() {
        return inputDims;
    }
}

