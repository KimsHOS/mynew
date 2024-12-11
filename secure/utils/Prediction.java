package com.example.achalasecure.utils;

import android.graphics.Rect;

public class Prediction {

    private Rect bbox;
    private String label;
    private String maskLabel;

    public Prediction(Rect bbox, String label) {
        this.bbox = bbox;
        this.label = label;
        this.maskLabel = "";
    }

    public Prediction(Rect bbox, String label, String maskLabel) {
        this.bbox = bbox;
        this.label = label;
        this.maskLabel = maskLabel;
    }

    public Rect getBbox() {
        return bbox;
    }

    public void setBbox(Rect bbox) {
        this.bbox = bbox;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getMaskLabel() {
        return maskLabel;
    }

    public void setMaskLabel(String maskLabel) {
        this.maskLabel = maskLabel;
    }

    @Override
    public String toString() {
        return "Prediction{" +
                "bbox=" + bbox +
                ", label='" + label + '\'' +
                ", maskLabel='" + maskLabel + '\'' +
                '}';
    }
}
