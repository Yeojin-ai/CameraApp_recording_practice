package com.example.example_recording_v4;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;


import com.example.example_recording_v4.databinding.ActivityMainBinding;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "IrisVideoRecord";

    ActivityMainBinding binding;

    //Fragment
    FragmentManager fragmentManager;
    FragmentTransaction fragmentTransaction;

    private static final int CALL_VIDEO_CAM = 0;
    private static final int PERMISSIONS_NUM = 100;

    private static  final String[] PERMISSIONS =
            {Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this,R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            checkPermission();
        }

        //fragment init
        fragmentManager = getSupportFragmentManager();
        fragmentTransaction = fragmentManager.beginTransaction();
        if (null == savedInstanceState){
            fragmentTransaction.add(R.id.container, Camera2BasicFragment.newInstance()).commit();
        }

        //mbinding.callCameraBtn.setOnClickListener(view -> callVideoCam());
    }

    public void replaceFragment(Fragment fragment){
        fragmentManager = getSupportFragmentManager();
        fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.container,fragment).commit();
    }

    private void callVideoCam(){
        Intent callVideoCamIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        try {
            PackageManager packageManager = getPackageManager();

            final ResolveInfo resolveInfo = packageManager.resolveActivity(callVideoCamIntent,0);
            //CALL_VIDEO_CAM -> 0 (replace)

            Intent coreIntent = new Intent();
            String packageName = resolveInfo.activityInfo.packageName;
            String name = resolveInfo.activityInfo.name;
            coreIntent.setComponent(new ComponentName(packageName, name));
            coreIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            startActivity(coreIntent);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_NUM:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.e("Permission", "granted");
                } else {
                    finish();
                }
        }
    }

    private void checkPermission(){
        int result;
        ArrayList<String> permissionNeeded = new ArrayList<>();
        for (String p : PERMISSIONS){
            result = ContextCompat.checkSelfPermission(this,p);
            if (result!=PackageManager.PERMISSION_GRANTED){
                permissionNeeded.add(p);
            }
        }
        if (!permissionNeeded.isEmpty()){
            String[] ps = new  String[permissionNeeded.size()];
            for (int i=0; i< ps.length; i++){
                ps[i] = permissionNeeded.get(i);
            }
            ActivityCompat.requestPermissions(this,ps,PERMISSIONS_NUM);
        }
    }

    @Override
    public void onBackPressed(){
        finish();
    }

}