package com.example.achalasecure;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;

import com.example.achalasecure.activity.CameraActivity;
import com.example.achalasecure.utils.AchalaSdkConfigurations;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class AchalaSecureImpl implements AchalaSecure {
    private final Activity activity;
    private final Context context;
    private final List<String> actions = new ArrayList<>();
    private String authFaceByUrl = "";
    private Bitmap authFaceByBitmap = null;


    private ActivityResultLauncher<Intent> cameraLauncher;

    public AchalaSecureImpl(Context context) {
        this.activity = context instanceof Activity ? (Activity) context : null;
        this.context = context;
    }

    @Override
    public void closeAchalaSdk() {

    }

    @Override
    public void enrollFace(int ACHALA_SECURE_REQUEST_CODE,String id) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // This method runs on the main thread, so no need for a new thread
                if (activity != null) {
                    Intent intent = new Intent(activity, CameraActivity.class);
                    intent.putExtra("userGid", "newUser");
                    intent.putExtra("isRegistration", true);
                    intent.putExtra("configurations",new Gson().toJson(getConfigurations()));
                    activity.startActivityForResult(intent, ACHALA_SECURE_REQUEST_CODE);
                } else {
                    Log.e("EnrollFace", "Activity is null, cannot start CameraActivity");
                }
            }
        });
        // This method runs on the main thread, so no need for a new thread
       /* if (activity != null) {
            Intent intent = new Intent(activity, CameraActivity.class);
            intent.putExtra("userGid", userGid);
            intent.putExtra("isRegistration", true);
            activity.startActivityForResult(intent, ACHALA_SECURE_REQUEST_CODE);
        } else {
            Log.e("EnrollFace", "Activity is null, cannot start CameraActivity");
        }*/
    }


    @Override
    public void verifyFace(int ACHALA_SECURE_REQUEST_CODE,String id) {
        if (activity != null) {
            Intent intent = new Intent(activity, CameraActivity.class);
            intent.putExtra("isRegistration", false);
            intent.putExtra("configurations",new Gson().toJson(getConfigurations()));
            intent.putExtra("userGid",id);
            activity.startActivityForResult(intent, ACHALA_SECURE_REQUEST_CODE);
        } else {
            Log.e("EnrollFace", "Activity is null, cannot start CameraActivity");
        }
    }

    private AchalaSdkConfigurations getConfigurations() {
        AchalaSdkConfigurations configurations = new AchalaSdkConfigurations();
        configurations.setActions(actions);
        configurations.setVerifyImageURL(this.authFaceByUrl);
        configurations.setVerifyImageBitmap(this.authFaceByBitmap);
        return configurations;
    }

    @Override
    public void setActions(List<String> actions) {
        this.actions.addAll(actions);
    }

    @Override
    public void setAuthenticateFaceByUrl(String Url) {
        this.authFaceByUrl = Url;
    }

    @Override
    public void setAuthenticateFaceByBitmap(Bitmap bitmap) {
        this.authFaceByBitmap = bitmap;
    }
}
