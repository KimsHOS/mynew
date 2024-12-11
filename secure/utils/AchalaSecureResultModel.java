package com.example.achalasecure.utils;

import android.graphics.Bitmap;

import java.io.Serializable;

public class AchalaSecureResultModel implements Serializable {
    String score;
    Bitmap bitmapResult;
    String status;
    String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Bitmap getBitmapResult() {
        return bitmapResult;
    }

    public void setBitmapResult(Bitmap bitmapResult) {
        this.bitmapResult = bitmapResult;
    }

    public String getScore() {
        return score;
    }

    public void setScore(String score) {
        this.score = score;
    }
}
