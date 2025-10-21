package com.example.sample_camera_sdk;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.mynew.secure.AchalaSecure;
import com.mynew.secure.AchalaSecureImpl;
import com.mynew.secure.utils.AchalaActions;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button openCameraButton = findViewById(R.id.openCameraButton);
        openCameraButton.setOnClickListener(v -> {
            // Create an instance and call the method
            AchalaSecure achalaSecure = new AchalaSecureImpl(MainActivity.this);

            // Add default liveness check
            List<String> achalaActions = new ArrayList<>();
//            achalaActions.add(AchalaActions.Blink);
//            achalaActions.add(AchalaActions.Smile);
            achalaActions.add(AchalaActions.Open_Eyes);
            achalaSecure.setActions(achalaActions);

            achalaSecure.liveNessDetection(100, "123");
//            Intent intent = new Intent(this, CameraActivity.class);
//            startActivity(intent);
        });
    }
}