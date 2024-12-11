package com.mynew.secure.utils;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

public class AchalaSdkConfigurations {

    List<String> actions = new ArrayList<>();

    String verifyImageURL;
    Bitmap verifyImageBitmap;

    public List<String> getActions() {
        return actions;
    }

    public void setActions(List<String> actions) {
        this.actions = actions;
    }

    public String getVerifyImageURL() {
        return this.verifyImageURL;
    }

    public void setVerifyImageURL(String verifyImageURL) {
        this.verifyImageURL = verifyImageURL;
    }

    public Bitmap getVerifyImageBitmap() {
        return this.verifyImageBitmap;
    }

    public void setVerifyImageBitmap(Bitmap verifyImageBitmap) {
        this.verifyImageBitmap = verifyImageBitmap;
    }
}
