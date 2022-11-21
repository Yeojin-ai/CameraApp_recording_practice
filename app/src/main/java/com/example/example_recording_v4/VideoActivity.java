package com.example.example_recording_v4;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.example.example_recording_v4.databinding.ActivityVideoBinding;

public class VideoActivity extends AppCompatActivity {
    ActivityVideoBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_video);
        if (null == savedInstanceState){
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container,VideoFragment.newInstance()).commit();
        }
    }
}
