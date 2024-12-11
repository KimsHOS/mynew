package com.example.sample_camera_sdk;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.mynew.secure.CameraActivity;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button openCameraButton = findViewById(R.id.openCameraButton);
        openCameraButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CameraActivity.class);
            startActivity(intent);
        });
    }
}