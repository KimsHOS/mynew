package com.mynew.secure;

import android.graphics.Bitmap;

import java.util.List;

public interface AchalaSecure {
    void closeAchalaSdk();
    void enrollFace(int ACHALA_SECURE_REQUST_CODE,String id);
    void liveNessDetection(int ACHALA_SECURE_REQUST_CODE,String id);
    void verifyFace(int ACHALA_SECURE_REQUEST_CODE,String userGid);
    void setActions(List<String> actions);
    void setAuthenticateFaceByUrl(String Url);
    void setAuthenticateFaceByBitmap(Bitmap bitmap);
}